package com.umavpn.api

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.umavpn.model.GameMode
import com.umavpn.model.VpnServer
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class UmaVpnApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    /**
     * Fetches VPN servers verified for the given [mode] from api.umavpn.top.
     * Results are ordered by lowest ping ([orderBy]=duration).
     */
    @Throws(IOException::class)
    fun fetchServers(
        mode: GameMode = GameMode.GLOBAL,
        take: Int = 10,
        orderBy: String = "duration",
    ): List<ServerEntry> {
        val urlBuilder = "$BASE_URL/api/server".toHttpUrl().newBuilder()
            .addQueryParameter("sites", mode.site)
            .addQueryParameter("take", take.toString())
            .addQueryParameter("orderBy", orderBy)

        // Global (umag) servers are outside Japan — never apply country=JP here.
        mode.countryCode?.let { urlBuilder.addQueryParameter("country", it) }

        val request = Request.Builder()
            .url(urlBuilder.build())
            .header("Accept", "*/*")
            .header("User-Agent", USER_AGENT)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("API request failed: HTTP ${response.code}")
            }
            val body = response.body?.string()
                ?: throw IOException("Empty response body from API")

            val type = object : TypeToken<ApiResponse<List<ServerEntry>>>() {}.type
            val parsed: ApiResponse<List<ServerEntry>> = gson.fromJson(body, type)

            if (!parsed.success) {
                throw IOException("API returned success=false")
            }
            return parsed.data.orEmpty()
        }
    }

    /**
     * Downloads the OpenVPN profile for [ip].
     */
    @Throws(IOException::class)
    fun fetchServerConfig(ip: String): String {
        val request = Request.Builder()
            .url("$BASE_URL/api/server/$ip/config?variant=current")
            .header("Accept", "*/*")
            .header("User-Agent", USER_AGENT)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Failed to fetch VPN profile for $ip: HTTP ${response.code}")
            }
            return response.body?.string()?.takeIf { it.isNotBlank() }
                ?: throw IOException("Empty VPN profile for $ip")
        }
    }

    /**
     * Returns the best available server for [mode] (lowest ping) with its OpenVPN profile.
     */
    @Throws(IOException::class)
    fun fetchBestServer(mode: GameMode = GameMode.GLOBAL): VpnServer {
        val servers = fetchServers(mode)
        val best = servers.firstOrNull()
            ?: throw IOException("No verified VPN servers available right now. Please try again later.")

        val profile = fetchServerConfig(best.ip)
        return VpnServer(
            profile = profile,
            ip = best.ip,
            ping = best.duration.toDouble(),
            country = best.country,
        )
    }

    data class ApiResponse<T>(
        val success: Boolean,
        val data: T?,
    )

    data class ServerEntry(
        val ip: String,
        val country: String,
        val timestamp: String,
        val duration: Int,
        val speed: Double,
    )

    companion object {
        private const val BASE_URL = "https://api.umavpn.top"
        private const val USER_AGENT = "UmaVPN-Android/1.0"
    }
}
