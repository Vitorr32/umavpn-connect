package com.umavpn.model

import com.google.gson.annotations.SerializedName

data class ApiResponse<T>(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data") val data: T?
)

data class ServerEntry(
    @SerializedName("ip") val ip: String,
    @SerializedName("country") val country: String,
    @SerializedName("timestamp") val timestamp: String,
    @SerializedName("duration") val duration: Int,
    @SerializedName("speed") val speed: Double
)

data class VpnServer(
    val profile: String,
    val ip: String,
    val ping: Double
) {
    val remoteHost: String
        get() {
            val remoteLine = profile.lines().firstOrNull { it.trimStart().startsWith("remote ") }
            return remoteLine?.trim()?.split(" ")?.getOrNull(1) ?: ip
        }

    val remotePort: String
        get() {
            val remoteLine = profile.lines().firstOrNull { it.trimStart().startsWith("remote ") }
            return remoteLine?.trim()?.split(" ")?.getOrNull(2) ?: "unknown"
        }
}
