package com.jagr.fridamusic.data.remote.innertube

import android.content.Context
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.localization.Localization
import java.util.concurrent.TimeUnit

object NewPipeUtils {
    fun init(context: Context) {
        val dispatcher = Dispatcher().apply {
            // A single stream extraction fires several sub-requests (page/player/iOS
            // fallback) to a small set of Google hosts. The OkHttp default of 5 max
            // requests per host can serialize those unnecessarily when a prefetch and
            // a user-initiated playback overlap, so we raise the per-host ceiling.
            maxRequestsPerHost = 10
            maxRequests = 24
        }

        val downloader = NewPipeDownloaderImpl(
            OkHttpClient.Builder()
                .dispatcher(dispatcher)
                .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                // Caps the whole call (all redirects/retries included), so a single
                // extraction can't hang past a sane bound even if individual socket
                // reads keep resetting their own timeout.
                .callTimeout(25, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
        )
        NewPipe.init(downloader, Localization.DEFAULT)
    }
}

class NewPipeDownloaderImpl(private val client: OkHttpClient) : Downloader() {
    override fun execute(request: Request): Response {
        val method = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend() // This is ByteArray? (byte[] in Java)

        val okHttpRequestBuilder = okhttp3.Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .apply {
                headers.forEach { (key, values) ->
                    values.forEach { value -> addHeader(key, value) }
                }
            }

        if (method == "POST") {
            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
            val requestBody = if (dataToSend != null) {
                dataToSend.toRequestBody(mediaType)
            } else {
                "".toRequestBody(mediaType)
            }
            okHttpRequestBuilder.post(requestBody)
        } else {
            okHttpRequestBuilder.method(method, null)
        }

        val okHttpResponse = client.newCall(okHttpRequestBuilder.build()).execute()
        val responseCode = okHttpResponse.code
        val responseMessage = okHttpResponse.message
        val responseHeaders = okHttpResponse.headers.toMultimap()
        val responseBody = okHttpResponse.body?.string() ?: ""
        val responseUrl = okHttpResponse.request.url.toString()

        return Response(responseCode, responseMessage, responseHeaders, responseBody, responseUrl)
    }
}