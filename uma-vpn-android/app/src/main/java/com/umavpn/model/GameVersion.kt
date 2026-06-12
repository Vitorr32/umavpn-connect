package com.umavpn.model

/** How to interpret the post-connect HTTP probe for a [GameVersion]. */
enum class ConnectivityCheckStyle {
    /** Cygames JP API: HTTP 404 = allowed, 403 = geo-blocked. */
    CYGAMES_API,
    /** Global website: HTTP 2xx = allowed, 403 = geo-blocked. */
    GLOBAL_WEBSITE,
}

enum class GameVersion(
    val label: String,
    /** Site filter on api.umavpn.top (matches umavpn.top checker). */
    val siteCode: String,
    val excludeCountryCode: String?,
    val onlyCountryCode: String?,
    val launchPackageName: String,
    /**
     * When true, fetch profiles with `split=true` so only game-related domains are
     * routed through the VPN (per the umavpn.top "Split tunneling" variant).
     */
    val useSplitTunnel: Boolean,
    /** Endpoint probed after the VPN tunnel is up (see umavpn.top player guide). */
    val connectivityTestUrl: String,
    val connectivityCheckStyle: ConnectivityCheckStyle,
) {
    /**
     * Umamusume: Pretty Derby — Global release (Cygames worldwide).
     * The global client is blocked by Cygames when connecting from Japanese IPs,
     * so we filter out JP-located VPN servers entirely.
     */
    GLOBAL(
        label = "Global",
        siteCode = "umag",
        excludeCountryCode = "JP",
        onlyCountryCode = null,
        launchPackageName = "com.cygames.umamusume",
        // Route-based split tunnel needs OpenVPN 2.7 on desktop; on Android the probe
        // must hit umamusume.com through the full tunnel (not in the route-nopull list).
        useSplitTunnel = false,
        connectivityTestUrl = "https://umamusume.com/",
        connectivityCheckStyle = ConnectivityCheckStyle.GLOBAL_WEBSITE,
    ),

    /**
     * Umamusume: Pretty Derby — Japanese version (DMM / Cygames JP).
     * Requires a Japanese IP to pass the geo-check on Cygames servers.
     */
    JAPANESE(
        label = "Japanese",
        siteCode = "uma",
        excludeCountryCode = null,
        onlyCountryCode = "JP",
        launchPackageName = "jp.co.cygames.umamusume",
        useSplitTunnel = false,
        connectivityTestUrl = "https://api-umamusume.cygames.jp/",
        connectivityCheckStyle = ConnectivityCheckStyle.CYGAMES_API,
    );

    companion object {
        /** ISO country codes tried first when connecting in Global mode. */
        val PREFERRED_SEA_COUNTRIES = setOf("TH", "VN", "KH")

        fun fromOrdinal(ordinal: Int): GameVersion =
            entries.getOrElse(ordinal) { GLOBAL }
    }
}
