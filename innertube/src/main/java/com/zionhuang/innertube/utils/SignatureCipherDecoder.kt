package com.zionhuang.innertube.utils

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.logging.Logger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Decodes YouTube's `signatureCipher` (and optionally the `n`-parameter / nsig) so that
 * adaptive formats returned by browser-style InnerTube clients (WEB_REMIX, WEB) produce
 * playable URLs.
 *
 * YouTube browser clients do NOT return direct `url` fields for adaptive formats; instead
 * they return a `signatureCipher` string of the form:
 *   s=<scrambled_sig>&sp=sig&url=<base_url_encoded>
 *
 * Decoding requires:
 *  1. Downloading the YouTube player JS (cached per player version).
 *  2. Locating the decipher function and its helper object.
 *  3. Replaying the operation sequence (reverse / splice / swap) in pure Kotlin.
 *
 * References: yt-dlp, NewPipeExtractor, pytube.
 */
@Suppress("RegExpRedundantEscape")
object SignatureCipherDecoder {

    private val log = Logger.getLogger("SignatureCipherDecoder")

    // ── Types ─────────────────────────────────────────────────────────────────

    sealed class CipherOp {
        data class Swap(val pos: Int) : CipherOp()
        data class Splice(val count: Int) : CipherOp()
        object Reverse : CipherOp()
    }

    data class DecipherCall(val funcName: String, val arg1: String? = null, val arg2: String? = null)

    private data class CipherCache(
        val playerUrl: String,
        val decipherCall: DecipherCall?,
        val nsigFuncBody: String?,
    )

    // ── State ─────────────────────────────────────────────────────────────────

    @Volatile private var cache: CipherCache? = null
    @Volatile private var cacheFuncName: String? = null
    @Volatile private var cacheDecipherCall: DecipherCall? = null
    /**
     * True when the player JS uses the new URL-level nsig wrapper (oL5-style).
     * In this mode decodeNsig() passes the whole URL to window.nsigWrapper()
     * instead of extracting and replacing just the ?n= query parameter.
     */
    @Volatile private var useUrlLevelNsigWrapper: Boolean = false

    /**
     * Per-session in-memory cache of decoded n-parameter values.
     * Key = raw (throttled) n value, Value = decoded (unthrottled) n value.
     * Avoids redundant WebView JS evaluations for the same n token across streams.
     */
    private val nsigCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    /**
     * The player URL whose JS we already attempted but failed to parse.
     * Prevents hammering YouTube's servers when a player version can't be decoded.
     * Cleared when a new (different) player version is detected.
     */
    @Volatile private var lastFailedPlayerUrl: String? = null
    private val initMutex = Mutex()

    // ── Regex constants ───────────────────────────────────────────────────────

    // NEW Obfuscation pattern: T=vi(8,8658,va(33,1211,f.s))
    // Matches the function name and the two numeric XOR constants.
    private val RE_DECIPHER_NEW_CALL = Regex("([a-zA-Z\$_][a-zA-Z0-9\$_]*)\\((\\d+),(\\d+),va\\(")
    
    // Primary (Old): var Abc=function(a){a=a.split("")
    private val RE_DECIPHER_FUNC_VAR = Regex(
        "var\\s+([a-zA-Z0-9\$_]{1,})\\s*=\\s*function\\s*\\(a\\)\\s*\\{\\s*a=a\\.split\\([\"'][\"']\\)"
    )
    // Alternate (Old): SomeVar=function(a){a=a.split("")
    private val RE_DECIPHER_FUNC_ASSIGN = Regex(
        "([a-zA-Z0-9\$_]{1,})\\s*=\\s*function\\s*\\(a\\)\\s*\\{\\s*a=a\\.split\\([\"'][\"']\\)"
    )
    // Alternate (Old): function SomeName(a){a=a.split("")
    private val RE_DECIPHER_FUNC_DEF = Regex(
        "function\\s+([a-zA-Z0-9\$_]{1,})\\s*\\(a\\)\\s*\\{\\s*a=a\\.split\\([\"'][\"']\\)"
    )

    // Matches ;HelperObj. or ,HelperObj. at the start of a call in the function body
    private val RE_HELPER_NAME = Regex("[;,]([a-zA-Z0-9\$_]{1,})\\.")

    // Matches helper method definitions: name:function(a,b){...}
    private val RE_HELPER_METHOD = Regex(
        "([a-zA-Z0-9\$_]{1,})\\s*:\\s*function\\s*\\([^)]*\\)\\s*\\{([^}]*)\\}"
    )

    // Player version hash in iframe_api.
    // The iframe_api response escapes slashes: \/s\/player\/HASH\/www-widgetapi.vflset
    // Match both literal and backslash-escaped slash variants.
    private val RE_PLAYER_VERSION = Regex("(?:[/\\\\]+)player(?:[/\\\\]+)([a-zA-Z0-9_-]+)(?:[/\\\\]+)")

    // nsig function location in player JS — OLD style: .get("n"))&&(b=funcName[idx](arg))
    private val RE_NSIG_FUNC = Regex(
        "\\.get\\(\"n\"\\)\\)&&\\(b=([a-zA-Z0-9\$_]+)(?:\\[(\\d+)])?\\([a-zA-Z0-9\$_]+\\)"
    )

    // NEW style: URL-level nsig wrapper like L8G=function(T){...(new g.Ad(T,!0)).get("n")...}
    // Matches the wrapper function name, its argument, and the inner class instantiation.
    private val RE_NSIG_URL_WRAPPER = Regex(
        "([a-zA-Z0-9\$_]{2,6})=function\\(([a-zA-Z])\\)\\{try\\{var [a-zA-Z]=\\((new [a-zA-Z0-9\$_]+\\.[a-zA-Z0-9\$_]+\\(\\2,!0\\))\\)\\.get\\(\"n\""
    )

    // Quoted function names in an array literal
    private val RE_QUOTED_NAME = Regex("\"([a-zA-Z0-9\$_]+)\"")

    // nsig splice call
    private val RE_NSIG_SPLICE = Regex("\\.splice\\(0\\s*,\\s*(\\d+)\\)")

    // ── Public API ────────────────────────────────────────────────────────────

    @Volatile private var jsEvaluator: JsEvaluator? = null

    /**
     * Ensures the cipher cache is warm. Call this before [decipher] / [decodeNsig].
     *
     * Key behaviour:
     *  - Returns immediately when a valid cache already exists for the current player URL.
     *  - Modern players (>=bcd88ad3) no longer use signatureCipher; [findDecipherCall] may
     *    return null. This is treated as a non-fatal "no-cipher" mode — we still inject
     *    [window.nsigWrapper] and mark the cache as initialized.
     *  - If the player JS cannot be fetched/parsed at all, we record [lastFailedPlayerUrl]
     *    and bail without setting cache, so the next call with a newer player URL retries.
     */
    suspend fun initialize(httpClient: HttpClient, evaluator: JsEvaluator) {
        this.jsEvaluator = evaluator
        // Fast path: cache already warmed for the current session.
        if (cache != null) return
        initMutex.withLock {
            if (cache != null) return
            try {
                val playerUrl = resolvePlayerUrl(httpClient) ?: run {
                    log.warning("Could not resolve player JS URL")
                    return
                }
                // If we already have a good cache for this exact player URL, skip.
                if (playerUrl == cache?.playerUrl) return
                // If we already tried this player URL and it failed, don't hammer it again.
                // A different player URL (new YouTube release) will pass through.
                if (playerUrl == lastFailedPlayerUrl) {
                    log.warning("Skipping re-fetch of previously-failed player JS: $playerUrl")
                    return
                }

                log.info("Fetching player JS: $playerUrl")
                val js = httpClient.get(playerUrl).bodyAsText()
                val decipherCall = findDecipherCall(js)

                if (decipherCall == null) {
                    // Modern players (>= bcd88ad3) dropped the split("") cipher entirely.
                    // This is NOT a fatal error — ANDROID_MUSIC/IOS clients return direct URLs.
                    // We still inject window.nsigWrapper so URL-level throttle removal works.
                    log.info("No signatureCipher decipher function found — modern no-cipher player. " +
                        "Proceeding with nsig-only mode.")
                }

                val nsigBody = extractNsigFuncBody(js)

                // ── nsig function detection ───────────────────────────────────────────────
                // Strategy 1 (new, ≥ player bcd88ad3): YouTube now uses a URL-level wrapper
                //   function (like L8G) that takes the full URL and transforms the /n/ path
                //   segment using an internal class. We expose it as window.nsigWrapper,
                //   and expose the inner logic as window.getNsig.
                val urlWrapperMatch = RE_NSIG_URL_WRAPPER.find(js)
                val urlWrapperName = urlWrapperMatch?.groupValues?.get(1)
                val urlWrapperArg = urlWrapperMatch?.groupValues?.get(2)
                val urlWrapperInnerClass = urlWrapperMatch?.groupValues?.get(3)

                // Strategy 2 (legacy): a dedicated nsig function that takes just the n value.
                val match = RE_NSIG_FUNC.find(js)
                val funcName = match?.groupValues?.get(1)
                val idxStr = match?.groupValues?.get(2)
                val resolvedName = if (idxStr != null && idxStr.isNotEmpty()) {
                    val arrayBody = extractBracedBody(js, "var $funcName=[")
                    val names = arrayBody?.let { RE_QUOTED_NAME.findAll(it).map { m -> m.groupValues[1] }.toList() }
                    names?.getOrNull(idxStr.toIntOrNull() ?: 0)
                } else funcName

                // Expose base.js functions to the WebView JavaScript environment for dynamic evaluation.
                // We inject `window.decipherFunc=`, `window.nsigWrapper=` and/or `window.nsigFunc=`
                // directly into their definitions to guarantee they are exported globally,
                // bypassing the IIFE and any local scoping issues.
                var modifiedJs = if (decipherCall != null) {
                    js.replace(
                        Regex("""([;{,\n\s])(${Regex.escape(decipherCall.funcName)}=function\()"""),
                        "$1window.decipherFunc=$2"
                    )
                } else js
                if (urlWrapperName != null) {
                    // New-style: patch the URL-level wrapper (e.g. L8G) → window.nsigWrapper
                    modifiedJs = modifiedJs.replace(
                        Regex("""([;{,\n\s])(${Regex.escape(urlWrapperName)}=function\()"""),
                        "$1window.nsigWrapper=$2"
                    )
                    log.info("nsig URL-wrapper detected: $urlWrapperName → window.nsigWrapper")
                }
                if (resolvedName != null) {
                    // Legacy-style: also patch the per-value nsig func → window.nsigFunc
                    modifiedJs = modifiedJs.replace(
                        Regex("""([;{,\n\s])(${Regex.escape(resolvedName)}=function\()"""),
                        "$1window.nsigFunc=$2"
                    )
                }
                evaluator.evaluate(modifiedJs)

                // Commit to cache. decipherCall may be null in modern no-cipher players.
                cache = CipherCache(playerUrl, decipherCall, nsigBody)
                cacheFuncName = resolvedName
                cacheDecipherCall = decipherCall
                useUrlLevelNsigWrapper = urlWrapperName != null
                lastFailedPlayerUrl = null // clear any previous failure record

                val cipherMode = if (decipherCall != null) "decipher=${decipherCall.funcName}" else "no-cipher"
                log.info("Cipher ready: $cipherMode, nsigWrapper=${urlWrapperName ?: "none"}, nsigFunc=${resolvedName ?: "none"}")
            } catch (e: Exception) {
                log.warning("SignatureCipherDecoder.initialize failed: ${e.message}")
            }
        }
    }

    /**
     * Resets the cipher cache, forcing a full re-initialization on the next [initialize] call.
     * Call this when YouTube changes its obfuscation (e.g. onObfuscationFailure callback).
     */
    fun resetCache() {
        cache = null
        cacheFuncName = null
        cacheDecipherCall = null
        useUrlLevelNsigWrapper = false
        lastFailedPlayerUrl = null
        nsigCache.clear()
        log.info("SignatureCipherDecoder: cache reset — will re-initialize on next player() call")
    }

    /**
     * Decodes a `signatureCipher` string into a playable URL.
     * Returns `null` if decoding cannot be performed (cache not warm / malformed input).
     */
    suspend fun decipher(signatureCipher: String): String? {
        val params = parseQuery(signatureCipher)
        val encodedSig = params["s"] ?: return null
        val encodedUrl = params["url"] ?: return null
        val sp = params["sp"] ?: "signature"

        val sig = URLDecoder.decode(encodedSig, "UTF-8")
        val baseUrl = URLDecoder.decode(encodedUrl, "UTF-8")

        val decipherCall = cacheDecipherCall
        if (decipherCall == null) {
            log.warning("decipher() called but decipher function parsing failed previously.")
            return null // Return null to trigger fallback
        }

        val decodedSig = runCatching {
            // Evaluate dynamically in WebView using the globally exposed function.
            val jsCode = if (decipherCall.arg1 != null && decipherCall.arg2 != null) {
                "window.decipherFunc(${decipherCall.arg1}, ${decipherCall.arg2}, '$sig')"
            } else {
                "window.decipherFunc('$sig')"
            }
            val result = jsEvaluator?.evaluate(jsCode) ?: ""
            // Remove surround quotes if present (WebView string result is JSON-serialized)
            if (result.startsWith("\"") && result.endsWith("\"")) {
                result.drop(1).dropLast(1)
            } else result
        }.getOrNull() ?: sig

        return "$baseUrl&$sp=$decodedSig"
    }

    /**
     * Decodes the `n` parameter in a YouTube stream URL to remove throttling.
     *
     * YouTube's approach changed across player versions:
     *  - **New style (≥ bcd88ad3):** The n token lives in the URL *path* as `/n/<value>` and
     *    is transformed by a URL-level wrapper function (e.g. `L8G`). We call it via
     *    `window.nsigWrapper(fullUrl)` which returns the fully-rewritten URL.
     *  - **Legacy style:** The n token is a `?n=` query parameter transformed by a dedicated
     *    function. We extract, decode, and re-substitute it.
     *
     * Returns the original URL unchanged if decoding fails or is not needed.
     */
    suspend fun decodeNsig(url: String): String {
        val evaluator = jsEvaluator ?: run {
            log.warning("decodeNsig() skipped: no JsEvaluator set")
            return url
        }

        // ── Strategy 1: new URL-level wrapper (oL5 / path-based /n/<value>) ────────
        if (useUrlLevelNsigWrapper) {
            return runCatching {
                val nParam = extractQueryParam(url, "n")

                // For ?n= query-param URLs (WEB_REMIX client):
                // The oL5 wrapper (nsigWrapper) uses g.oA(url, true).get("n") to obtain the
                // DECODED n value. g.oA stores the URL and processes query params — it IS the
                // nsig decoder. The wrapper then replaces the /n/ENCODED path segment with /n/DECODED.
                //
                // For this to work with a WEB_REMIX ?n= URL, we construct a synthetic googlevideo
                // URL that has BOTH the encoded n in the /n/ path segment AND ?n=VALUE in the
                // query string. g.oA will process ?n=VALUE and return the decoded value; the
                // wrapper then replaces /n/ENCODED → /n/DECODED in our synthetic URL.
                // We extract the decoded value from the result and replace ?n= in the real URL.
                if (nParam != null && !url.contains("/n/")) {
                    // Check in-memory decode cache first — avoids repeated WebView round-trips.
                    val cachedDecoded = nsigCache[nParam]
                    if (cachedDecoded != null) {
                        log.info("decodeNsig: cache hit for n=$nParam → $cachedDecoded")
                        return@runCatching url.replace(Regex("[?&]n=[^&]*")) { mr ->
                            mr.value.replace(nParam, URLEncoder.encode(cachedDecoded, "UTF-8"))
                        }
                    }

                    // Build a synthetic URL: /n/ENCODED in path, ?n=ENCODED in query.
                    // g.oA will parse this and call its internal nsig transformer on ENCODED
                    // to obtain DECODED, which it stores internally; .get("n") returns DECODED.
                    val encodedNParam = URLEncoder.encode(nParam, "UTF-8")
                    val syntheticUrl = "https://rr1---sn-test.googlevideo.com/videoplayback/n/$nParam?n=$encodedNParam"
                    log.info("decodeNsig: ?n= URL detected. Calling nsigWrapper with synthetic URL for n=$nParam")

                    // IMPORTANT: wrap in IIFE so evaluateJavascript gets the return value.
                    // A bare try-catch statement's completion value is undefined in some WebView
                    // versions, causing the callback to receive JSON 'null'.
                    val escapedSyntheticUrl = syntheticUrl.replace("'", "\\'")
                    val jsCode = """(function(){
                        try {
                            var r = window.nsigWrapper('$escapedSyntheticUrl');
                            return r == null ? null : String(r);
                        } catch(e) {
                            return 'ERROR:' + String(e);
                        }
                    })()"""
                    val result = evaluator.evaluate(jsCode) ?: ""
                    val resultUnquoted = when {
                        result.startsWith("\"") && result.endsWith("\"") -> result.drop(1).dropLast(1)
                        else -> result
                    }

                    log.info("decodeNsig: nsigWrapper returned: '$resultUnquoted'")

                    // Extract decoded n from the /n/DECODED path segment of the returned URL.
                    val decodedN = when {
                        resultUnquoted.contains("/n/") -> {
                            resultUnquoted.substringAfter("/n/").substringBefore("/").substringBefore("?")
                        }
                        else -> null
                    }

                    if (decodedN != null && decodedN.isNotBlank() &&
                        decodedN != "null" && decodedN != "undefined" &&
                        decodedN != nParam && !decodedN.startsWith("ERROR")) {
                        log.info("decodeNsig: Successfully decoded n: $nParam → $decodedN")
                        nsigCache[nParam] = decodedN   // cache for reuse
                        return@runCatching url.replace(Regex("[?&]n=[^&]*")) { mr ->
                            mr.value.replace(nParam, URLEncoder.encode(decodedN, "UTF-8"))
                        }
                    }

                    log.warning("decodeNsig: nsigWrapper could not decode n=$nParam (result='$resultUnquoted'). URL unchanged.")
                    return@runCatching url
                }

                // For path-based /n/ URLs (non-WEB_REMIX clients), call wrapper directly.
                val escapedUrl = url.replace("'", "\\'")
                val jsCode = """(function(){
                    try {
                        var r = (typeof window.nsigWrapper === 'function') ? window.nsigWrapper('$escapedUrl') : '$escapedUrl';
                        return r == null ? '$escapedUrl' : String(r);
                    } catch(e) {
                        return '$escapedUrl';
                    }
                })()"""
                val result = evaluator.evaluate(jsCode) ?: url
                val cleaned = when {
                    result.startsWith("\"") && result.endsWith("\"") -> result.drop(1).dropLast(1)
                    else -> result
                }
                if (cleaned.isBlank() || cleaned == "null" || cleaned == "undefined") url else cleaned
            }.getOrElse { e ->
                log.warning("decodeNsig() URL-wrapper call failed: ${e.message}")
                url
            }
        }

        // ── Strategy 2: legacy ?n= query-param style ──────────────────────────────
        val nParam = extractQueryParam(url, "n") ?: return url  // no n-param → no throttling

        val funcName = cacheFuncName
        if (funcName == null) {
            log.warning("decodeNsig() failed: no nsig func name in cache")
            return url
        }

        val decoded = runCatching {
            val jsCode = "window.nsigFunc('$nParam')"
            val result = evaluator.evaluate(jsCode) ?: ""
            if (result.startsWith("\"") && result.endsWith("\"")) {
                result.drop(1).dropLast(1)
            } else result
        }.getOrNull() ?: return url

        if (decoded.isBlank() || decoded == "null") return url

        return url.replace(Regex("[?&]n=[^&]*")) { mr ->
            mr.value.replace(nParam, URLEncoder.encode(decoded, "UTF-8"))
        }
    }

    // ── Player JS resolution ──────────────────────────────────────────────────

    private suspend fun resolvePlayerUrl(httpClient: HttpClient): String? {
        // Primary: scrape iframe_api — it contains the current player hash
        // NOTE: iframe_api escapes forward slashes as \/ in the string literals.
        val iframeApi = runCatching {
            httpClient.get("https://www.youtube.com/iframe_api").bodyAsText()
        }.getOrNull()

        if (iframeApi != null) {
            val version = RE_PLAYER_VERSION.find(iframeApi)?.groupValues?.get(1)
            if (version != null) {
                return "https://www.youtube.com/s/player/$version/player_ias.vflset/en_US/base.js"
            }
        }

        // Fallback: scrape the YouTube Music web page for the base.js script tag
        val ytMusicPage = runCatching {
            httpClient.get("https://music.youtube.com/").bodyAsText()
        }.getOrNull() ?: return null

        val version = Regex("player/([a-zA-Z0-9_-]+)/").find(ytMusicPage)?.groupValues?.get(1)
            ?: return null
        return "https://www.youtube.com/s/player/$version/player_ias.vflset/en_US/base.js"
    }

    // ── Cipher extraction ─────────────────────────────────────────────────────

    private fun findDecipherCall(js: String): DecipherCall? {
        // Try new pattern: vi(8, 8658, f.s)
        RE_DECIPHER_NEW_CALL.find(js)?.let { match ->
            return DecipherCall(match.groupValues[1], match.groupValues[2], match.groupValues[3])
        }
        
        // Try old patterns
        val oldFuncName = RE_DECIPHER_FUNC_VAR.find(js)?.groupValues?.get(1)
            ?: RE_DECIPHER_FUNC_ASSIGN.find(js)?.groupValues?.get(1)
            ?: RE_DECIPHER_FUNC_DEF.find(js)?.groupValues?.get(1)
            
        return oldFuncName?.let { DecipherCall(it) }
    }

    /** Finds the opening `{` after [marker] and returns the brace-balanced body. */
    private fun extractBracedBody(js: String, marker: String): String? {
        val start = js.indexOf(marker).takeIf { it >= 0 } ?: return null
        val braceStart = js.indexOf('{', start).takeIf { it >= 0 } ?: return null
        var depth = 0
        val sb = StringBuilder()
        for (i in braceStart until js.length) {
            val c = js[i]
            sb.append(c)
            if (c == '{') depth++ else if (c == '}') { depth--; if (depth == 0) break }
        }
        return sb.toString()
    }



    // ── nsig extraction & interpretation ─────────────────────────────────────

    private fun extractNsigFuncBody(js: String): String? {
        val match = RE_NSIG_FUNC.find(js) ?: return null
        val funcName = match.groupValues[1]
        val idxStr = match.groupValues[2]

        val resolvedName = if (idxStr.isNotEmpty()) {
            val arrayBody = extractBracedBody(js, "var $funcName=[") ?: return null
            RE_QUOTED_NAME.findAll(arrayBody)
                .map { it.groupValues[1] }
                .toList()
                .getOrNull(idxStr.toIntOrNull() ?: 0)
        } else funcName

        if (resolvedName == null) return null
        return extractBracedBody(js, "$resolvedName=function(a)")
            ?: extractBracedBody(js, "function $resolvedName(a)")
    }

    /**
     * Best-effort pure-Kotlin interpreter for the nsig function.
     * Handles the common simple patterns; returns input unchanged if it cannot decode.
     */
    private fun interpretNsig(n: String): String {
        val body = cache?.nsigFuncBody ?: return n
        val chars = n.toMutableList()
        if (body.contains(".reverse()")) chars.reverse()
        RE_NSIG_SPLICE.find(body)?.groupValues?.get(1)?.toIntOrNull()?.let { count ->
            repeat(count) { if (chars.isNotEmpty()) chars.removeAt(0) }
        }
        return chars.joinToString("")
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun parseQuery(query: String): Map<String, String> =
        query.split("&").mapNotNull { part ->
            val idx = part.indexOf('=')
            if (idx < 0) null else part.substring(0, idx) to part.substring(idx + 1)
        }.toMap()

    private fun extractQueryParam(url: String, key: String): String? =
        Regex("[?&]" + Regex.escape(key) + "=([^&]*)").find(url)?.groupValues?.get(1)
}
