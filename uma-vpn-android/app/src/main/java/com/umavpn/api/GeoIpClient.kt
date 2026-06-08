package com.umavpn.api

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Resolves IP addresses to their ISO 3166-1 alpha-2 country codes using ip-api.com.
 * Results are cached for the process lifetime so repeated connect attempts don't
 * re-query the same IPs.
 *
 * ip-api.com's free batch endpoint is HTTP-only; that domain is explicitly allowed
 * in network_security_config.xml.
 */
class GeoIpClient {

    companion object {
        private const val TAG = "GeoIpClient"
        private const val BATCH_URL = "http://ip-api.com/batch?fields=query,countryCode,status"
        private const val UNKNOWN = "??"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val cache = ConcurrentHashMap<String, String>()

    /**
     * Returns a map of IP → ISO country code ("JP", "US", "TW", etc.) for all given IPs.
     * IPs that cannot be resolved are mapped to [UNKNOWN].
     * Already-cached IPs skip the network call.
     */
    fun lookupCountries(ips: List<String>): Map<String, String> {
        val needed = ips.filter { !cache.containsKey(it) }
        if (needed.isNotEmpty()) {
            try {
                fetchBatch(needed)
            } catch (e: Exception) {
                Log.w(TAG, "GeoIP batch lookup failed: ${e.message}")
                needed.forEach { cache.putIfAbsent(it, UNKNOWN) }
            }
        }
        return ips.associateWith { cache.getOrDefault(it, UNKNOWN) }
    }

    private fun fetchBatch(ips: List<String>) {
        val bodyArray = JsonArray()
        ips.forEach { ip ->
            JsonObject().also { obj ->
                obj.addProperty("query", ip)
                bodyArray.add(obj)
            }
        }

        val requestBody = gson.toJson(bodyArray)
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(BATCH_URL)
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w(TAG, "GeoIP API returned HTTP ${response.code}")
                return
            }
            val json = gson.fromJson(response.body?.string() ?: "[]", JsonArray::class.java)
            json.forEach { element ->
                val obj = element.asJsonObject
                val ip = obj.get("query")?.asString ?: return@forEach
                val status = obj.get("status")?.asString
                val country = if (status == "success") {
                    obj.get("countryCode")?.asString ?: UNKNOWN
                } else {
                    UNKNOWN
                }
                cache[ip] = country
            }
        }
    }
}
