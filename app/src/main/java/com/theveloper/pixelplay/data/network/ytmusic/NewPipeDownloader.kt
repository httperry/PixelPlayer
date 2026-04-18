package com.theveloper.pixelplay.data.network.ytmusic

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as ExtractorRequest
import org.schabi.newpipe.extractor.downloader.Response as ExtractorResponse
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.util.concurrent.TimeUnit

/**
 * OkHttp-based downloader for NewPipe Extractor.
 * 
 * This implements the Downloader interface required by NewPipe
 * to make HTTP requests for extracting YouTube data.
 */
class NewPipeDownloader private constructor() : Downloader() {

    companion object {
        @Volatile
        private var instance: NewPipeDownloader? = null

        fun getInstance(): NewPipeDownloader {
            return instance ?: synchronized(this) {
                instance ?: NewPipeDownloader().also { instance = it }
            }
        }
    }

    private val client = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .addInterceptor { chain ->
            val original = chain.request()
            val request = original.newBuilder()
                .header("User-Agent", USER_AGENT)
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()
            chain.proceed(request)
        }
        .build()

    override fun execute(request: ExtractorRequest): ExtractorResponse {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val requestBuilder = Request.Builder()
            .url(url)
            .method(
                httpMethod,
                if (dataToSend != null) {
                    RequestBody.create(null, dataToSend)
                } else {
                    null
                }
            )

        // Add headers
        headers.forEach { (key, values) ->
            values.forEach { value ->
                requestBuilder.addHeader(key, value)
            }
        }

        val response: Response = try {
            client.newCall(requestBuilder.build()).execute()
        } catch (e: Exception) {
            throw ReCaptchaException("Request failed", e)
        }

        val body = response.body?.string() ?: ""
        val responseHeaders = mutableMapOf<String, MutableList<String>>()
        
        response.headers.names().forEach { name ->
            responseHeaders[name] = response.headers.values(name).toMutableList()
        }

        return ExtractorResponse(
            response.code,
            response.message,
            responseHeaders,
            body,
            url
        )
    }

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/131.0.0.0 Safari/537.36"
    }
}
