package com.umavpn.api

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.umavpn.model.VpnServer
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class UmapyoiApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    /**
     * Fetches VPN servers verified to work with Cygames (Umamusume Global).
     * The list is already sorted by ping time ascending (fastest first).
     */
    @Throws(IOException::class)
    fun fetchCygamesServers(): List<VpnServer> {
        val request = Request.Builder()
            .url("https://umapyoi.net/api/v1/vpn/cygames")
            .header("User-Agent", "UmaVPN-Android/1.0")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("API request failed: HTTP ${response.code}")
            }
            val body = response.body?.string()
                ?: throw IOException("Empty response body from API")

            val type = object : TypeToken<List<VpnServer>>() {}.type
            return gson.fromJson(body, type)
        }
    }

    /**
     * Returns the best available server — the one with the lowest Cygames ping.
     * The API already sorts by ping, so we just take the first entry.
     */
    @Throws(IOException::class)
    fun fetchBestServer(): VpnServer {
        val servers = fetchCygamesServers()
        return servers.firstOrNull()
            ?: throw IOException("No verified VPN servers available right now. Please try again later.")
    }
}
