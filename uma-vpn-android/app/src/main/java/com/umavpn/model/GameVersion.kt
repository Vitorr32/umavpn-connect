package com.umavpn.model

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
    /** Cygames endpoint used to verify VPN reachability for this game version. */
    val connectivityTestUrl: String,
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
        useSplitTunnel = true,
        connectivityTestUrl = "https://api.games.umamusume.jp/",
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
    );

    companion object {
        /** ISO country codes tried first when connecting in Global mode. */
        val PREFERRED_SEA_COUNTRIES = setOf("TH", "VN", "KH")

        fun fromOrdinal(ordinal: Int): GameVersion =
            entries.getOrElse(ordinal) { GLOBAL }
    }
}
