package com.umavpn.model

sealed class ConnectionState {
    object Idle : ConnectionState()
    object FetchingServers : ConnectionState()
    data class Connecting(
        val serverIp: String,
        val attempt: Int = 0,
        val total: Int = 0
    ) : ConnectionState()
    /** VPN tunnel is up — verifying the Cygames server is reachable through it. */
    data class VerifyingGame(val serverIp: String) : ConnectionState()
    data class Connected(
        val serverIp: String,
        val ping: Double,
        /** null = test not run, true = game server accessible (HTTP 404), false = blocked (HTTP 403) */
        val gameAccessible: Boolean? = null
    ) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
    object Disconnecting : ConnectionState()
}
