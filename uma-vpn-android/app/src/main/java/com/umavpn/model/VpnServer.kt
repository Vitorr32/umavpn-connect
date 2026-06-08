package com.umavpn.model

import com.google.gson.annotations.SerializedName

data class VpnServer(
    @SerializedName("_profile") val profile: String,
    @SerializedName("cygames") val cygames: PingResult,
    @SerializedName("dmm") val dmm: PingResult
) {
    val remoteHost: String
        get() {
            val remoteLine = profile.lines().firstOrNull { it.trimStart().startsWith("remote ") }
            return remoteLine?.trim()?.split(" ")?.getOrNull(1) ?: "unknown"
        }

    val remotePort: String
        get() {
            val remoteLine = profile.lines().firstOrNull { it.trimStart().startsWith("remote ") }
            return remoteLine?.trim()?.split(" ")?.getOrNull(2) ?: "unknown"
        }
}

data class PingResult(
    @SerializedName("ping") val ping: Double,
    @SerializedName("success") val success: Boolean
)
