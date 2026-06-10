package com.umavpn.api

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.umavpn.model.GameVersion
import com.umavpn.model.VpnServer
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Fetches VPN servers from [api.umavpn.top] — the same backend used by
 * [umavpn.top](https://www.umavpn.top). Servers are pre-verified per game
 * version (`umag` = Global, `uma` = Japanese) and include country metadata.
 */
class VpnApiClient {

    companion object {
        private const val TAG = "VpnApiClient"
        private const val BASE_URL = "https://api.umavpn.top"
        private const val SERVER_TAKE = 100
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    private data class ApiResponse(
        @SerializedName("success") val success: Boolean,
        @SerializedName("data") val data: List<ServerEntry>?,
    )

    private data class ServerEntry(
        @SerializedName("ip") val ip: String,
        @SerializedName("country") val country: String,
        @SerializedName("duration") val duration: Int,
    )

    /**
     * Fetches verified servers for [gameVersion], sorted by latency (ascending).
     *
     * @throws IOException if the network call fails or no servers match the version.
     */
    @Throws(IOException::class)
    fun fetchServers(gameVersion: GameVersion): List<VpnServer> {
        val entries = fetchServerEntries(gameVersion)
        if (entries.isEmpty()) {
            throw IOException(
                "No servers available for the ${gameVersion.label} version right now. " +
                    "Try again in a few minutes."
            )
        }

        val servers = entries.mapNotNull { entry ->
            runCatching {
                val profile = fetchConfig(
                    ip = entry.ip,
                    variant = OpenVpnProfileVariant.PRIMARY,
                    splitTunnel = gameVersion.useSplitTunnel,
                )
                VpnServer(
                    profile = profile,
                    remoteHost = entry.ip,
                    pingMs = entry.duration.toDouble(),
                    country = entry.country,
                )
            }.getOrElse { error ->
                Log.w(TAG, "Skipping ${entry.ip}: ${error.message}")
                null
            }
        }.let { sortServers(it, gameVersion) }

        if (servers.isEmpty()) {
            throw IOException("Failed to download OpenVPN profiles for any server.")
        }

        Log.d(
            TAG,
            "Loaded ${servers.size}/${entries.size} servers for ${gameVersion.label} " +
                "(variant=${OpenVpnProfileVariant.PRIMARY.apiValue}, " +
                "splitTunnel=${gameVersion.useSplitTunnel})"
        )
        return servers
    }

    @Throws(IOException::class)
    private fun fetchServerEntries(gameVersion: GameVersion): List<ServerEntry> {
        val url = "$BASE_URL/api/server".toHttpUrl().newBuilder()
            .addQueryParameter("sites", gameVersion.siteCode)
            .addQueryParameter("take", SERVER_TAKE.toString())
            .addQueryParameter("orderBy", "timestamp")
            .build()

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "UmaVPN-Android/1.0")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("API request failed: HTTP ${response.code}")
            }
            val body = response.body?.string()
                ?: throw IOException("Empty response from api.umavpn.top")
            val parsed = gson.fromJson(body, ApiResponse::class.java)
            if (!parsed.success || parsed.data == null) {
                throw IOException("Server list unavailable from api.umavpn.top")
            }

            val filtered = parsed.data.filter { entry ->
                when {
                    gameVersion.onlyCountryCode != null ->
                        entry.country.equals(gameVersion.onlyCountryCode, ignoreCase = true)
                    gameVersion.excludeCountryCode != null ->
                        !entry.country.equals(gameVersion.excludeCountryCode, ignoreCase = true)
                    else -> true
                }
            }

            Log.d(
                TAG,
                "Servers after ${gameVersion.label} filter: ${filtered.size}/${parsed.data.size}"
            )
            return filtered
        }
    }

    private fun sortServers(servers: List<VpnServer>, gameVersion: GameVersion): List<VpnServer> {
        if (gameVersion != GameVersion.GLOBAL) {
            return servers.sortedBy { it.pingMs }
        }
        return servers.sortedWith(
            compareBy<VpnServer> { server ->
                if (server.country.uppercase() in GameVersion.PREFERRED_SEA_COUNTRIES) 0 else 1
            }.thenBy { it.pingMs }
        )
    }

    /**
     * Downloads the OpenVPN inline config for [ip] using the given [variant].
     * When [splitTunnel] is true, appends `split=true` so the API patches in
     * `route-nopull` and game-domain routes (umavpn.top "Split tunneling" variant).
     */
    @Throws(IOException::class)
    fun fetchConfig(
        ip: String,
        variant: OpenVpnProfileVariant,
        splitTunnel: Boolean = false,
    ): String {
        val urlBuilder = "$BASE_URL/api/server/$ip/config".toHttpUrl().newBuilder()
            .addQueryParameter("variant", variant.apiValue)
        if (splitTunnel) {
            urlBuilder.addQueryParameter("split", "true")
        }
        val url = urlBuilder.build()

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "UmaVPN-Android/1.0")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Config request failed: HTTP ${response.code}")
            }
            return response.body?.string()?.takeIf { it.isNotBlank() }
                ?: throw IOException("Empty OpenVPN profile for $ip")
        }
    }
}
