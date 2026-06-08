package com.umavpn.model

sealed class ConnectionState {
    object Idle : ConnectionState()
    object FetchingServers : ConnectionState()
    data class Connecting(val serverIp: String) : ConnectionState()
    data class Connected(val serverIp: String, val ping: Double) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
    object Disconnecting : ConnectionState()
}
