package com.zionhuang.innertube.utils

/**
 * Interface for evaluating JavaScript code dynamically.
 * Implemented by the app module using Android's WebView (Chromium V8 engine).
 */
interface JsEvaluator {
    suspend fun evaluate(js: String): String
}
