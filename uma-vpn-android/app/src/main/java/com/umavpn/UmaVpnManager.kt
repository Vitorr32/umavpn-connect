package com.umavpn

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.os.IBinder
import android.util.Log
import com.umavpn.api.GameConnectivityChecker
import com.umavpn.api.UmaVpnApiClient
import com.umavpn.model.ConnectionState
import com.umavpn.model.GameVersion
import com.umavpn.model.VpnServer
import de.blinkt.openvpn.api.IOpenVPNAPIService
import de.blinkt.openvpn.api.IOpenVPNStatusCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Singleton that owns the AIDL binding to OpenVPN for Android and drives the full
 * connect / retry / game-verify / disconnect lifecycle.
 *
 * Connection algorithm per attempt:
 *   1. Fetch the server list for the selected [GameVersion] from api.umavpn.top.
 *   2. For each server, in ascending ping order:
 *      a. startVPN — wait up to [connectTimeoutMs] for CONNECTED.
 *         CONNECTRETRY / AUTH_FAILED → immediate skip (faulty profile).
 *         Timeout → force disconnect, skip.
 *      b. If VPN connected: verify Cygames server reachability (HTTP test).
 *         404 → "Accessible" — set Connected state and stop.
 *         403 → IP geo-blocked for this version → disconnect, try next server.
 *         Inconclusive → stay connected but flag as unverified.
 *   3. If all servers fail: emit Error state.
 */
class UmaVpnManager private constructor(private val appContext: Context) {

    companion object {
        private const val TAG = "UmaVpnManager"
        private const val OPENVPN_PACKAGE = "de.blinkt.openvpn"
        private const val OPENVPN_SERVICE = "de.blinkt.openvpn.api.ExternalOpenVPNService"

        private const val PREFS_NAME = "umavpn_prefs"
        private const val PREF_TIMEOUT_SECONDS = "connect_timeout_seconds"
        private const val PREF_GAME_VERSION = "game_version_ordinal"

        const val DEFAULT_TIMEOUT_SECONDS = 8
        const val MIN_TIMEOUT_SECONDS = 3
        const val MAX_TIMEOUT_SECONDS = 30

        @Volatile
        private var instance: UmaVpnManager? = null

        fun getInstance(context: Context): UmaVpnManager =
            instance ?: synchronized(this) {
                instance ?: UmaVpnManager(context.applicationContext).also { instance = it }
            }
    }

    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var connectTimeoutSeconds: Int
        get() = prefs.getInt(PREF_TIMEOUT_SECONDS, DEFAULT_TIMEOUT_SECONDS)
            .coerceIn(MIN_TIMEOUT_SECONDS, MAX_TIMEOUT_SECONDS)
        set(value) = prefs.edit()
            .putInt(PREF_TIMEOUT_SECONDS, value.coerceIn(MIN_TIMEOUT_SECONDS, MAX_TIMEOUT_SECONDS))
            .apply()

    var gameVersion: GameVersion
        get() = GameVersion.fromOrdinal(prefs.getInt(PREF_GAME_VERSION, GameVersion.GLOBAL.ordinal))
        set(value) = prefs.edit().putInt(PREF_GAME_VERSION, value.ordinal).apply()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val apiClient = UmaVpnApiClient()
    private val connectivityChecker = GameConnectivityChecker()

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _connectionSignal = MutableSharedFlow<Boolean>(replay = 0, extraBufferCapacity = 16)

    private var vpnService: IOpenVPNAPIService? = null
    private var connectJob: Job? = null

    private val statusCallback = object : IOpenVPNStatusCallback.Stub() {
        override fun newStatus(uuid: String?, state: String?, message: String?, level: String?) {
            Log.d(TAG, "VPN status: state=$state message=$message level=$level")
            handleVpnStatus(state)
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            vpnService = IOpenVPNAPIService.Stub.asInterface(binder)
            Log.d(TAG, "Bound to OpenVPN for Android")
            runCatching { vpnService?.registerStatusCallback(statusCallback) }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.w(TAG, "OpenVPN service disconnected")
            vpnService = null
        }
    }

    init {
        bindToOpenVpnService()
    }

    private fun bindToOpenVpnService() {
        try {
            val intent = Intent().apply {
                component = ComponentName(OPENVPN_PACKAGE, OPENVPN_SERVICE)
            }
            if (!appContext.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)) {
                Log.w(TAG, "bindService returned false — OpenVPN for Android may not be installed")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception binding to OpenVPN service", e)
        }
    }

    fun isOpenVpnInstalled(): Boolean =
        runCatching { appContext.packageManager.getPackageInfo(OPENVPN_PACKAGE, 0) }.isSuccess
            || vpnService != null

    fun isConnected(): Boolean = _state.value is ConnectionState.Connected

    fun getApiPermissionIntent(): Intent? = runCatching<Intent?> {
        vpnService?.prepare(appContext.packageName)
    }.getOrNull()

    fun getVpnServicePermissionIntent(): Intent? = runCatching<Intent?> {
        vpnService?.prepareVPNService()
    }.getOrNull()

    fun connect() {
        val current = _state.value
        if (current is ConnectionState.Connecting || current is ConnectionState.Connected) return

        val version = gameVersion
        connectJob?.cancel()
        connectJob = scope.launch {
            _state.value = ConnectionState.FetchingServers

            val serversResult = withContext(Dispatchers.IO) {
                runCatching { apiClient.fetchServers(version) }
            }

            if (serversResult.isFailure) {
                _state.value = ConnectionState.Error(
                    serversResult.exceptionOrNull()?.message ?: "Failed to fetch VPN servers"
                )
                return@launch
            }

            val servers = serversResult.getOrThrow()
            val timeoutMs = connectTimeoutSeconds * 1_000L

            for ((index, server) in servers.withIndex()) {
                _state.value = ConnectionState.Connecting(
                    serverIp = server.remoteHost,
                    attempt = index + 1,
                    total = servers.size
                )

                val profileResult = withContext(Dispatchers.IO) {
                    runCatching { apiClient.fetchServerConfig(server.ip) }
                }
                if (profileResult.isFailure) {
                    Log.w(TAG, "Could not fetch profile for ${server.ip}, skipping")
                    continue
                }
                val serverWithProfile = server.copy(profile = profileResult.getOrThrow())

                // Step 1: establish VPN tunnel
                val vpnConnected = tryConnectVpn(serverWithProfile, timeoutMs)
                if (!vpnConnected) continue

                // Step 2: verify the game server is reachable through this VPN
                _state.value = ConnectionState.VerifyingGame(serverWithProfile.remoteHost)
                val gameResult = withContext(Dispatchers.IO) {
                    connectivityChecker.check(version.connectivityTestUrl)
                }

                when (gameResult) {
                    is GameConnectivityChecker.Result.Accessible -> {
                        _state.value = ConnectionState.Connected(
                            serverIp = serverWithProfile.remoteHost,
                            ping = serverWithProfile.ping,
                            gameAccessible = true
                        )
                        Log.i(TAG, "✓ Connected + game verified via ${serverWithProfile.remoteHost}")
                        return@launch
                    }
                    is GameConnectivityChecker.Result.Blocked -> {
                        Log.w(TAG, "✗ ${serverWithProfile.remoteHost} — game blocked (HTTP 403), trying next")
                        forceDisconnectAndWait()
                        continue
                    }
                    is GameConnectivityChecker.Result.Inconclusive -> {
                        // VPN is up but we couldn't confirm game access — stay connected
                        // but let the user know the check was inconclusive
                        _state.value = ConnectionState.Connected(
                            serverIp = serverWithProfile.remoteHost,
                            ping = serverWithProfile.ping,
                            gameAccessible = null
                        )
                        Log.w(TAG, "? ${serverWithProfile.remoteHost} — game check inconclusive: ${gameResult.reason}")
                        return@launch
                    }
                }
            }

            _state.value = ConnectionState.Error(
                "All ${servers.size} servers tried — none could reach the Umamusume server " +
                    "for the ${version.label} version. Try again in a few minutes."
            )
        }
    }

    /**
     * Issues startVPN for [server] and waits up to [timeoutMs] for the CONNECTED signal.
     * Returns true if the VPN tunnel came up; false if it failed or timed out.
     */
    private suspend fun tryConnectVpn(server: VpnServer, timeoutMs: Long): Boolean {
        val startOk = withContext(Dispatchers.IO) {
            runCatching { vpnService?.startVPN(server.profile) }.isSuccess
        }
        if (!startOk) return false

        val connected = withTimeoutOrNull(timeoutMs) {
            _connectionSignal.first()
        }

        return when (connected) {
            true -> true
            false -> {
                Log.w(TAG, "VPN error on ${server.remoteHost}, skipping")
                forceDisconnectAndWait()
                false
            }
            null -> {
                Log.w(TAG, "VPN timeout on ${server.remoteHost} after ${connectTimeoutSeconds}s")
                forceDisconnectAndWait()
                false
            }
        }
    }

    private suspend fun forceDisconnectAndWait() {
        withContext(Dispatchers.IO) { runCatching { vpnService?.disconnect() } }
        delay(600)
    }

    fun disconnect() {
        connectJob?.cancel()
        _state.value = ConnectionState.Disconnecting
        scope.launch {
            withContext(Dispatchers.IO) { runCatching { vpnService?.disconnect() } }
            _state.value = ConnectionState.Idle
        }
    }

    fun toggle() {
        when (_state.value) {
            is ConnectionState.Connected,
            is ConnectionState.Connecting,
            is ConnectionState.VerifyingGame -> disconnect()
            else -> connect()
        }
    }

    private fun handleVpnStatus(state: String?) {
        when (state) {
            "CONNECTED" -> scope.launch { _connectionSignal.emit(true) }
            "CONNECTRETRY", "AUTH_FAILED" -> scope.launch { _connectionSignal.emit(false) }
            "DISCONNECTED", "EXITING" -> {
                val current = _state.value
                if (current !is ConnectionState.Connecting &&
                    current !is ConnectionState.VerifyingGame &&
                    current !is ConnectionState.FetchingServers &&
                    current !is ConnectionState.Disconnecting
                ) {
                    _state.value = ConnectionState.Idle
                }
            }
        }
    }

    fun cleanup() {
        runCatching { vpnService?.unregisterStatusCallback(statusCallback) }
        runCatching { appContext.unbindService(serviceConnection) }
    }
}
