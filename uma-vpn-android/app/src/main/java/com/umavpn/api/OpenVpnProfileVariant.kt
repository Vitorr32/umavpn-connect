package com.umavpn.api

/**
 * OpenVPN config variants served by api.umavpn.top, plus the compatibility settings
 * [OpenVpnProfileAdapter] adds for OpenVPN for Android.
 *
 * The official umavpn.top guide recommends **Beta** for modern clients (OpenVPN > 2.7).
 * [CONNECT_FALLBACK_ORDER] is used when the primary variant fails to establish a tunnel.
 *
 * - [BETA] — OpenVPN > 2.7 (primary; recommended by the player guide)
 * - [CURRENT] — OpenVPN 2.6.x (`data-ciphers` present)
 * - [LEGACY] — OpenVPN before 2.6 (`cipher` only; VPNGate legacy encryption)
 *
 * VPN Gate relays (SoftEther) never negotiate the data-channel cipher, so every variant
 * needs OpenVPN's non-NCP fallback to AES-128-CBC. [compatMode] makes OpenVPN for Android
 * keep the `cipher` line when it regenerates the profile (0.7.65+ drops it otherwise):
 * `2.4.0` only re-adds the cipher to `data-ciphers`; `2.3.0` — what the umavpn.top guide
 * tells Android users to pick manually — additionally enables the fallback and TLS 1.0.
 */
enum class OpenVpnProfileVariant(
    val apiValue: String,
    /** Value written as `compat-mode` into the prepared profile; null = none. */
    val compatMode: String?,
    /** Drop `data-ciphers` so only `cipher` + `data-ciphers-fallback` remain. */
    val stripDataCiphers: Boolean,
) {
    BETA("beta", compatMode = "2.4.0", stripDataCiphers = false),
    CURRENT("current", compatMode = "2.4.0", stripDataCiphers = false),
    LEGACY("legacy", compatMode = "2.3.0", stripDataCiphers = true);

    companion object {
        /** Primary variant per the umavpn.top guide, then legacy fallback. */
        val PRIMARY = BETA

        val CONNECT_FALLBACK_ORDER = listOf(BETA, LEGACY)
    }
}
