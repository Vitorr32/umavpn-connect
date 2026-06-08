package com.umavpn.model

enum class GameVersion(val label: String, val excludeCountryCode: String?, val onlyCountryCode: String?) {
    /**
     * Umamusume: Pretty Derby — Global release (Cygames worldwide).
     * The global client is blocked by Cygames when connecting from Japanese IPs,
     * so we filter out JP-located VPN servers entirely.
     */
    GLOBAL(
        label = "Global",
        excludeCountryCode = "JP",
        onlyCountryCode = null
    ),

    /**
     * Umamusume: Pretty Derby — Japanese version (DMM / Cygames JP).
     * Requires a Japanese IP to pass the geo-check on Cygames servers.
     */
    JAPANESE(
        label = "Japanese",
        excludeCountryCode = null,
        onlyCountryCode = "JP"
    );

    /** The Cygames API endpoint used to verify a server can actually reach the game. */
    val connectivityTestUrl: String
        get() = "https://api-umamusume.cygames.jp/"

    companion object {
        fun fromOrdinal(ordinal: Int): GameVersion =
            entries.getOrElse(ordinal) { GLOBAL }
    }
}
