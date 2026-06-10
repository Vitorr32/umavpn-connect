package com.umavpn.api

/**
 * OpenVPN config variants served by api.umavpn.top.
 *
 * The umavpn.top website exposes the same selector for manual downloads:
 * - [CURRENT] — OpenVPN 2.6.x (`data-ciphers` present)
 * - [LEGACY] — OpenVPN before 2.6 (`cipher` only)
 * - [BETA] — OpenVPN after 2.7 (currently identical to [CURRENT] on the API)
 *
 * OpenVPN Connect (recommended on the website) uses the OpenVPN3 engine and is
 * more forgiving than OpenVPN for Android (community OpenVPN 2.x via AIDL).
 */
enum class OpenVpnProfileVariant(val apiValue: String) {
    CURRENT("current"),
    LEGACY("legacy"),
    BETA("beta"),
}
