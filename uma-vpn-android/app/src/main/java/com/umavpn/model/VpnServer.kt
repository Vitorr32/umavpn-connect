package com.umavpn.model

data class VpnServer(
    val profile: String,
    val ip: String,
    val ping: Double,
    val country: String = "",
) {
    val remoteHost: String
        get() = ip.ifEmpty {
            val remoteLine = profile.lines().firstOrNull { it.trimStart().startsWith("remote ") }
            remoteLine?.trim()?.split(" ")?.getOrNull(1) ?: "unknown"
        }

    val remotePort: String
        get() {
            val remoteLine = profile.lines().firstOrNull { it.trimStart().startsWith("remote ") }
            return remoteLine?.trim()?.split(" ")?.getOrNull(2) ?: "unknown"
        }
}
