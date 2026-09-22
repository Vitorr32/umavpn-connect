package com.umavpn.api

import android.util.Log

/**
 * Prepares inline OpenVPN configs for [de.blinkt.openvpn] (OpenVPN for Android).
 *
 * OpenVPN for Android does not hand the inline config to OpenVPN verbatim: it parses it
 * into a profile and regenerates the config from that. Only directives it understands
 * (`cipher`, `data-ciphers`, `compat-mode`, ...) or passes through as custom options
 * (`data-ciphers-fallback`) survive, so this adapter has to work within those rules.
 *
 * Why the cipher directives matter: VPN Gate relays run SoftEther, whose OpenVPN server
 * does not negotiate the data-channel cipher (no NCP) and never pushes `cipher`. The
 * client therefore falls back to the cipher named in its own config (AES-128-CBC). Since
 * OpenVPN 2.6 the plain `cipher` directive only serves that fallback when it is also
 * listed in `data-ciphers`, and OpenVPN for Android 0.7.65 (September 2026) stopped
 * writing the `cipher` line at all unless `compat-mode` is set. Without either, OpenVPN
 * assumes BF-CBC, rejects it with "negotiated cipher not allowed - BF-CBC not in
 * AES-128-CBC" and every VPN Gate connection fails.
 *
 * The prepared profile therefore always carries:
 *  - `cipher <c>` and, unless the variant strips it, `data-ciphers` including `<c>`;
 *  - `data-ciphers-fallback <c>` — the OpenVPN 2.5+ way to name the cipher used with
 *    servers that do not negotiate one (kept by OpenVPN for Android as a custom option);
 *  - `compat-mode` from [OpenVpnProfileVariant.compatMode], which keeps the `cipher`
 *    line in the regenerated config.
 */
object OpenVpnProfileAdapter {

    private const val TAG = "OpenVpnProfileAdapter"

    /** Data-channel cipher used by VPN Gate / SoftEther OpenVPN relays. */
    const val DEFAULT_CIPHER = "AES-128-CBC"

    /**
     * Normalizes a profile fetched from api.umavpn.top before passing it to
     * `IOpenVPNAPIService.startVPN()`.
     */
    fun forOpenVpnForAndroid(rawProfile: String, variant: OpenVpnProfileVariant): String {
        val lines = rawProfile
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .trim()
            .lines()
            .toMutableList()

        val cipher = directiveValue(lines, "cipher") ?: DEFAULT_CIPHER

        // Directives this adapter owns: drop whatever the API sent and re-add them below.
        removeDirective(lines, "data-ciphers-fallback")
        removeDirective(lines, "compat-mode")

        if (variant.stripDataCiphers) {
            removeDirective(lines, "data-ciphers")
        } else {
            ensureDataCiphersIncludes(lines, cipher)
        }
        if (directiveValue(lines, "cipher") == null) {
            insertDirectives(lines, listOf("cipher $cipher"))
        }

        val extra = mutableListOf("data-ciphers-fallback $cipher")
        variant.compatMode?.let { extra.add("compat-mode $it") }
        insertDirectives(lines, extra)

        val profile = lines.joinToString("\n")
        Log.d(
            TAG,
            "Prepared ${variant.name} profile (${profile.length} chars, cipher=$cipher, " +
                "data-ciphers=${directiveValue(lines, "data-ciphers") ?: "none"}, " +
                "compat-mode=${variant.compatMode ?: "none"}, " +
                "splitTunnel=${lines.any { isDirective(it, "route-nopull") }})"
        )
        return profile
    }

    private fun isDirective(line: String, name: String): Boolean {
        val trimmed = line.trimStart()
        return trimmed == name || trimmed.startsWith("$name ") || trimmed.startsWith("$name\t")
    }

    private fun directiveValue(lines: List<String>, name: String): String? =
        lines.firstOrNull { isDirective(it, name) }
            ?.trim()
            ?.removePrefix(name)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private fun removeDirective(lines: MutableList<String>, name: String) {
        lines.removeAll { isDirective(it, name) }
    }

    /**
     * OpenVPN 2.6+ only honours `cipher` for the non-NCP fallback when the value is also
     * listed in `data-ciphers`. The API's profiles already set both; this guards
     * partially generated configs.
     */
    private fun ensureDataCiphersIncludes(lines: MutableList<String>, cipher: String) {
        val index = lines.indexOfFirst { isDirective(it, "data-ciphers") }
        if (index < 0) {
            insertDirectives(lines, listOf("data-ciphers $cipher"))
            return
        }
        val listed = lines[index].trim().removePrefix("data-ciphers").trim()
        if (listed.split(':').none { it.equals(cipher, ignoreCase = true) }) {
            lines[index] = "data-ciphers $cipher:$listed"
        }
    }

    /** Inserts plain directives before the first inline `<tag>` block (or at the end). */
    private fun insertDirectives(lines: MutableList<String>, directives: List<String>) {
        val firstInlineBlock = lines.indexOfFirst { it.trimStart().startsWith("<") }
        val at = if (firstInlineBlock < 0) lines.size else firstInlineBlock
        lines.addAll(at, directives)
    }
}
