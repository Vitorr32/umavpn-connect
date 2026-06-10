package com.umavpn

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.umavpn.model.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class VpnTileService : TileService() {

    private var scope: CoroutineScope? = null
    private lateinit var manager: UmaVpnManager

    override fun onCreate() {
        super.onCreate()
        manager = UmaVpnManager.getInstance(applicationContext)
    }

    override fun onStartListening() {
        super.onStartListening()
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope = newScope
        manager.state
            .onEach { state -> updateTile(state) }
            .launchIn(newScope)
    }

    override fun onStopListening() {
        super.onStopListening()
        scope?.cancel()
        scope = null
    }

    override fun onClick() {
        super.onClick()

        if (!manager.isOpenVpnInstalled()) {
            launchActivity(createInstallIntent())
            return
        }

        val permissionIntent = manager.getApiPermissionIntent()
        if (permissionIntent != null) {
            launchActivity(
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra(MainActivity.EXTRA_REQUEST_PERMISSION, true)
                }
            )
            return
        }

        val vpnPermIntent = manager.getVpnServicePermissionIntent()
        if (vpnPermIntent != null) {
            launchActivity(
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra(MainActivity.EXTRA_REQUEST_VPN_PERMISSION, true)
                }
            )
            return
        }

        // All permissions granted — toggle immediately from the tile
        manager.toggle()
    }

    private fun createInstallIntent(): Intent = Intent(Intent.ACTION_VIEW).apply {
        data = android.net.Uri.parse(
            "https://play.google.com/store/apps/details?id=de.blinkt.openvpn"
        )
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }

    private fun launchActivity(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+: startActivityAndCollapse requires a PendingIntent
            val pi = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun updateTile(state: ConnectionState) {
        val tile = qsTile ?: return
        tile.label = getString(R.string.tile_label)
        when (state) {
            is ConnectionState.Idle -> {
                tile.state = Tile.STATE_INACTIVE
                setSubtitle(tile, getString(R.string.tile_subtitle_idle))
            }
            is ConnectionState.FetchingServers -> {
                tile.state = Tile.STATE_ACTIVE
                setSubtitle(tile, getString(R.string.tile_subtitle_fetching))
            }
            is ConnectionState.Connecting -> {
                tile.state = Tile.STATE_ACTIVE
                setSubtitle(tile, if (state.total > 0)
                    getString(R.string.tile_subtitle_connecting, state.attempt, state.total, state.serverIp)
                else state.serverIp)
            }
            is ConnectionState.VerifyingGame -> {
                tile.state = Tile.STATE_ACTIVE
                setSubtitle(tile, getString(R.string.tile_subtitle_verifying))
            }
            is ConnectionState.Connected -> {
                tile.state = Tile.STATE_ACTIVE
                val pingStr = String.format("%.0f", state.ping)
                setSubtitle(tile, getString(R.string.tile_subtitle_connected, state.serverIp, pingStr))
            }
            is ConnectionState.Error -> {
                tile.state = Tile.STATE_INACTIVE
                setSubtitle(tile, getString(R.string.tile_subtitle_error))
            }
            is ConnectionState.Disconnecting -> {
                tile.state = Tile.STATE_INACTIVE
                setSubtitle(tile, getString(R.string.tile_subtitle_disconnecting))
            }
        }
        tile.updateTile()
    }

    private fun setSubtitle(tile: android.service.quicksettings.Tile, text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = text
        }
    }
}
