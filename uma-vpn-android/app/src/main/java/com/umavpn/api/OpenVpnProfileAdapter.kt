package com.umavpn.api

import android.util.Log

/**
 * Prepares inline OpenVPN configs for [de.blinkt.openvpn] (OpenVPN for Android).
 *
 * Manual imports through OpenVPN Connect use a different engine (OpenVPN3) and can
 * succeed with the raw API file. The AIDL [startVPN] path is stricter about inline
 * formatting and OpenVPN 2.x cipher directives.
 */
object OpenVpnProfileAdapter {

    private const val TAG = "OpenVpnProfileAdapter"

    /**
     * Normalizes a profile fetched from api.umavpn.top before passing it to
     * `IOpenVPNAPIService.startVPN()`.
     */
    fun forOpenVpnForAndroid(rawProfile: String, variant: OpenVpnProfileVariant): String {
        var profile = rawProfile
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .trim()

        if (variant == OpenVpnProfileVariant.LEGACY) {
            profile = stripDataCiphers(profile)
        } else {
            profile = ensureDataCiphersIncludesCipher(profile)
        }

        Log.d(
            TAG,
            "Prepared ${variant.name} profile (${profile.length} chars, " +
                "cipher=${profile.contains("\ncipher ")}, " +
                "data-ciphers=${profile.contains("\ndata-ciphers ")}, " +
                "splitTunnel=${profile.contains("\nroute-nopull")})"
        )
        return profile
    }

    /** Removes OpenVPN 2.5+ `data-ciphers` lines (legacy / pre-2.6 clients). */
    private fun stripDataCiphers(profile: String): String =
        profile.lineSequence()
            .filterNot { it.trimStart().startsWith("data-ciphers ") }
            .joinToString("\n")

    /**
     * OpenVPN 2.6+ ignores `--cipher` unless the value is listed in `--data-ciphers`.
     * The API's `current` variant already sets both; this guards partially-generated configs.
     */
    private fun ensureDataCiphersIncludesCipher(profile: String): String {
        val cipher = profile.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("cipher ") }
            ?.removePrefix("cipher ")
            ?.trim()
            ?: return profile

        val dataCiphersLine = profile.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("data-ciphers ") }
            ?: return "$profile\ndata-ciphers $cipher"

        val listed = dataCiphersLine.removePrefix("data-ciphers ").split(':')
        return if (listed.any { it.equals(cipher, ignoreCase = true) }) {
            profile
        } else {
            profile.replace(
                dataCiphersLine,
                "data-ciphers $cipher:${dataCiphersLine.removePrefix("data-ciphers ")}"
            )
        }
    }
}
