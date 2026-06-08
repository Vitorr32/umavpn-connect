package com.umavpn.api

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.umavpn.model.GameVersion
import com.umavpn.model.VpnServer
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class UmapyoiApiClient {

    companion object {
        private const val TAG = "UmapyoiApiClient"
        private const val CYGAMES_URL = "https://umapyoi.net/api/v1/vpn/cygames"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val geoIp = GeoIpClient()

    /**
     * Fetches the full verified server list, then filters it by [gameVersion]:
     *  - GLOBAL  → excludes Japanese IPs (Cygames blocks JP IPs on the global client)
     *  - JAPANESE → keeps only Japanese IPs
     *
     * The API already sorts by Cygames ping (ascending), so the first entry in the
     * returned list is always the fastest passing server for the selected version.
     *
     * @throws IOException if the network call fails or the response is empty.
     */
    @Throws(IOException::class)
    fun fetchServers(gameVersion: GameVersion): List<VpnServer> {
        val all = fetchAllServers()
        if (all.isEmpty()) throw IOException("No verified VPN servers available right now.")

        val ips = all.map { it.remoteHost }
        val countryMap = geoIp.lookupCountries(ips)

        val filtered = all.filter { server ->
            val country = countryMap[server.remoteHost] ?: "??"
            when {
                gameVersion.onlyCountryCode != null ->
                    country.equals(gameVersion.onlyCountryCode, ignoreCase = true)
                gameVersion.excludeCountryCode != null ->
                    !country.equals(gameVersion.excludeCountryCode, ignoreCase = true)
                else -> true
            }
        }

        Log.d(TAG, "Servers after ${gameVersion.label} filter: ${filtered.size}/${all.size}")

        if (filtered.isEmpty()) {
            throw IOException(
                "No servers available for the ${gameVersion.label} version right now. " +
                    "Try again in a few minutes."
            )
        }
        return filtered
    }

    @Throws(IOException::class)
    private fun fetchAllServers(): List<VpnServer> {
        val request = Request.Builder()
            .url(CYGAMES_URL)
            .header("User-Agent", "UmaVPN-Android/1.0")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("API request failed: HTTP ${response.code}")
            }
            val body = response.body?.string()
                ?: throw IOException("Empty response from umapyoi.net")
            val type = object : TypeToken<List<VpnServer>>() {}.type
            return gson.fromJson(body, type)
        }
    }
}
