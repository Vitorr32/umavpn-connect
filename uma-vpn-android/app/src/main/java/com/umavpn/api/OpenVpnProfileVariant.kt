package com.umavpn.api

/**
 * OpenVPN config variants served by api.umavpn.top.
 *
 * The official umavpn.top guide recommends **Beta** for modern clients (OpenVPN > 2.7).
 * [CONNECT_FALLBACK_ORDER] is used when the primary variant fails to establish a tunnel.
 *
 * - [BETA] — OpenVPN > 2.7 (primary; recommended by the player guide)
 * - [CURRENT] — OpenVPN 2.6.x (`data-ciphers` present)
 * - [LEGACY] — OpenVPN before 2.6 (`cipher` only; VPNGate legacy encryption)
 */
enum class OpenVpnProfileVariant(val apiValue: String) {
    BETA("beta"),
    CURRENT("current"),
    LEGACY("legacy");

    companion object {
        /** Primary variant per the umavpn.top guide, then legacy fallback. */
        val PRIMARY = BETA

        val CONNECT_FALLBACK_ORDER = listOf(BETA, LEGACY)
    }
}
