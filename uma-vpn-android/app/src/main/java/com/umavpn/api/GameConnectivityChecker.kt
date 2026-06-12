package com.umavpn.api

import android.util.Log
import com.umavpn.model.ConnectivityCheckStyle
import com.umavpn.model.GameVersion
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Verifies whether the game is reachable through the active VPN.
 *
 * Per the [umavpn.top player guide](https://www.umavpn.top):
 * - **Japanese** — `https://api-umamusume.cygames.jp/`: 404 = allowed, 403 = blocked
 * - **Global** — `https://umamusume.com/`: 2xx = allowed, 403 = blocked
 *
 * OkHttp uses the system network stack, so traffic goes through the VPN tunnel
 * once OpenVPN is connected.
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

    fun check(version: GameVersion): Result = check(version.connectivityTestUrl, version.connectivityCheckStyle)

    fun check(testUrl: String, style: ConnectivityCheckStyle): Result {
        return try {
            val request = Request.Builder()
                .url(testUrl)
                .header("User-Agent", DALVIK_UA)
                .build()

            client.newCall(request).execute().use { response ->
                Log.d(TAG, "Game connectivity test ($testUrl): HTTP ${response.code}")
                interpret(response.code, style)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Connectivity check failed: ${e.message}")
            Result.Inconclusive(e.message ?: "Unknown error")
        }
    }

    private fun interpret(httpCode: Int, style: ConnectivityCheckStyle): Result =
        when (style) {
            ConnectivityCheckStyle.CYGAMES_API -> when (httpCode) {
                404 -> Result.Accessible
                403 -> Result.Blocked
                else -> Result.Inconclusive("Unexpected HTTP $httpCode")
            }
            ConnectivityCheckStyle.GLOBAL_WEBSITE -> when (httpCode) {
                403 -> Result.Blocked
                in 200..299 -> Result.Accessible
                else -> Result.Inconclusive("Unexpected HTTP $httpCode")
            }
        }
}
