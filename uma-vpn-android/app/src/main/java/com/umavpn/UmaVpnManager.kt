package com.umavpn

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.os.IBinder
import android.util.Log
import com.umavpn.api.UmapyoiApiClient
import com.umavpn.model.ConnectionState
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
 * Singleton that owns the AIDL binding to OpenVPN for Android and drives
 * the full connect/retry/disconnect lifecycle.
 *
 * Connection algorithm:
 *   1. Fetch the full verified server list from umapyoi.net (sorted by ping).
 *   2. For each server in order:
 *      a. Call startVPN(profile).
 *      b. Wait up to [connectTimeoutMs] for a CONNECTED signal.
 *      c. If an error signal arrives before the timeout, skip to the next server immediately.
 *      d. If the timeout elapses, call disconnect() and skip to the next server.
 *   3. If all servers are exhausted, emit an Error state.
 */
class UmaVpnManager private constructor(private val appContext: Context) {

    companion object {
        private const val TAG = "UmaVpnManager"
        private const val OPENVPN_PACKAGE = "de.blinkt.openvpn"
        private const val OPENVPN_SERVICE = "de.blinkt.openvpn.api.ExternalOpenVPNService"

        private const val PREFS_NAME = "umavpn_prefs"
        private const val PREF_TIMEOUT_SECONDS = "connect_timeout_seconds"
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

    /** Timeout per connection attempt in seconds. Persisted across app restarts. */
    var connectTimeoutSeconds: Int
        get() = prefs.getInt(PREF_TIMEOUT_SECONDS, DEFAULT_TIMEOUT_SECONDS)
            .coerceIn(MIN_TIMEOUT_SECONDS, MAX_TIMEOUT_SECONDS)
        set(value) {
            prefs.edit().putInt(PREF_TIMEOUT_SECONDS, value.coerceIn(MIN_TIMEOUT_SECONDS, MAX_TIMEOUT_SECONDS)).apply()
        }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val apiClient = UmapyoiApiClient()

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    /**
     * Hot signal flow used to bridge the AIDL status callback into the
     * coroutine-based retry loop. Emits true on CONNECTED, false on any failure.
     * replay=0 so a new collector never sees stale events from a previous attempt.
     */
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
            Log.d(TAG, "Connected to OpenVPN for Android service")
            runCatching { vpnService?.registerStatusCallback(statusCallback) }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.w(TAG, "Disconnected from OpenVPN for Android service")
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

    fun isOpenVpnInstalled(): Boolean {
        val visibleToPackageManager = runCatching {
            appContext.packageManager.getPackageInfo(OPENVPN_PACKAGE, 0)
        }.isSuccess
        return visibleToPackageManager || vpnService != null
    }

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

        connectJob?.cancel()
        connectJob = scope.launch {
            _state.value = ConnectionState.FetchingServers

            val serversResult = withContext(Dispatchers.IO) {
                runCatching { apiClient.fetchCygamesServers() }
            }

            if (serversResult.isFailure) {
                _state.value = ConnectionState.Error(
                    serversResult.exceptionOrNull()?.message ?: "Failed to fetch VPN servers"
                )
                return@launch
            }

            val servers = serversResult.getOrThrow()
            if (servers.isEmpty()) {
                _state.value = ConnectionState.Error("No verified servers available right now.")
                return@launch
            }

            val timeoutMs = connectTimeoutSeconds * 1_000L

            for ((index, server) in servers.withIndex()) {
                _state.value = ConnectionState.Connecting(
                    serverIp = server.remoteHost,
                    attempt = index + 1,
                    total = servers.size
                )

                // Start the connection attempt on a background thread
                val startOk = withContext(Dispatchers.IO) {
                    runCatching { vpnService?.startVPN(server.profile) }.isSuccess
                }
                if (!startOk) {
                    Log.w(TAG, "startVPN failed for ${server.remoteHost}, skipping")
                    continue
                }

                // Wait for a terminal signal (connected=true or failed=false) with a timeout.
                // _connectionSignal has replay=0 so we only receive signals emitted *after*
                // this collect starts — stale events from a previous attempt are never seen.
                val connected = withTimeoutOrNull(timeoutMs) {
                    _connectionSignal.first()
                }

                when (connected) {
                    true -> {
                        _state.value = ConnectionState.Connected(
                            serverIp = server.remoteHost,
                            ping = server.cygames.ping
                        )
                        Log.i(TAG, "Connected via ${server.remoteHost} (attempt ${index + 1}/${servers.size})")
                        return@launch
                    }
                    false -> {
                        Log.w(TAG, "Server ${server.remoteHost} failed, trying next")
                        forceDisconnectAndWait()
                    }
                    null -> {
                        Log.w(TAG, "Server ${server.remoteHost} timed out after ${connectTimeoutSeconds}s, trying next")
                        forceDisconnectAndWait()
                    }
                }
            }

            // All servers tried
            _state.value = ConnectionState.Error(
                "All ${servers.size} servers failed. Try again in a few minutes."
            )
        }
    }

    /**
     * Issues a disconnect and gives OpenVPN ~600 ms to clean up before we try the next server.
     * We intentionally do NOT await the DISCONNECTED callback here — the brief fixed delay is
     * enough in practice and avoids re-entrant callback complexity.
     */
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
            is ConnectionState.Connecting -> disconnect()
            else -> connect()
        }
    }

    private fun handleVpnStatus(state: String?) {
        when (state) {
            "CONNECTED" -> {
                // Signal the connect() retry loop — it will set the Connected state
                // since it has the full VpnServer object (including the ping value).
                scope.launch { _connectionSignal.emit(true) }
            }

            "CONNECTRETRY", "AUTH_FAILED" -> {
                // Faulty profile or unreachable host — signal immediate skip
                scope.launch { _connectionSignal.emit(false) }
            }

            "DISCONNECTED", "EXITING" -> {
                // Only update the public state if we're NOT inside a retry loop.
                // During active connection attempts the connect() coroutine owns all
                // state transitions; this branch only fires for user-initiated disconnects.
                val current = _state.value
                if (current !is ConnectionState.Connecting &&
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
