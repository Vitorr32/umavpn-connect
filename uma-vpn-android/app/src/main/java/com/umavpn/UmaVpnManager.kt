package com.umavpn

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.os.IBinder
import android.util.Log
import com.umavpn.api.GameConnectivityChecker
import com.umavpn.api.OpenVpnProfileAdapter
import com.umavpn.api.OpenVpnProfileVariant
import com.umavpn.api.VpnApiClient
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
 *   1. Fetch the server list for the selected [GameVersion].
 *   2. For each server, in ascending ping order:
 *      a. startVPN — wait up to [connectTimeoutMs] for CONNECTED.
 *         CONNECTRETRY / AUTH_FAILED → immediate skip (faulty profile).
 *         Timeout → force disconnect, skip.
 *      b. If VPN connected: verify Cygames server reachability (HTTP test).
 *         404 → "Accessible" — set Connected state, optionally launch game, stop.
 *         403 / Inconclusive → disconnect, try next server.
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
        private const val PREF_AUTO_LAUNCH_GAME = "auto_launch_game"

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

    var autoLaunchGame: Boolean
        get() = prefs.getBoolean(PREF_AUTO_LAUNCH_GAME, false)
        set(value) = prefs.edit().putBoolean(PREF_AUTO_LAUNCH_GAME, value).apply()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val apiClient = VpnApiClient()
    private val connectivityChecker = GameConnectivityChecker()

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private data class VpnAttemptResult(
        val connected: Boolean,
        val statusMessage: String? = null,
    )

    private val _connectionSignal = MutableSharedFlow<VpnAttemptResult>(
        replay = 0,
        extraBufferCapacity = 16,
    )

    private var vpnService: IOpenVPNAPIService? = null
    private var connectJob: Job? = null
    @Volatile
    private var lastVpnStatusMessage: String? = null

    private val statusCallback = object : IOpenVPNStatusCallback.Stub() {
        override fun newStatus(uuid: String?, state: String?, message: String?, level: String?) {
            Log.d(TAG, "VPN status: state=$state message=$message level=$level")
            if (!message.isNullOrBlank()) {
                lastVpnStatusMessage = message
            }
            handleVpnStatus(state, message)
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
            var vpnFailures = 0
            var gameCheckFailures = 0
            var lastFailureDetail: String? = null

            for ((index, server) in servers.withIndex()) {
                _state.value = ConnectionState.Connecting(
                    serverIp = server.remoteHost,
                    attempt = index + 1,
                    total = servers.size
                )

                val tunnel = connectVpnWithVariantFallback(server, version, timeoutMs)
                if (!tunnel.connected) {
                    vpnFailures++
                    lastFailureDetail = tunnel.statusMessage ?: lastVpnStatusMessage
                    Log.w(
                        TAG,
                        "✗ ${server.remoteHost} — VPN tunnel failed " +
                            "(variant=${tunnel.variant}, detail=${tunnel.statusMessage})"
                    )
                    continue
                }

                _state.value = ConnectionState.VerifyingGame(server.remoteHost)
                val gameResult = withContext(Dispatchers.IO) {
                    connectivityChecker.check(version)
                }

                when (gameResult) {
                    is GameConnectivityChecker.Result.Accessible -> {
                        _state.value = ConnectionState.Connected(
                            serverIp = server.remoteHost,
                            ping = server.pingMs,
                            gameAccessible = true
                        )
                        Log.i(
                            TAG,
                            "✓ Connected + game verified via ${server.remoteHost} " +
                                "(profile variant=${tunnel.variant})"
                        )
                        if (autoLaunchGame) {
                            GameLauncher.launch(appContext, version)
                        }
                        return@launch
                    }
                    is GameConnectivityChecker.Result.Blocked -> {
                        gameCheckFailures++
                        lastFailureDetail = "Cygames geo-blocked (HTTP 403)"
                        Log.w(TAG, "✗ ${server.remoteHost} — game blocked (HTTP 403), trying next")
                        forceDisconnectAndWait()
                        continue
                    }
                    is GameConnectivityChecker.Result.Inconclusive -> {
                        gameCheckFailures++
                        lastFailureDetail = gameResult.reason
                        Log.w(TAG, "✗ ${server.remoteHost} — game check failed: ${gameResult.reason}")
                        forceDisconnectAndWait()
                        continue
                    }
                }
            }

            _state.value = ConnectionState.Error(
                buildConnectFailureMessage(
                    versionLabel = version.label,
                    serverCount = servers.size,
                    vpnFailures = vpnFailures,
                    gameCheckFailures = gameCheckFailures,
                    lastFailureDetail = lastFailureDetail,
                )
            )
        }
    }

    private data class TunnelAttempt(
        val connected: Boolean,
        val variant: OpenVpnProfileVariant,
        val statusMessage: String? = null,
    )

    /**
     * Tries [OpenVpnProfileVariant.PRIMARY] (beta), then [OpenVpnProfileVariant.LEGACY]
     * if the tunnel fails — matching the umavpn.top player guide and legacy encryption fallback.
     */
    private suspend fun connectVpnWithVariantFallback(
        server: VpnServer,
        gameVersion: GameVersion,
        timeoutMs: Long,
    ): TunnelAttempt {
        val variants = OpenVpnProfileVariant.CONNECT_FALLBACK_ORDER
        var lastDetail: String? = null

        for ((index, variant) in variants.withIndex()) {
            if (index > 0) {
                Log.i(TAG, "Retrying ${server.remoteHost} with ${variant.name} profile variant")
            }

            val profile = loadProfile(server, variant, gameVersion)
            val attempt = tryConnectVpn(server.remoteHost, profile, variant, timeoutMs)
            if (attempt.connected) {
                return TunnelAttempt(true, variant)
            }

            lastDetail = attempt.statusMessage
            if (index < variants.lastIndex) {
                forceDisconnectAndWait()
            }
        }

        return TunnelAttempt(false, OpenVpnProfileVariant.LEGACY, lastDetail)
    }

    private suspend fun loadProfile(
        server: VpnServer,
        variant: OpenVpnProfileVariant,
        gameVersion: GameVersion,
    ): String = withContext(Dispatchers.IO) {
        val raw = if (variant == OpenVpnProfileVariant.PRIMARY) {
            server.profile
        } else {
            apiClient.fetchConfig(
                ip = server.remoteHost,
                variant = variant,
                splitTunnel = gameVersion.useSplitTunnel,
            )
        }
        OpenVpnProfileAdapter.forOpenVpnForAndroid(raw, variant)
    }

    /**
     * Issues startVPN for [profile] and waits up to [timeoutMs] for the CONNECTED signal.
     */
    private suspend fun tryConnectVpn(
        serverIp: String,
        profile: String,
        variant: OpenVpnProfileVariant,
        timeoutMs: Long,
    ): VpnAttemptResult {
        lastVpnStatusMessage = null

        val startOk = withContext(Dispatchers.IO) {
            runCatching { vpnService?.startVPN(profile) }.isSuccess
        }
        if (!startOk) {
            return VpnAttemptResult(false, "startVPN() rejected the inline profile")
        }

        val result = withTimeoutOrNull(timeoutMs) {
            _connectionSignal.first()
        }

        return when {
            result?.connected == true -> VpnAttemptResult(true)
            result?.connected == false -> {
                val detail = result.statusMessage ?: lastVpnStatusMessage ?: "OpenVPN reported an error"
                Log.w(TAG, "VPN error on $serverIp (${variant.name}): $detail")
                forceDisconnectAndWait()
                VpnAttemptResult(false, detail)
            }
            else -> {
                val detail = "Timed out after ${connectTimeoutSeconds}s"
                Log.w(TAG, "VPN timeout on $serverIp (${variant.name}) after ${connectTimeoutSeconds}s")
                forceDisconnectAndWait()
                VpnAttemptResult(false, detail)
            }
        }
    }

    private fun buildConnectFailureMessage(
        versionLabel: String,
        serverCount: Int,
        vpnFailures: Int,
        gameCheckFailures: Int,
        lastFailureDetail: String?,
    ): String {
        val summary = when {
            vpnFailures == serverCount ->
                "Could not establish a VPN tunnel on any of the $serverCount servers " +
                    "for the $versionLabel version."
            gameCheckFailures > 0 && vpnFailures == 0 ->
                "VPN connected but Cygames was not reachable on any of the $serverCount servers " +
                    "for the $versionLabel version."
            else ->
                "None of the $serverCount servers worked for the $versionLabel version " +
                    "($vpnFailures tunnel failures, $gameCheckFailures game-check failures)."
        }

        val hint = when {
            vpnFailures > 0 ->
                " If manual import works in OpenVPN Connect, note that this app uses " +
                    "OpenVPN for Android with a different engine — try updating that app " +
                    "or increasing the connect timeout."
            else ->
                " The VPN tunnel came up but the Cygames geo-check failed — try again later."
        }

        val detail = lastFailureDetail?.let { " Last error: $it" }.orEmpty()
        return summary + hint + detail
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

    private fun handleVpnStatus(state: String?, message: String?) {
        when (state) {
            "CONNECTED" -> scope.launch { _connectionSignal.emit(VpnAttemptResult(true)) }
            "CONNECTRETRY", "AUTH_FAILED" -> scope.launch {
                _connectionSignal.emit(VpnAttemptResult(false, message ?: state))
            }
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
