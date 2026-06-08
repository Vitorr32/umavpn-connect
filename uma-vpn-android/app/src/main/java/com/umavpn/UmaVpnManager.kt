package com.umavpn

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Singleton manager that owns the connection to OpenVPN for Android's external API
 * and the full connect/disconnect lifecycle.
 */
class UmaVpnManager private constructor(private val appContext: Context) {

    companion object {
        private const val TAG = "UmaVpnManager"
        private const val OPENVPN_PACKAGE = "de.blinkt.openvpn"
        private const val OPENVPN_SERVICE = "de.blinkt.openvpn.api.ExternalOpenVPNService"

        @Volatile
        private var instance: UmaVpnManager? = null

        fun getInstance(context: Context): UmaVpnManager =
            instance ?: synchronized(this) {
                instance ?: UmaVpnManager(context.applicationContext).also { instance = it }
            }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val apiClient = UmapyoiApiClient()

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private var vpnService: IOpenVPNAPIService? = null
    private var connectJob: Job? = null
    private var currentServer: VpnServer? = null

    private val statusCallback = object : IOpenVPNStatusCallback.Stub() {
        override fun newStatus(uuid: String?, state: String?, message: String?, level: String?) {
            Log.d(TAG, "VPN status: uuid=$uuid state=$state message=$message level=$level")
            handleVpnStatus(state)
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            vpnService = IOpenVPNAPIService.Stub.asInterface(binder)
            Log.d(TAG, "Connected to OpenVPN for Android service")
            try {
                vpnService?.registerStatusCallback(statusCallback)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register status callback", e)
            }
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
            val bound = appContext.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            if (!bound) {
                Log.w(TAG, "Could not bind to OpenVPN for Android — app may not be installed")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception binding to OpenVPN service", e)
        }
    }

    fun isOpenVpnInstalled(): Boolean {
        // Primary check: package manager lookup (works once <queries> is declared in the manifest)
        val visibleToPackageManager = try {
            appContext.packageManager.getPackageInfo(OPENVPN_PACKAGE, 0)
            true
        } catch (e: Exception) {
            false
        }
        if (visibleToPackageManager) return true

        // Fallback: if the AIDL service bound successfully, the app is definitely installed
        // (handles edge cases where the package manager check still fails)
        return vpnService != null
    }

    fun isConnected(): Boolean = _state.value is ConnectionState.Connected

    /**
     * Returns an Intent that an Activity must start if the external API permission
     * hasn't been granted yet, or null if already permitted.
     */
    fun getApiPermissionIntent(): Intent? = runCatching<Intent?> {
        vpnService?.prepare(appContext.packageName)
    }.getOrNull()

    /**
     * Returns an Intent to show the Android system VPN dialog if needed, null if already granted.
     */
    fun getVpnServicePermissionIntent(): Intent? = runCatching<Intent?> {
        vpnService?.prepareVPNService()
    }.getOrNull()

    fun connect() {
        val current = _state.value
        if (current is ConnectionState.Connecting || current is ConnectionState.Connected) return

        connectJob?.cancel()
        connectJob = scope.launch {
            _state.value = ConnectionState.FetchingServers

            val serverResult = withContext(Dispatchers.IO) {
                runCatching { apiClient.fetchBestServer() }
            }

            if (serverResult.isFailure) {
                _state.value = ConnectionState.Error(
                    serverResult.exceptionOrNull()?.message ?: "Failed to fetch VPN servers"
                )
                return@launch
            }

            val server = serverResult.getOrThrow()
            currentServer = server
            _state.value = ConnectionState.Connecting(server.remoteHost)

            val startResult = withContext(Dispatchers.IO) {
                runCatching {
                    val service = checkNotNull(vpnService) {
                        "OpenVPN for Android is not running. Please ensure it is installed."
                    }
                    service.startVPN(server.profile)
                }
            }

            if (startResult.isFailure) {
                _state.value = ConnectionState.Error(
                    startResult.exceptionOrNull()?.message ?: "Failed to start VPN"
                )
                currentServer = null
            }
            // On success the status callback drives further state transitions
        }
    }

    fun disconnect() {
        connectJob?.cancel()
        _state.value = ConnectionState.Disconnecting
        scope.launch {
            withContext(Dispatchers.IO) {
                runCatching { vpnService?.disconnect() }
            }
            _state.value = ConnectionState.Idle
            currentServer = null
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
        val server = currentServer ?: return
        when (state) {
            "CONNECTED" -> _state.value = ConnectionState.Connected(
                serverIp = server.remoteHost,
                ping = server.cygames.ping
            )

            "DISCONNECTED", "EXITING" -> {
                val current = _state.value
                if (current !is ConnectionState.Idle && current !is ConnectionState.Disconnecting) {
                    _state.value = ConnectionState.Idle
                }
                currentServer = null
            }

            "AUTH_FAILED" -> {
                _state.value = ConnectionState.Error("Authentication failed")
                currentServer = null
            }

            "RECONNECTING" -> _state.value = ConnectionState.Connecting(server.remoteHost)

            "CONNECTRETRY" -> {
                _state.value = ConnectionState.Error(
                    "Could not connect to ${server.remoteHost}. Server may be offline."
                )
                currentServer = null
            }
        }
    }

    fun cleanup() {
        runCatching { vpnService?.unregisterStatusCallback(statusCallback) }
        runCatching { appContext.unbindService(serviceConnection) }
    }
}
