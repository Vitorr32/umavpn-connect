package com.umavpn.model

data class VpnServer(
    val profile: String,
    val remoteHost: String,
    val pingMs: Double,
    val country: String = "??",
) {
    val remotePort: String
        get() {
            val remoteLine = profile.lines().firstOrNull { it.trimStart().startsWith("remote ") }
            return remoteLine?.trim()?.split(" ")?.getOrNull(2) ?: "unknown"
        }
}
