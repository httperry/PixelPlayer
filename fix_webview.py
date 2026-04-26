import re

with open("app/src/main/java/com/theveloper/pixelplay/presentation/ytmusic/auth/YTLoginActivity.kt", "r") as f:
    text = f.read()

old_override = """                                        override fun shouldOverrideUrlLoading(
                                            view: WebView?,
                                            request: WebResourceRequest?
                                        ): Boolean {
                                            // Handle in-page redirects natively
                                            return false
                                        }"""

new_override = """                                        override fun shouldOverrideUrlLoading(
                                            view: WebView?,
                                            request: WebResourceRequest?
                                        ): Boolean {
                                            val url = request?.url?.toString() ?: return false
                                            if (url.startsWith("intent://") || url.startsWith("fido:")) {
                                                try {
                                                    val intent = android.content.Intent.parseUri(url, android.content.Intent.URI_INTENT_SCHEME)
                                                    if (intent != null) {
                                                        view?.context?.startActivity(intent)
                                                        return true
                                                    }
                                                } catch (e: Exception) {
                                                    Log.e(TAG, "Failed to launch passkey/intent URL: ${e.message}")
                                                }
                                                return false
                                            }
                                            return false
                                        }"""

text = text.replace(old_override, new_override)

with open("app/src/main/java/com/theveloper/pixelplay/presentation/ytmusic/auth/YTLoginActivity.kt", "w") as f:
    f.write(text)
