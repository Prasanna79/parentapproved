package com.kidswatch.feasibility.ui.util

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response

class NewPipeDownloader private constructor() : Downloader() {

    private val client = OkHttpClient.Builder().build()

    override fun execute(request: Request): Response {
        val builder = okhttp3.Request.Builder()
            .url(request.url())
            .method(
                request.httpMethod(),
                request.dataToSend()?.toRequestBody()
            )

        // Desktop Firefox UA to avoid mobile restrictions
        builder.header(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"
        )

        request.headers().forEach { (name, values) ->
            values.forEach { value -> builder.addHeader(name, value) }
        }

        val response = client.newCall(builder.build()).execute()
        val responseBody = response.body?.string() ?: ""
        val latestUrl = response.request.url.toString()

        val responseHeaders: Map<String, List<String>> = response.headers.toMultimap()

        return Response(
            response.code,
            response.message,
            responseHeaders,
            responseBody,
            latestUrl
        )
    }

    companion object {
        val instance: NewPipeDownloader by lazy { NewPipeDownloader() }
    }
}
