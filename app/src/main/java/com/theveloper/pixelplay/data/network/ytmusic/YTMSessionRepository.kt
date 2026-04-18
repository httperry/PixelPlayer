package com.theveloper.pixelplay.data.network.ytmusic

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "YTMSessionRepository"
private val Context.ytmDataStore by preferencesDataStore(name = "ytm_session")

private val KEY_COOKIE_STRING = stringPreferencesKey("ytm_cookie_string")
private val KEY_SAPISID = stringPreferencesKey("ytm_sapisid")
private val KEY_LOGIN_EMAIL = stringPreferencesKey("ytm_login_email")

/**
 * Persists the YouTube Music session cookies extracted from the WebView login.
 *
 * The stored cookie string is the full raw `Cookie` header value (all cookies
 * joined with "; "), identical to what a browser sends.
 *
 * SAPISIDHASH computation:
 *   SHA1(timestamp + " " + SAPISID + " " + "https://music.youtube.com")
 *   prefixed with: "<timestamp>_"
 */
@Singleton
class YTMSessionRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : YTMCookieProvider {

    // -------------------------------------------------------------------------
    // YTMCookieProvider implementation
    // -------------------------------------------------------------------------

    override fun getCookies(): String? {
        // Synchronous read using runBlocking is intentional here — OkHttp interceptors
        // run on background threads so it's safe, and we need a non-suspend return.
        return kotlinx.coroutines.runBlocking {
            context.ytmDataStore.data
                .map { it[KEY_COOKIE_STRING] }
                .firstOrNull()
        }
    }

    override fun getSapisidHash(): String? {
        val sapisid = kotlinx.coroutines.runBlocking {
            context.ytmDataStore.data.map { it[KEY_SAPISID] }.firstOrNull()
        } ?: return null

        return computeSapisidHash(sapisid)
    }

    // -------------------------------------------------------------------------
    // Session management — called from YTLoginActivity
    // -------------------------------------------------------------------------

    /**
     * Persists the cookies extracted from the WebView after a successful login.
     * Also parses and stores the SAPISID cookie separately for hash computation.
     *
     * @param rawCookieString The full `Cookie` header string e.g.
     *   "SAPISID=abc123; __Secure-3PAPISID=xyz; SID=…"
     * @param email Optional user email for display in the AccountsScreen card.
     */
    suspend fun saveCookies(rawCookieString: String, email: String? = null) {
        val sapisid = extractSapisid(rawCookieString)
        if (sapisid == null) {
            Log.w(TAG, "SAPISID not found in cookie string — session may not be authenticated")
        }

        context.ytmDataStore.edit { prefs ->
            prefs[KEY_COOKIE_STRING] = rawCookieString
            if (sapisid != null) prefs[KEY_SAPISID] = sapisid
            if (email != null) prefs[KEY_LOGIN_EMAIL] = email
        }
        Log.d(TAG, "YTM session saved (email=$email, hasSapisid=${sapisid != null})")
    }

    /** Clears all stored session data — equivalent of logging out. */
    suspend fun clearSession() {
        context.ytmDataStore.edit { it.clear() }
        Log.d(TAG, "YTM session cleared")
    }

    /**
     * Returns true if the user has a stored session (cookies present).
     * Does NOT verify whether the session is still valid server-side.
     */
    suspend fun isLoggedIn(): Boolean {
        return context.ytmDataStore.data.map {
            !it[KEY_COOKIE_STRING].isNullOrBlank()
        }.firstOrNull() ?: false
    }

    /** Returns the stored login email for display in UI, or null if unavailable. */
    suspend fun getLoginEmail(): String? {
        return context.ytmDataStore.data.map { it[KEY_LOGIN_EMAIL] }.firstOrNull()
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun extractSapisid(cookieString: String): String? {
        // Support both SAPISID and __Secure-3PAPISID (newer accounts may only have the latter)
        val regex = Regex("(?:SAPISID|__Secure-3PAPISID)=([^;]+)")
        return regex.find(cookieString)?.groupValues?.get(1)?.trim()
    }

    private fun computeSapisidHash(sapisid: String): String {
        val timestamp = System.currentTimeMillis() / 1000
        val toHash = "$timestamp $sapisid https://music.youtube.com"
        val digest = MessageDigest.getInstance("SHA-1").digest(toHash.toByteArray())
        val hex = digest.joinToString("") { "%02x".format(it) }
        return "${timestamp}_$hex"
    }
}
