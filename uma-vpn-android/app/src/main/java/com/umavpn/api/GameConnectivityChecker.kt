package com.umavpn.api

import android.util.Log
import com.umavpn.model.ConnectivityCheckStyle
import com.umavpn.model.GameVersion
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.net.UnknownHostException
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
 *
 * If the probe host itself has disappeared from DNS (as `api-umamusume.cygames.jp` did
 * in 2026) while DNS otherwise works through the tunnel, the result is [Result.Unverifiable]
 * rather than a failure: the servers from api.umavpn.top are already pre-verified for the
 * selected game version, so the tunnel is kept and reported as "not verified".
 */
class GameConnectivityChecker {

    companion object {
        private const val TAG = "GameConnectivityChecker"
        // Mimic the Android game client's user-agent so Cygames applies the same geo rules
        private const val DALVIK_UA =
            "Dalvik/2.1.0 (Linux; U; Android 9; ALP-AL00 Build/HUAWEIALP-AL00)"

        /** Always-resolvable host used to tell "probe host gone" from "DNS broken". */
        private const val DNS_CONTROL_HOST = "api.umavpn.top"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    sealed class Result {
        /** Probe response says the IP is allowed by Cygames */
        object Accessible : Result()
        /** HTTP 403 — IP is geo-blocked by Cygames */
        object Blocked : Result()
        /** The probe host no longer exists in DNS, although DNS works — the check cannot run. */
        data class Unverifiable(val reason: String) : Result()
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
        } catch (e: UnknownHostException) {
            val host = testUrl.toHttpUrlOrNull()?.host ?: testUrl
            if (dnsWorks()) {
                Log.w(TAG, "Probe host $host does not resolve although DNS works — cannot verify game access")
                Result.Unverifiable("$host no longer exists in DNS")
            } else {
                Log.w(TAG, "DNS is not working through the VPN: ${e.message}")
                Result.Inconclusive("DNS not working through the VPN")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Connectivity check failed: ${e.message}")
            Result.Inconclusive(e.message ?: "Unknown error")
        }
    }

    private fun dnsWorks(): Boolean =
        runCatching { InetAddress.getAllByName(DNS_CONTROL_HOST).isNotEmpty() }.getOrDefault(false)

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
