package com.umavpn.api

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.umavpn.model.ApiResponse
import com.umavpn.model.ServerEntry
import com.umavpn.model.VpnServer
import okhttp3.HttpUrl.Companion.toHttpUrl
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
     * The list is sorted by ping (duration) ascending — fastest first.
     */
    @Throws(IOException::class)
    fun fetchCygamesServers(): List<VpnServer> {
        val entries = fetchServerEntries()
        return entries.map { entry ->
            VpnServer(
                profile = fetchOpenVpnProfile(entry.ip),
                ip = entry.ip,
                ping = entry.duration.toDouble()
            )
        }
    }

    /**
     * Returns the best available server — the one with the lowest ping to Cygames.
     */
    @Throws(IOException::class)
    fun fetchBestServer(): VpnServer {
        val entry = fetchServerEntries().firstOrNull()
            ?: throw IOException("No verified VPN servers available right now. Please try again later.")

        return VpnServer(
            profile = fetchOpenVpnProfile(entry.ip),
            ip = entry.ip,
            ping = entry.duration.toDouble()
        )
    }

    private fun fetchServerEntries(): List<ServerEntry> {
        val url = API_BASE.toHttpUrl().newBuilder()
            .addPathSegments("api/server")
            .addQueryParameter("sites", SITE_UMAMUSUME_GLOBAL)
            .addQueryParameter("take", SERVER_LIST_SIZE.toString())
            .addQueryParameter("orderBy", "duration")
            .build()

        val request = newApiRequest(url.toString()).get().build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("API request failed: HTTP ${response.code}")
            }
            val body = response.body?.string()
                ?: throw IOException("Empty response body from API")

            val type = object : TypeToken<ApiResponse<List<ServerEntry>>>() {}.type
            val parsed: ApiResponse<List<ServerEntry>> = gson.fromJson(body, type)
                ?: throw IOException("Invalid response from API")

            if (!parsed.success) {
                throw IOException("API returned success=false")
            }

            return parsed.data.orEmpty()
        }
    }

    private fun fetchOpenVpnProfile(ip: String): String {
        val url = API_BASE.toHttpUrl().newBuilder()
            .addPathSegments("api/server/$ip/config")
            .addQueryParameter("variant", "current")
            .build()

        val request = newApiRequest(url.toString()).get().build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Failed to fetch OpenVPN profile for $ip: HTTP ${response.code}")
            }
            val profile = response.body?.string()?.trim()
            if (profile.isNullOrEmpty()) {
                throw IOException("Empty OpenVPN profile for $ip")
            }
            return profile
        }
    }

    private fun newApiRequest(url: String): Request.Builder =
        Request.Builder()
            .url(url)
            .header("Accept", "*/*")
            .header("User-Agent", USER_AGENT)
            .header("Referer", REFERER)

    companion object {
        private const val API_BASE = "https://api.umavpn.top"
        private const val REFERER = "https://www.umavpn.top/"
        private const val USER_AGENT = "UmaVPN-Android/1.0"
        private const val SITE_UMAMUSUME_GLOBAL = "umag"
        private const val SERVER_LIST_SIZE = 20
    }
}
