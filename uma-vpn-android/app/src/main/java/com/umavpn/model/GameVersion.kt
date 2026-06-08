package com.umavpn.model

enum class GameVersion(
    val label: String,
    /** umavpn.top site filter: umag = Global, uma = Japanese */
    val site: String,
    /** Country filter sent to the API. Must be null for Global — umag + JP returns zero servers. */
    val countryCode: String?,
) {
    GLOBAL(
        label = "Global",
        site = "umag",
        countryCode = null,
    ),

    JAPANESE(
        label = "Japanese",
        site = "uma",
        countryCode = "JP",
    );

    val connectivityTestUrl: String
        get() = "https://api-umamusume.cygames.jp/"

    companion object {
        fun fromOrdinal(ordinal: Int): GameVersion =
            entries.getOrElse(ordinal) { GLOBAL }
    }
}
