package com.kidswatch.feasibility.util

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Experimental: Attempt to convert an OAuth token into web cookies
 * via Google's undocumented MergeSession/OAuthLogin endpoints.
 * This is a bonus experiment — expected to fail on most device configurations.
 */
class CookieConverter {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    data class ConversionResult(
        val success: Boolean,
        val cookies: Map<String, String> = emptyMap(),
        val error: String? = null,
        val rawResponse: String? = null,
    )

    /**
     * Step 1: Exchange OAuth token for an uber-token via OAuthLogin.
     * Endpoint: https://accounts.google.com/OAuthLogin?source=ChromiumBrowser&issueuberauth=1
     * Header: Authorization: OAuth <token>
     */
    fun getUberToken(oauthToken: String): ConversionResult {
        val request = Request.Builder()
            .url("https://accounts.google.com/OAuthLogin?source=ChromiumBrowser&issueuberauth=1")
            .addHeader("Authorization", "OAuth $oauthToken")
            .build()

        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""

            if (response.isSuccessful && body.isNotBlank() && !body.contains("Error")) {
                ConversionResult(success = true, rawResponse = body.trim())
            } else {
                ConversionResult(
                    success = false,
                    error = "OAuthLogin failed: HTTP ${response.code}, body=$body",
                )
            }
        } catch (e: IOException) {
            ConversionResult(success = false, error = "Network error: ${e.message}")
        }
    }

    /**
     * Step 2: Exchange uber-token for web cookies via MergeSession.
     * Endpoint: https://accounts.google.com/MergeSession?uberauth=<ubertoken>&continue=https://www.youtube.com
     */
    fun mergeSession(uberToken: String): ConversionResult {
        val request = Request.Builder()
            .url("https://accounts.google.com/MergeSession?uberauth=$uberToken&continue=https://www.youtube.com")
            .build()

        return try {
            val response = client.newCall(request).execute()
            val setCookies = response.headers("Set-Cookie")
            val cookieMap = setCookies.associate { cookie ->
                val parts = cookie.split(";")[0].split("=", limit = 2)
                (parts.getOrElse(0) { "" }) to (parts.getOrElse(1) { "" })
            }

            if (cookieMap.isNotEmpty()) {
                ConversionResult(success = true, cookies = cookieMap)
            } else {
                ConversionResult(
                    success = false,
                    error = "No cookies returned. HTTP ${response.code}",
                    rawResponse = response.headers.toString(),
                )
            }
        } catch (e: IOException) {
            ConversionResult(success = false, error = "Network error: ${e.message}")
        }
    }
}
