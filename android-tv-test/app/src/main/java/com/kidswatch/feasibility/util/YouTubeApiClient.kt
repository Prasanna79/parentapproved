package com.kidswatch.feasibility.util

import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class YouTubeApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    data class ApiResult(
        val success: Boolean,
        val data: String? = null,
        val error: String? = null,
        val playlistNames: List<String> = emptyList(),
    )

    fun fetchMyPlaylists(accessToken: String): ApiResult {
        val request = Request.Builder()
            .url("https://www.googleapis.com/youtube/v3/playlists?part=snippet&mine=true&maxResults=10")
            .addHeader("Authorization", "Bearer $accessToken")
            .build()

        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val json = gson.fromJson(body, JsonObject::class.java)
                val items = json.getAsJsonArray("items") ?: return ApiResult(
                    success = true,
                    data = body,
                    playlistNames = emptyList(),
                )
                val names = items.map { item ->
                    item.asJsonObject
                        .getAsJsonObject("snippet")
                        ?.get("title")?.asString ?: "(untitled)"
                }
                ApiResult(success = true, data = body, playlistNames = names)
            } else {
                ApiResult(success = false, error = "HTTP ${response.code}: $body")
            }
        } catch (e: IOException) {
            ApiResult(success = false, error = "Network error: ${e.message}")
        }
    }

    fun validateToken(accessToken: String): ApiResult {
        val request = Request.Builder()
            .url("https://www.googleapis.com/oauth2/v3/tokeninfo?access_token=$accessToken")
            .build()

        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""

            if (response.isSuccessful) {
                ApiResult(success = true, data = body)
            } else {
                ApiResult(success = false, error = "Token invalid: HTTP ${response.code}")
            }
        } catch (e: IOException) {
            ApiResult(success = false, error = "Network error: ${e.message}")
        }
    }
}
