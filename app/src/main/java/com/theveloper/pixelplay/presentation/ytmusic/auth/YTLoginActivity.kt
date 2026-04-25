package com.theveloper.pixelplay.presentation.ytmusic.auth

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.google.gson.Gson
import java.io.File
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.theveloper.pixelplay.data.network.ytmusic.YTMSessionRepository
import com.theveloper.pixelplay.ui.theme.PixelPlayTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import javax.inject.Inject

private const val YTM_BASE_URL = "https://accounts.google.com/ServiceLogin?" +
    "service=youtube&uilel=3&passive=true&continue=https%3A%2F%2Fwww.youtube.com%2Fsignin" +
    "%3Faction_handle_signin%3Dtrue%26app%3Ddesktop%26next%3Dhttps%253A%252F%252Fmusic.youtube.com" +
    "%252F&hl=en&ec=65620"

private const val YTM_SUCCESS_URL_PREFIX = "https://music.youtube.com"

@AndroidEntryPoint
class YTLoginActivity : ComponentActivity() {

    @Inject lateinit var sessionRepository: YTMSessionRepository
    @Inject lateinit var webSocketClient: com.theveloper.pixelplay.data.network.ytmusic.YTMusicWebSocketClient
    
    private var headersIntercepted = false
    private var loginSucceeded = false
    private val gson = Gson()
    private var timeoutJob: kotlinx.coroutines.Job? = null
    
    companion object {
        private const val TAG = "YTLoginActivity"
        const val RESULT_AUTH_SUCCESS = 100
        const val RESULT_AUTH_FAILED = 101
        private const val TIMEOUT_MS = 30000L // 30 seconds timeout
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            PixelPlayTheme {
                var isLoading by remember { mutableStateOf(true) }
                var loginSucceeded by remember { mutableStateOf(false) }

                Box(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize()) {

                        // Top bar — matches app's CollapsibleCommonTopBar style
                        Surface(
                            shape = AbsoluteSmoothCornerShape(0.dp, 0),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp,
                                        start = 16.dp,
                                        end = 16.dp,
                                        bottom = 12.dp
                                    )
                            ) {
                                FilledIconButton(
                                    onClick = { finish() },
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                    ),
                                    modifier = Modifier.align(Alignment.CenterStart)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                        contentDescription = "Back"
                                    )
                                }

                                Text(
                                    text = "Sign in to YouTube Music",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }
                        }

                        // Wavy loading bar — M3 Expressive, same as used in SettingsCategoryScreen
                        AnimatedVisibility(
                            visible = isLoading,
                            enter = expandVertically(spring(stiffness = Spring.StiffnessMedium)) +
                                    fadeIn(spring(stiffness = Spring.StiffnessMedium))
                        ) {
                            LinearWavyProgressIndicator(
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // The WebView
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.userAgentString = settings.userAgentString
                                        .replace("; wv", "")
                                        .replace("Version/4.0 ", "")

                                    CookieManager.getInstance().setAcceptCookie(true)
                                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                                    webViewClient = object : WebViewClient() {
                                        
                                        override fun shouldInterceptRequest(
                                            view: WebView?,
                                            request: WebResourceRequest?
                                        ): WebResourceResponse? {
                                            val url = request?.url?.toString() ?: return super.shouldInterceptRequest(view, request)
                                            
                                            // Intercept the browse API call to extract full headers including HttpOnly cookies
                                            if (!headersIntercepted && url.contains("youtubei/v1/browse")) {
                                                Log.d(TAG, "🎯 Intercepting browse request: $url")
                                                
                                                val headers = request.requestHeaders
                                                
                                                // Get cookies from CookieManager (this includes HttpOnly cookies!)
                                                val cookieHeader = CookieManager.getInstance().getCookie("https://music.youtube.com")
                                                
                                                val authHeader = headers["Authorization"] ?: headers["authorization"]
                                                val authUserHeader = headers["X-Goog-AuthUser"] ?: headers["x-goog-authuser"] ?: "0"
                                                
                                                Log.d(TAG, "Cookie header present: ${cookieHeader != null}")
                                                Log.d(TAG, "Cookie header length: ${cookieHeader?.length ?: 0}")
                                                Log.d(TAG, "Authorization header present: ${authHeader != null}")
                                                
                                                if (cookieHeader != null && authHeader != null) {
                                                    headersIntercepted = true
                                                    
                                                    // Cancel timeout
                                                    timeoutJob?.cancel()
                                                    
                                                    CoroutineScope(Dispatchers.IO).launch {
                                                        try {
                                                            Log.d(TAG, "💾 Saving auth headers...")
                                                            
                                                            // Save to session repository
                                                            sessionRepository.saveAuthHeaders(
                                                                cookieString = cookieHeader,
                                                                authorization = authHeader,
                                                                authUser = authUserHeader
                                                            )
                                                            
                                                            // Create browser.json for Python backend
                                                            val browserJson = mapOf(
                                                                "Accept" to "*/*",
                                                                "Authorization" to authHeader,
                                                                "Content-Type" to "application/json",
                                                                "Cookie" to cookieHeader,
                                                                "X-Goog-AuthUser" to authUserHeader,
                                                                "x-origin" to "https://music.youtube.com"
                                                            )
                                                            
                                                            // Write to internal storage (heavily secured)
                                                            val browserJsonFile = File(filesDir, "browser.json")
                                                            browserJsonFile.writeText(gson.toJson(browserJson))
                                                            
                                                            Log.d(TAG, "✅ browser.json created at: ${browserJsonFile.absolutePath}")
                                                            Log.d(TAG, "✅ Auth headers saved to DataStore")
                                                            
                                                            // Return success and close activity IMMEDIATELY
                                                            withContext(Dispatchers.Main) {
                                                                setResult(RESULT_AUTH_SUCCESS)
                                                                finish()
                                                            }
                                                            
                                                            // Authenticate in background (don't wait for these)
                                                            launch(Dispatchers.IO) {
                                                                try {
                                                                    val hash = sessionRepository.getSapisidHash()
                                                                    if (hash != null) {
                                                                        webSocketClient.setupAuth(cookieHeader, hash)
                                                                        Log.d(TAG, "✅ WebSocket authenticated")
                                                                    }
                                                                    
                                                                    // Pass browser.json to Python backend
                                                                    com.theveloper.pixelplay.data.service.YTMusicPythonService.setupAuthentication(
                                                                        browserJsonFile.absolutePath
                                                                    )
                                                                    Log.d(TAG, "✅ Python backend authenticated")
                                                                } catch (e: Exception) {
                                                                    Log.e(TAG, "⚠️ Background auth failed (non-critical): ${e.message}")
                                                                }
                                                            }
                                                            
                                                        } catch (e: Exception) {
                                                            Log.e(TAG, "❌ Failed to save auth headers", e)
                                                            withContext(Dispatchers.Main) {
                                                                setResult(RESULT_AUTH_FAILED)
                                                                finish()
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                            
                                            return super.shouldInterceptRequest(view, request)
                                        }
                                        
                                        override fun onPageFinished(view: WebView, url: String) {
                                            isLoading = false
                                            Log.d(TAG, "📄 Page finished: $url")

                                            // Auto-navigate to Library to trigger browse API call
                                            if (url.startsWith(YTM_SUCCESS_URL_PREFIX) && !headersIntercepted && !loginSucceeded) {
                                                loginSucceeded = true
                                                Log.d(TAG, "🎵 Landed on YTM, navigating to Library...")
                                                
                                                // Start timeout timer
                                                timeoutJob?.cancel()
                                                timeoutJob = CoroutineScope(Dispatchers.Main).launch {
                                                    delay(TIMEOUT_MS)
                                                    if (!headersIntercepted) {
                                                        Log.e(TAG, "❌ Timeout: Headers not intercepted after ${TIMEOUT_MS}ms")
                                                        Toast.makeText(
                                                            this@YTLoginActivity,
                                                            "Authentication timeout. Please try again.",
                                                            Toast.LENGTH_LONG
                                                        ).show()
                                                        setResult(RESULT_AUTH_FAILED)
                                                        finish()
                                                    }
                                                }
                                                
                                                // Navigate to library page directly
                                                view.postDelayed({
                                                    Log.d(TAG, "🔄 Loading library page...")
                                                    view.loadUrl("https://music.youtube.com/library")
                                                }, 1000) // Wait 1 second for page to settle
                                            }
                                            
                                            // If we're on library page and still no headers, something went wrong
                                            if (url.contains("/library") && !headersIntercepted && loginSucceeded) {
                                                Log.w(TAG, "⚠️ On library page but no headers intercepted yet...")
                                                // Try reloading after a moment
                                                view.postDelayed({
                                                    if (!headersIntercepted) {
                                                        Log.d(TAG, "🔄 Reloading library page to trigger API call...")
                                                        view.reload()
                                                    }
                                                }, 2000)
                                            }
                                        }

                                        override fun shouldOverrideUrlLoading(
                                            view: WebView?,
                                            request: WebResourceRequest?
                                        ): Boolean {
                                            // Handle in-page redirects natively
                                            return false
                                        }

                                        override fun onPageStarted(
                                            view: WebView?,
                                            url: String?,
                                            favicon: android.graphics.Bitmap?
                                        ) {
                                            isLoading = true
                                        }
                                    }

                                    loadUrl(YTM_BASE_URL)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Success overlay
                    AnimatedVisibility(
                        visible = loginSucceeded,
                        enter = fadeIn(spring(stiffness = Spring.StiffnessLow)),
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Surface(
                            shape = AbsoluteSmoothCornerShape(24.dp, 60),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp),
                                    strokeWidth = 3.dp
                                )
                                androidx.compose.foundation.layout.Spacer(
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "Signing you in…",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        timeoutJob?.cancel()
    }
}
