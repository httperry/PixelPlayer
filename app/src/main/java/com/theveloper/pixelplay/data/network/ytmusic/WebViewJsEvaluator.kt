package com.theveloper.pixelplay.data.network.ytmusic

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import com.zionhuang.innertube.utils.JsEvaluator
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Implementation of JsEvaluator that uses Android's built-in WebView (Chromium V8 engine).
 * This runs the Javascript in a full browser environment, avoiding the need for
 * heavy 3rd-party JS engines or JSDOM polyfills.
 */
class WebViewJsEvaluator(private val context: Context) : JsEvaluator {

    // Keep a cached instance of WebView to avoid recreation overhead per evaluation
    private var webView: WebView? = null
    private val handler = Handler(Looper.getMainLooper())

    @SuppressLint("SetJavaScriptEnabled")
    override suspend fun evaluate(js: String): String = suspendCancellableCoroutine { continuation ->
        handler.post {
            try {
                if (webView == null) {
                    webView = WebView(context).apply {
                        settings.javaScriptEnabled = true
                    }
                }
                
                webView?.evaluateJavascript(js) { result ->
                    if (continuation.isActive) {
                        continuation.resume(result ?: "")
                    }
                }
            } catch (e: Exception) {
                if (continuation.isActive) {
                    continuation.resume("") // Return empty on error
                }
            }
        }
    }
}
