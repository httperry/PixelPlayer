package com.theveloper.pixelplay.presentation.screens

import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch

/**
 * WebView-based login screen for YouTube Music.
 * 
 * This screen shows the official YouTube Music login page in a WebView.
 * When the user logs in, we extract the cookies and store them for API calls.
 * 
 * This approach:
 * - ✅ Works with 2FA
 * - ✅ User-friendly (normal login)
 * - ✅ Gets all required cookies
 * - ✅ Works with your existing YTMusicApi
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YTMusicLoginScreen(
    onLoginSuccess: (cookies: String) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }
    var currentUrl by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Login to YouTube Music") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // WebView
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                                    "AppleWebKit/537.36 (KHTML, like Gecko) " +
                                    "Chrome/131.0.0.0 Safari/537.36"
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                                currentUrl = url ?: ""

                                // Check if user is logged in
                                if (url?.contains("music.youtube.com") == true && 
                                    !url.contains("/signin") &&
                                    !url.contains("/ServiceLogin")) {
                                    
                                    // User is logged in! Extract cookies
                                    scope.launch {
                                        val cookies = extractCookies()
                                        if (cookies.isNotEmpty()) {
                                            onLoginSuccess(cookies)
                                        }
                                    }
                                }
                            }

                            override fun onPageStarted(
                                view: WebView?,
                                url: String?,
                                favicon: android.graphics.Bitmap?
                            ) {
                                super.onPageStarted(view, url, favicon)
                                isLoading = true
                            }
                        }

                        // Load YouTube Music login page
                        loadUrl("https://music.youtube.com")
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Loading indicator
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

/**
 * Extract cookies from WebView's CookieManager.
 * Returns a cookie string ready to use in HTTP headers.
 */
private fun extractCookies(): String {
    val cookieManager = CookieManager.getInstance()
    val cookieString = cookieManager.getCookie("https://music.youtube.com") ?: ""
    
    // Parse and validate cookies
    val cookies = cookieString.split(";").map { it.trim() }
    
    // Check for required cookies
    val hasSAPISID = cookies.any { it.startsWith("SAPISID=") }
    val hasSID = cookies.any { it.startsWith("SID=") }
    
    return if (hasSAPISID && hasSID) {
        cookieString
    } else {
        ""
    }
}
