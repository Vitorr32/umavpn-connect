package com.umavpn

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.snackbar.Snackbar
import com.umavpn.databinding.ActivityMainBinding
import com.umavpn.model.ConnectionState
import com.umavpn.model.GameVersion
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_REQUEST_PERMISSION = "extra_request_permission"
        const val EXTRA_REQUEST_VPN_PERMISSION = "extra_request_vpn_permission"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var manager: UmaVpnManager

    private val apiPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode == RESULT_OK) handleToggle()
        else showSnackbar("Permission denied. Cannot control OpenVPN for Android.")
    }

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode == RESULT_OK) manager.connect()
        else showSnackbar("VPN permission denied.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        manager = UmaVpnManager.getInstance(applicationContext)

        setupVersionToggle()
        setupConnectButton()
        setupInstallBanner()
        setupTimeoutSeekBar()
        observeState()
        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIncomingIntent(it) }
    }

    private fun handleIncomingIntent(intent: Intent) {
        when {
            intent.getBooleanExtra(EXTRA_REQUEST_PERMISSION, false) -> requestApiPermission()
            intent.getBooleanExtra(EXTRA_REQUEST_VPN_PERMISSION, false) -> requestVpnPermission()
        }
    }

    // ── Version toggle ────────────────────────────────────────────────────────

    private fun setupVersionToggle() {
        val savedVersion = manager.gameVersion
        binding.toggleGameVersion.check(
            if (savedVersion == GameVersion.GLOBAL) R.id.btnVersionGlobal
            else R.id.btnVersionJapanese
        )
        updateVersionHint(savedVersion)

        binding.toggleGameVersion.addOnButtonCheckedListener { _: MaterialButtonToggleGroup, checkedId: Int, isChecked: Boolean ->
            if (!isChecked) return@addOnButtonCheckedListener
            val newVersion = if (checkedId == R.id.btnVersionGlobal) GameVersion.GLOBAL
                            else GameVersion.JAPANESE
            manager.gameVersion = newVersion
            updateVersionHint(newVersion)
        }
    }

    private fun updateVersionHint(version: GameVersion) {
        binding.tvVersionHint.text = getString(
            if (version == GameVersion.GLOBAL) R.string.hint_version_global
            else R.string.hint_version_japanese
        )
    }

    // ── Connect / toggle button ───────────────────────────────────────────────

    private fun setupConnectButton() {
        binding.btnToggle.setOnClickListener {
            if (!manager.isOpenVpnInstalled()) promptInstallOpenVpn()
            else handleToggle()
        }
    }

    private fun handleToggle() {
        val apiPermIntent = manager.getApiPermissionIntent()
        if (apiPermIntent != null) { requestApiPermission(); return }
        val vpnPermIntent = manager.getVpnServicePermissionIntent()
        if (vpnPermIntent != null) { requestVpnPermission(); return }
        manager.toggle()
    }

    private fun requestApiPermission() {
        val intent = manager.getApiPermissionIntent() ?: return
        apiPermissionLauncher.launch(intent)
    }

    private fun requestVpnPermission() {
        val intent = manager.getVpnServicePermissionIntent() ?: run {
            manager.connect(); return
        }
        vpnPermissionLauncher.launch(intent)
    }

    // ── Install banner ────────────────────────────────────────────────────────

    private fun setupInstallBanner() {
        binding.btnInstallOpenVpn.setOnClickListener { promptInstallOpenVpn() }
        binding.btnOpenVpnApp.setOnClickListener { openOpenVpnApp() }
    }

    // ── Timeout seekbar ───────────────────────────────────────────────────────

    private fun setupTimeoutSeekBar() {
        val range = UmaVpnManager.MAX_TIMEOUT_SECONDS - UmaVpnManager.MIN_TIMEOUT_SECONDS
        binding.seekTimeout.max = range
        binding.seekTimeout.progress = manager.connectTimeoutSeconds - UmaVpnManager.MIN_TIMEOUT_SECONDS
        updateTimeoutLabel(manager.connectTimeoutSeconds)

        binding.seekTimeout.setOnSeekBarChangeListener(
            object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                    val seconds = progress + UmaVpnManager.MIN_TIMEOUT_SECONDS
                    updateTimeoutLabel(seconds)
                    if (fromUser) manager.connectTimeoutSeconds = seconds
                }
                override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
            }
        )
    }

    private fun updateTimeoutLabel(seconds: Int) {
        binding.tvTimeoutLabel.text = getString(R.string.label_timeout, seconds)
    }

    // ── State observer ────────────────────────────────────────────────────────

    private fun observeState() {
        manager.state.onEach { state -> updateUI(state) }.launchIn(lifecycleScope)
    }

    private fun updateUI(state: ConnectionState) {
        val openVpnInstalled = manager.isOpenVpnInstalled()
        binding.cardInstallBanner.visibility = if (!openVpnInstalled) View.VISIBLE else View.GONE

        // Disable the version toggle while a connection is active
        val isIdle = state is ConnectionState.Idle || state is ConnectionState.Error
        binding.toggleGameVersion.isEnabled = isIdle
        binding.btnVersionGlobal.isEnabled = isIdle
        binding.btnVersionJapanese.isEnabled = isIdle

        when (state) {
            is ConnectionState.Idle -> {
                binding.statusIndicator.setImageResource(R.drawable.ic_status_disconnected)
                binding.tvStatus.text = getString(R.string.status_disconnected)
                binding.tvServer.visibility = View.GONE
                binding.tvPing.visibility = View.GONE
                binding.tvGameAccess.visibility = View.GONE
                binding.progressBar.visibility = View.GONE
                binding.btnToggle.text = getString(R.string.btn_connect)
                binding.btnToggle.isEnabled = openVpnInstalled
            }

            is ConnectionState.FetchingServers -> {
                binding.statusIndicator.setImageResource(R.drawable.ic_status_connecting)
                binding.tvStatus.text = getString(R.string.status_fetching)
                binding.tvServer.visibility = View.GONE
                binding.tvPing.visibility = View.GONE
                binding.tvGameAccess.visibility = View.GONE
                binding.progressBar.visibility = View.VISIBLE
                binding.btnToggle.text = getString(R.string.btn_cancel)
                binding.btnToggle.isEnabled = true
            }

            is ConnectionState.Connecting -> {
                binding.statusIndicator.setImageResource(R.drawable.ic_status_connecting)
                binding.tvStatus.text = if (state.total > 0)
                    getString(R.string.status_connecting, state.attempt, state.total)
                else getString(R.string.status_connecting, 1, 1)
                binding.tvServer.text = getString(R.string.label_server, state.serverIp)
                binding.tvServer.visibility = View.VISIBLE
                binding.tvPing.visibility = View.GONE
                binding.tvGameAccess.visibility = View.GONE
                binding.progressBar.visibility = View.VISIBLE
                binding.btnToggle.text = getString(R.string.btn_cancel)
                binding.btnToggle.isEnabled = true
            }

            is ConnectionState.VerifyingGame -> {
                binding.statusIndicator.setImageResource(R.drawable.ic_status_connecting)
                binding.tvStatus.text = getString(R.string.status_verifying)
                binding.tvServer.text = getString(R.string.label_server, state.serverIp)
                binding.tvServer.visibility = View.VISIBLE
                binding.tvPing.visibility = View.GONE
                binding.tvGameAccess.visibility = View.GONE
                binding.progressBar.visibility = View.VISIBLE
                binding.btnToggle.text = getString(R.string.btn_cancel)
                binding.btnToggle.isEnabled = true
            }

            is ConnectionState.Connected -> {
                binding.statusIndicator.setImageResource(R.drawable.ic_status_connected)
                binding.tvStatus.text = if (state.gameAccessible == false)
                    getString(R.string.status_connected_unverified)
                else getString(R.string.status_connected)
                binding.tvServer.text = getString(R.string.label_server, state.serverIp)
                binding.tvServer.visibility = View.VISIBLE
                binding.tvPing.text = getString(R.string.label_ping, String.format("%.0f", state.ping))
                binding.tvPing.visibility = View.VISIBLE
                binding.tvGameAccess.visibility = View.VISIBLE
                when (state.gameAccessible) {
                    true -> {
                        binding.tvGameAccess.text = getString(R.string.label_game_accessible)
                        binding.tvGameAccess.setTextColor(getColor(android.R.color.holo_green_light))
                    }
                    false -> {
                        binding.tvGameAccess.text = getString(R.string.label_game_blocked)
                        binding.tvGameAccess.setTextColor(getColor(android.R.color.holo_red_light))
                    }
                    null -> {
                        binding.tvGameAccess.text = getString(R.string.label_game_unverified)
                        binding.tvGameAccess.setTextColor(getColor(android.R.color.darker_gray))
                    }
                }
                binding.progressBar.visibility = View.GONE
                binding.btnToggle.text = getString(R.string.btn_disconnect)
                binding.btnToggle.isEnabled = true
            }

            is ConnectionState.Error -> {
                binding.statusIndicator.setImageResource(R.drawable.ic_status_error)
                binding.tvStatus.text = getString(R.string.status_error)
                binding.tvServer.text = state.message
                binding.tvServer.visibility = View.VISIBLE
                binding.tvPing.visibility = View.GONE
                binding.tvGameAccess.visibility = View.GONE
                binding.progressBar.visibility = View.GONE
                binding.btnToggle.text = getString(R.string.btn_retry)
                binding.btnToggle.isEnabled = openVpnInstalled
            }

            is ConnectionState.Disconnecting -> {
                binding.statusIndicator.setImageResource(R.drawable.ic_status_connecting)
                binding.tvStatus.text = getString(R.string.status_disconnecting)
                binding.tvServer.visibility = View.GONE
                binding.tvPing.visibility = View.GONE
                binding.tvGameAccess.visibility = View.GONE
                binding.progressBar.visibility = View.VISIBLE
                binding.btnToggle.isEnabled = false
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun promptInstallOpenVpn() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=de.blinkt.openvpn")))
        } catch (e: Exception) {
            startActivity(Intent(Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=de.blinkt.openvpn")))
        }
    }

    private fun openOpenVpnApp() {
        packageManager.getLaunchIntentForPackage("de.blinkt.openvpn")
            ?.let { startActivity(it) }
            ?: showSnackbar("OpenVPN for Android is not installed.")
    }

    private fun showSnackbar(message: String) =
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
}
