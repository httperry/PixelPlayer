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
        // Use a more recent Chrome version and add more realistic headers
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/120.0.0.0 Safari/537.36"
        
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
        // Disable automatic redirect following - let NewPipe handle it
        .followRedirects(false)
        .followSslRedirects(false)
        .addInterceptor { chain ->
            val original = chain.request()
            val request = original.newBuilder()
                .header("User-Agent", USER_AGENT)
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                // DO NOT manually set Accept-Encoding - let OkHttp handle compression automatically
                // .header("Accept-Encoding", "gzip, deflate, br")  // REMOVED - OkHttp adds this and decompresses automatically
                .header("Sec-Ch-Ua", "\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"120\", \"Google Chrome\";v=\"120\"")
                .header("Sec-Ch-Ua-Mobile", "?0")
                .header("Sec-Ch-Ua-Platform", "\"Windows\"")
                .header("Sec-Fetch-Dest", "document")
                .header("Sec-Fetch-Mode", "navigate")
                .header("Sec-Fetch-Site", "none")
                .header("Sec-Fetch-User", "?1")
                .header("Upgrade-Insecure-Requests", "1")
                .removeHeader("Connection") // Let OkHttp manage this
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
            throw ReCaptchaException("Request failed: ${e.message}", url)
        }

        val body = response.body?.string() ?: ""
        val responseHeaders = mutableMapOf<String, MutableList<String>>()
        
        response.headers.names().forEach { name ->
            responseHeaders[name] = response.headers.values(name).toMutableList()
        }

        // Log response for debugging YouTube issues
        android.util.Log.d("NewPipeDownloader", "Response for $url:")
        android.util.Log.d("NewPipeDownloader", "  Status: ${response.code} ${response.message}")
        android.util.Log.d("NewPipeDownloader", "  Content-Type: ${response.header("Content-Type")}")
        android.util.Log.d("NewPipeDownloader", "  Body length: ${body.length}")
        
        // Log first 500 chars of response to see what we're getting
        if (body.isNotEmpty()) {
            val preview = body.take(500)
            android.util.Log.d("NewPipeDownloader", "  Body preview: $preview")
            
            // Check if it's HTML instead of JSON
            if (body.trimStart().startsWith("<")) {
                android.util.Log.e("NewPipeDownloader", "  ⚠️ RECEIVED HTML INSTEAD OF JSON!")
            } else if (body.trimStart().startsWith("{") || body.trimStart().startsWith("[")) {
                android.util.Log.d("NewPipeDownloader", "  ✓ Response looks like JSON")
            } else {
                android.util.Log.w("NewPipeDownloader", "  ⚠️ Response is neither HTML nor JSON")
            }
        } else {
            android.util.Log.w("NewPipeDownloader", "  ⚠️ Empty response body")
        }

        return ExtractorResponse(
            response.code,
            response.message,
            responseHeaders,
            body,
            url
        )
    }
}
