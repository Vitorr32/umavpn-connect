package com.umavpn.api

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.umavpn.model.GameVersion
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
     * Fetches VPN servers verified for [gameVersion] from api.umavpn.top.
     * Global uses sites=umag with no country filter; Japanese uses sites=uma + country=JP.
     */
    @Throws(IOException::class)
    fun fetchServers(gameVersion: GameVersion, take: Int = 30): List<VpnServer> {
        val urlBuilder = "$BASE_URL/api/server".toHttpUrl().newBuilder()
            .addQueryParameter("sites", gameVersion.site)
            .addQueryParameter("take", take.toString())
            .addQueryParameter("orderBy", "duration")

        // Global servers are outside Japan — never apply country=JP for umag.
        gameVersion.countryCode?.let { urlBuilder.addQueryParameter("country", it) }

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
                ?: throw IOException("Empty response from api.umavpn.top")

            val type = object : TypeToken<ApiResponse<List<ServerEntry>>>() {}.type
            val parsed: ApiResponse<List<ServerEntry>> = gson.fromJson(body, type)

            if (!parsed.success) {
                throw IOException("API returned success=false")
            }

            val entries = parsed.data.orEmpty()
            Log.d(TAG, "Fetched ${entries.size} servers for ${gameVersion.label}")

            if (entries.isEmpty()) {
                throw IOException(
                    "No servers available for the ${gameVersion.label} version right now. " +
                        "Try again in a few minutes."
                )
            }

            return entries.map { entry ->
                VpnServer(
                    ip = entry.ip,
                    ping = entry.duration.toDouble(),
                    country = entry.country,
                )
            }
        }
    }

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
        private const val TAG = "UmaVpnApiClient"
        private const val BASE_URL = "https://api.umavpn.top"
        private const val USER_AGENT = "UmaVPN-Android/1.4"
    }
}
