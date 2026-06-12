package com.umavpn.api

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Verifies whether the Cygames game server is reachable through the active VPN.
 *
 * Test URLs are version-specific (see [com.umavpn.model.GameVersion.connectivityTestUrl]):
 *   Global   → `https://api.games.umamusume.jp/`
 *   Japanese → `https://api-umamusume.cygames.jp/`
 *
 * Cygames returns:
 *   HTTP 404  →  IP is **allowed** — the game server is accessible
 *   HTTP 403  →  IP is **geo-blocked** — the game will not connect
 *   Timeout   →  Unable to determine (treat as inconclusive)
 *
 * Because OkHttp uses the system network stack, traffic automatically
 * goes through the VPN tunnel once OpenVPN is connected.
 */
class GameConnectivityChecker {

    companion object {
        private const val TAG = "GameConnectivityChecker"
        // Mimic the Android game client's user-agent so Cygames applies the same geo rules
        private const val DALVIK_UA =
            "Dalvik/2.1.0 (Linux; U; Android 9; ALP-AL00 Build/HUAWEIALP-AL00)"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    sealed class Result {
        /** HTTP 404 — IP is allowed by Cygames */
        object Accessible : Result()
        /** HTTP 403 — IP is geo-blocked by Cygames */
        object Blocked : Result()
        /** Network error or unexpected response */
        data class Inconclusive(val reason: String) : Result()
    }

    fun check(testUrl: String): Result {
        return try {
            val request = Request.Builder()
                .url(testUrl)
                .header("User-Agent", DALVIK_UA)
                .build()

            client.newCall(request).execute().use { response ->
                Log.d(TAG, "Cygames connectivity test: HTTP ${response.code}")
                when (response.code) {
                    404 -> Result.Accessible
                    403 -> Result.Blocked
                    else -> Result.Inconclusive("Unexpected HTTP ${response.code}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Connectivity check failed: ${e.message}")
            Result.Inconclusive(e.message ?: "Unknown error")
        }
    }
}
