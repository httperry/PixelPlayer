package com.theveloper.pixelplay.data.network.ytmusic

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.sync.withLock

@Singleton
class YouTubePoTokenGenerator @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val TAG = "YouTubePoTokenGenerator"
    private var webView: WebView? = null
    private var currentContinuation: kotlinx.coroutines.CancellableContinuation<String?>? = null
    private val mutex = kotlinx.coroutines.sync.Mutex()

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    suspend fun generatePoToken(visitorData: String): String? = mutex.withLock {
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                currentContinuation = continuation
                
                continuation.invokeOnCancellation {
                    if (currentContinuation == continuation) {
                        currentContinuation = null
                    }
                }
            try {
                if (webView == null) {
                    webView = WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.cacheMode = WebSettings.LOAD_NO_CACHE
                        addJavascriptInterface(object : Any() {
                            @JavascriptInterface
                            fun onTokenGenerated(token: String?) {
                                currentContinuation?.takeIf { it.isActive }?.resume(token)
                                currentContinuation = null
                            }
                            @JavascriptInterface
                            fun onError(error: String?) {
                                Log.e(TAG, "JS Error: $error")
                                currentContinuation?.takeIf { it.isActive }?.resume(null)
                                currentContinuation = null
                            }
                        }, "AndroidInterface")
                    }
                }

                val jsFile = context.assets.open("app/browser.js").bufferedReader().use { it.readText() }
                
                val html = """
                    <html>
                    <head>
                        <script>
                            $jsFile
                            
                            async function generateToken() {
                                try {
                                    const visitorData = "$visitorData";
                                    const requestKey = 'O43z0dpjhgX20SCx4KAo';
                                    
                                    const bgConfig = {
                                      fetch: (input, init) => fetch(input, init),
                                      globalObj: window,
                                      identifier: visitorData,
                                      requestKey: requestKey
                                    };
                                    
                                    const bgChallenge = await BGUtils.BG.Challenge.create(bgConfig);
                                    if (!bgChallenge) {
                                        window.AndroidInterface.onError("Could not get challenge");
                                        return;
                                    }
                                    
                                    const interpreterJavascript = bgChallenge.interpreterJavascript.privateDoNotAccessOrElseSafeScriptWrappedValue;
                                    if (interpreterJavascript) {
                                      new Function(interpreterJavascript)();
                                    } else {
                                      window.AndroidInterface.onError("Could not load VM");
                                      return;
                                    }
                                    
                                    const poTokenResult = await BGUtils.BG.PoToken.generate({
                                      program: bgChallenge.program,
                                      globalName: bgChallenge.globalName,
                                      bgConfig: bgConfig
                                    });
                                    
                                    window.AndroidInterface.onTokenGenerated(poTokenResult.poToken);
                                } catch(e) {
                                    window.AndroidInterface.onError(e.message || e.toString());
                                }
                            }
                        </script>
                    </head>
                    <body>
                        <script>
                            generateToken();
                        </script>
                    </body>
                    </html>
                """.trimIndent()

                webView?.loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "UTF-8", null)
                
            } catch (e: Exception) {
                Log.e(TAG, "WebView error", e)
                currentContinuation?.takeIf { it.isActive }?.resumeWithException(e)
                currentContinuation = null
            }
        }
    }
    }
}
