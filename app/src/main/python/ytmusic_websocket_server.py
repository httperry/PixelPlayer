"""
YouTube Music WebSocket Server (No Encryption)

Fast, always-running background service for YouTube Music API.

Features:
- WebSocket communication (faster than HTTP)
- Async/await (non-blocking)
- Pre-initialized ytmusicapi (instant responses)
- yt-dlp for stream URL extraction (replaces NewPipe)
- Progressive search: supports offset+limit for fast first-load
- Automatic reconnection support
- No encryption (localhost-only, safe for local communication)
"""

import asyncio
import json
import os
import time
from typing import Optional, Dict, Any, List
from ytmusicapi import YTMusic
import websockets
from websockets.server import serve

# Derive the app's private files directory at runtime so the same code works
# for both debug (com.theveloper.pixelplay.debug) and release builds.
_FILES_DIR = os.environ.get("HOME", "/data/data/com.theveloper.pixelplay.debug/files")
if "/files" not in _FILES_DIR:
    _FILES_DIR = os.path.dirname(os.path.abspath(__file__))

# yt-dlp for stream URL extraction
try:
    import yt_dlp
    YT_DLP_AVAILABLE = True
except ImportError:
    YT_DLP_AVAILABLE = False
    print("⚠️  yt-dlp not available — stream extraction will fail")

# Global state
ytmusic: Optional[YTMusic] = None
is_authenticated = False
_auth_source: Optional[str] = None

# Performance cache
cache: Dict[str, Dict[str, Any]] = {
    'library': {'data': None, 'timestamp': 0, 'ttl': 300},
    'playlists': {'data': None, 'timestamp': 0, 'ttl': 300},
}

# Stream URL cache (yt-dlp URLs expire after ~6 hours, cache 5h to be safe)
stream_cache: Dict[str, Dict[str, Any]] = {}
STREAM_CACHE_TTL = 5 * 60 * 60  # 5 hours in seconds

# ============================================================================
# CACHE HELPERS
# ============================================================================

def get_cached(key: str) -> Optional[Any]:
    """Get cached data if still valid"""
    if cache[key]['data'] and (time.time() - cache[key]['timestamp']) < cache[key]['ttl']:
        return cache[key]['data']
    return None

def set_cached(key: str, data: Any):
    """Cache data with timestamp"""
    cache[key]['data'] = data
    cache[key]['timestamp'] = time.time()

def invalidate_cache(key: str):
    """Invalidate specific cache"""
    cache[key]['data'] = None

def get_cached_stream_url(video_id: str) -> Optional[str]:
    """Get cached stream URL if still valid"""
    entry = stream_cache.get(video_id)
    if entry and (time.time() - entry['timestamp']) < STREAM_CACHE_TTL:
        return entry['url']
    return None

def set_cached_stream_url(video_id: str, url: str):
    """Cache a stream URL"""
    stream_cache[video_id] = {'url': url, 'timestamp': time.time()}

# ============================================================================
# MESSAGE HANDLERS
# ============================================================================

async def handle_auth_setup(data: Dict[str, Any]) -> Dict[str, Any]:
    """Setup authentication with cookies"""
    global ytmusic, is_authenticated, _auth_source
    
    # If already authenticated by setup_auth (from Java), don't overwrite it
    if is_authenticated and _auth_source == "browser.json":
        print("✅ Skipping WebSocket auth_setup because browser.json auth is already active")
        return {'success': True, 'authenticated': True, 'message': 'Already authenticated via browser.json'}
    
    try:
        cookies = data.get('cookies', '')
        if not cookies:
            return {'error': 'No cookies provided'}
        
        import json
        import re
        
        sapisid_hash = data.get('sapisid_hash', '')
        
        # ytmusicapi strictly requires __Secure-3PAPISID. If WebView only gave SAPISID, mirror it.
        if '__Secure-3PAPISID=' not in cookies and 'SAPISID=' in cookies:
            match = re.search(r'SAPISID=([^;]+)', cookies)
            if match:
                cookies += f"; __Secure-3PAPISID={match.group(1)}"
        
        # Save cookies in ytmusicapi headers format
        headers = {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36",
            "Accept": "*/*",
            "Accept-Language": "en-US,en;q=0.5",
            "Content-Type": "application/json",
            "X-Goog-AuthUser": "0",
            "x-origin": "https://music.youtube.com",
            "Cookie": cookies
        }
        
        if sapisid_hash:
            headers["Authorization"] = f"SAPISIDHASH {sapisid_hash}"
        
        cookies_path = os.path.join(_FILES_DIR, 'ytm_cookies.txt')
        with open(cookies_path, 'w') as f:
            f.write(json.dumps(headers, indent=4))
        
        # Initialize YTMusic with the correctly formatted headers
        ytmusic = YTMusic(cookies_path)
        is_authenticated = True
        _auth_source = "websocket"
        
        # Clear cache
        for key in cache:
            cache[key]['data'] = None
        
        with open(os.path.join(_FILES_DIR, 'last_auth_error.txt'), 'w') as f:
            f.write("SUCCESS")
            
        return {'success': True, 'authenticated': True}
    
    except Exception as e:
        import traceback
        with open(os.path.join(_FILES_DIR, 'last_auth_error.txt'), 'w') as f:
            f.write(traceback.format_exc())
        return {'error': str(e)}

async def handle_search(data: Dict[str, Any]) -> Dict[str, Any]:
    """
    Search YouTube Music with progressive pagination support.
    
    Supports 'limit' and 'offset' for progressive loading:
    - First call:  limit=10, offset=0  → fast, returns first 10 results
    - Second call: limit=10, offset=10 → background load of next 10
    """
    try:
        query = data.get('query', '')
        filter_type = data.get('filter', 'songs')
        limit = int(data.get('limit', 10))
        offset = int(data.get('offset', 0))
        
        ytm = ytmusic if ytmusic else YTMusic()
        
        # We need to fetch offset+limit items total, then slice
        total_needed = offset + limit
        # ytmusicapi uses limit param — fetch total needed (add buffer for safety)
        fetch_limit = max(total_needed, 10)
        
        loop = asyncio.get_event_loop()
        results = await loop.run_in_executor(
            None,
            lambda: ytm.search(query, filter=filter_type, limit=fetch_limit)
        )
        
        # Apply offset/limit slicing
        results_page = results[offset:offset + limit] if offset < len(results) else []
        
        return {
            'results': results_page,
            'offset': offset,
            'limit': limit,
            'total_fetched': len(results)
        }
    
    except Exception as e:
        return {'error': str(e)}

async def handle_get_stream_url(data: Dict[str, Any]) -> Dict[str, Any]:
    """
    Get audio stream URL for a YouTube video ID using yt-dlp.
    
    yt-dlp is more reliable than NewPipe for extracting YouTube stream URLs:
    - Handles YouTube's cipher obfuscation
    - Supports all audio formats (webm/opus, m4a, mp4)
    - Automatically selects the best quality audio stream
    - Actively maintained with frequent YouTube compatibility updates
    
    URL expires after ~6 hours; cached internally for 5 hours.
    """
    if not YT_DLP_AVAILABLE:
        return {'error': 'yt-dlp not available'}
    
    video_id = data.get('video_id', '')
    if not video_id:
        return {'error': 'No video_id provided'}
    
    # Check stream URL cache first
    cached_url = get_cached_stream_url(video_id)
    if cached_url:
        return {'stream_url': cached_url, 'cached': True}
    
    url = f'https://www.youtube.com/watch?v={video_id}'

    # ── CLIENT MATRIX (updated 2025-04-25) ─────────────────────────────────
    # client           | cookies | Premium 256kbps (141/258)? | Notes
    # -----------------+---------+----------------------------+---------------
    # web              |  YES    |  YES (primary choice)      | canonical auth
    # web_creator      |  YES    |  YES (secondary)           | also works
    # android_vr       |  no     |  NO — Opus 251 ~130kbps    | fallback; works
    #                  |         |                            | without PO token
    # tv_embedded      |  N/A    |  BROKEN — needs PO token   | deprecated
    # android_creator  |  N/A    |  BROKEN — needs PO token   | deprecated
    #
    # KEY RULES:
    # 1. ONLY use cookiefile for auth — do NOT pass Cookie in http_headers too
    #    (yt-dlp scopes http_headers cookies to download URL only, breaking auth)
    # 2. Write cookies for .youtube.com, www.youtube.com, AND music.youtube.com
    # 3. yt-dlp Python jsinterp solves 'web'/'web_creator' sig challenges —
    #    no external Node/Deno/QuickJS binary required.
    # ──────────────────────────────────────────────────────────────────────────

    # Shared base options
    _base_opts = {
        'noplaylist': True,
        'quiet': False,
        'no_warnings': False,
        'extract_flat': False,
        'skip_download': True,
        'writesubtitles': False,
        'writeautomaticsub': False,
        'force_ipv4': True,
        'postprocessors': [],
    }

    # ── LOAD COOKIES ───────────────────────────────────────────────────────
    cookie_str = ''
    netscape_cookies_path = os.path.join(_FILES_DIR, 'netscape_cookies.txt')

    if is_authenticated and ytmusic:
        try:
            headers_obj = getattr(ytmusic, 'headers', None) or {}
            cookie_str = headers_obj.get('Cookie', '') or headers_obj.get('cookie', '')
            if not cookie_str:
                import json as _json
                ck_path = os.path.join(_FILES_DIR, 'ytm_cookies.txt')
                if os.path.exists(ck_path):
                    with open(ck_path, 'r') as _f:
                        saved = _json.load(_f)
                    cookie_str = saved.get('Cookie', '') or saved.get('cookie', '')

            if cookie_str:
                with open(netscape_cookies_path, 'w') as f:
                    f.write('# Netscape HTTP Cookie File\n\n')
                    for part in cookie_str.split(';'):
                        part = part.strip()
                        if '=' not in part:
                            continue
                        k, v = part.split('=', 1)
                        k, v = k.strip(), v.strip()
                        f.write(f'.youtube.com\tTRUE\t/\tTRUE\t2147483647\t{k}\t{v}\n')
                        f.write(f'www.youtube.com\tFALSE\t/\tTRUE\t2147483647\t{k}\t{v}\n')
                        f.write(f'music.youtube.com\tFALSE\t/\tTRUE\t2147483647\t{k}\t{v}\n')
                print(f'✅ Cookies loaded ({len(cookie_str)} chars) — Netscape jar written')
            else:
                print('⚠️  No cookies found — Premium extraction will be skipped')
        except Exception as _e:
            print(f'⚠️  Cookie setup failed: {_e}')
            cookie_str = ''

    # ── BUILD OPT DICTS ────────────────────────────────────────────────────
    # FIXES (confirmed from logcat 2025-04-25):
    #
    # FIX 1 — Do NOT pass Cookie in http_headers alongside cookiefile:
    #   yt-dlp warns and scopes http_headers Cookie to download URL only,
    #   NOT to the auth endpoints → "Please sign in" even though cookiefile
    #   exists. Use cookiefile ONLY for authentication.
    #
    # FIX 2 — tv_embedded and android_creator are deprecated in latest yt-dlp:
    #   Both show "Skipping unsupported client" in logs. They now need PO token.
    #   android_vr still works without PO token (confirmed in logs).
    #
    # FIX 3 — Use 'web' as primary Premium client:
    #   'web' is the canonical authenticated client; returns 141/258 with valid
    #   session cookies. 'web_creator' is tried as secondary if web fails.

    # ATTEMPT 1: Premium — authenticated web client (cookiefile only, no http_headers Cookie)
    premium_opts = {
        **_base_opts,
        'format': '258/141/251/bestaudio/best',
        'extractor_args': {'youtube': {'player_client': ['web', 'web_creator']}},
    }
    if cookie_str:
        # ONLY set cookiefile — do NOT also set Cookie in http_headers
        premium_opts['cookiefile'] = netscape_cookies_path

    # ATTEMPT 2: Unauthenticated fallback — android_vr works without PO token
    fallback_opts = {
        **_base_opts,
        'format': '251/140/bestaudio/best',
        'extractor_args': {'youtube': {'player_client': ['android_vr']}},
    }

    def extract_stream(opts_dict, label):
        try:
            with yt_dlp.YoutubeDL(opts_dict) as ydl:
                print(f'🎬 [{label}] Extracting: {video_id}')
                info = ydl.extract_info(url, download=False)
                if not info:
                    print(f'❌ [{label}] No info returned')
                    return None, None, None

                direct_url = info.get('url')
                if direct_url:
                    acodec = info.get('acodec', '')
                    ext = info.get('ext', 'webm')
                    tbr = info.get('tbr', 0) or info.get('abr', 0) or 0
                    fmt_id = info.get('format_id', '?')
                    print(f'✅ [{label}] Format {fmt_id}: {acodec} {ext} {tbr:.0f}kbps')
                    return direct_url, f'audio/{ext}', int(tbr * 1000)

                # Adaptive: manually pick best audio from formats list
                formats = info.get('formats', [])
                audio_fmts = [
                    f for f in formats
                    if f.get('acodec') not in (None, 'none')
                    and f.get('vcodec') in (None, 'none', 'video only')
                    and f.get('url')
                ]
                if not audio_fmts:
                    audio_fmts = [f for f in formats if f.get('url') and f.get('acodec') not in (None, 'none')]
                if not audio_fmts:
                    print(f'❌ [{label}] No audio formats')
                    return None, None, None

                def score(f):
                    br = f.get('tbr', 0) or f.get('abr', 0) or 0
                    return br + (5 if 'opus' in (f.get('acodec', '') or '').lower() else 0)

                best = max(audio_fmts, key=score)
                ext = best.get('ext', 'webm')
                tbr = best.get('tbr', 0) or best.get('abr', 0) or 0
                fmt_id = best.get('format_id', '?')
                acodec = best.get('acodec', '')
                print(f'✅ [{label}] Manual {fmt_id}: {acodec} {ext} {tbr:.0f}kbps')
                return best['url'], f'audio/{ext}', int(tbr * 1000)

        except Exception as exc:
            msg = str(exc)
            if 'sign in' in msg.lower() or 'login' in msg.lower():
                print(f'🔐 [{label}] Auth required — cookies may be expired')
            else:
                print(f'❌ [{label}] Error: {msg[:200]}')
            return None, None, None

    try:
        loop = asyncio.get_event_loop()

        # ── ATTEMPT 1: Premium (web_creator + cookies) ────────────────────
        if cookie_str:
            stream_url, mime_type, bitrate = await loop.run_in_executor(
                None, extract_stream, premium_opts, 'Premium/web_creator')
            if stream_url:
                set_cached_stream_url(video_id, stream_url)
                return {
                    'stream_url': stream_url,
                    'mime_type': mime_type or 'audio/webm',
                    'bitrate': bitrate or 0,
                    'cached': False,
                    'quality': 'premium',
                }
            print('🔄 Premium failed — falling back to unauthenticated client')
        else:
            print('ℹ️  No cookies — skipping Premium attempt')

        # ── ATTEMPT 2: Standard (tv_embedded, no auth needed) ─────────────
        stream_url, mime_type, bitrate = await loop.run_in_executor(
            None, extract_stream, fallback_opts, 'Standard/tv_embedded')
        if stream_url:
            set_cached_stream_url(video_id, stream_url)
            return {
                'stream_url': stream_url,
                'mime_type': mime_type or 'audio/webm',
                'bitrate': bitrate or 0,
                'cached': False,
                'quality': 'standard',
            }

        return {'error': f'No stream URL found for video_id: {video_id}'}

    except Exception as e:
        return {'error': f'yt-dlp extraction failed: {str(e)}'}


async def handle_library_songs(data: Dict[str, Any]) -> Dict[str, Any]:
    """Get library songs with pagination support.
    
    First call (offset=0, limit=50) returns fast.
    Subsequent calls fetch the next page.
    Returns has_more=True if there are more songs to fetch.
    """
    if not is_authenticated:
        return {'error': 'Not authenticated'}
    
    try:
        offset = int(data.get('offset', 0))
        limit = int(data.get('limit', 50))
        
        # Use full cache when available
        cached = get_cached('library')
        if cached:
            page = cached[offset:offset + limit]
            return {
                'songs': page,
                'cached': True,
                'offset': offset,
                'limit': limit,
                'total': len(cached),
                'has_more': (offset + limit) < len(cached)
            }
        
        loop = asyncio.get_event_loop()
        
        # Fetch only what we need for this page (fast first response)
        # ytmusicapi paginates internally — limit=None fetches all but is slow
        fetch_limit = offset + limit
        songs = await loop.run_in_executor(
            None,
            lambda: ytmusic.get_library_songs(limit=fetch_limit)
        )
        
        page = songs[offset:offset + limit] if songs else []
        has_more = len(songs) >= fetch_limit  # If we got exactly what we asked for, assume there's more
        
        # Cache only when we have a reasonably complete response
        if not has_more:
            set_cached('library', songs)
        
        return {
            'songs': page,
            'cached': False,
            'offset': offset,
            'limit': limit,
            'total': len(songs),
            'has_more': has_more
        }
    
    except Exception as e:
        return {'error': str(e)}

async def handle_library_playlists(data: Dict[str, Any]) -> Dict[str, Any]:
    """Get library playlists"""
    if not is_authenticated:
        return {'error': 'Not authenticated'}
    
    try:
        # Check cache
        cached = get_cached('playlists')
        if cached:
            return {'playlists': cached, 'cached': True}
        
        loop = asyncio.get_event_loop()
        playlists = await loop.run_in_executor(
            None,
            lambda: ytmusic.get_library_playlists(limit=None)
        )
        set_cached('playlists', playlists)
        
        return {'playlists': playlists, 'cached': False}
    
    except Exception as e:
        return {'error': str(e)}

async def handle_get_playlist(data: Dict[str, Any]) -> Dict[str, Any]:
    """Get playlist details — requires authentication for personal playlists"""
    try:
        playlist_id = data.get('playlist_id', '')
        limit = data.get('limit', 200)
        
        # Use authenticated instance if available, fallback to guest
        ytm = ytmusic if is_authenticated and ytmusic else YTMusic()
        loop = asyncio.get_event_loop()
        playlist = await loop.run_in_executor(
            None,
            lambda: ytm.get_playlist(playlist_id, limit=limit)
        )
        
        # ytmusicapi returns tracks under 'tracks' key
        tracks = playlist.get('tracks', [])
        print(f"✅ get_playlist: {playlist_id} → {len(tracks)} tracks")
        return {'playlist': playlist, 'tracks': tracks}
    
    except Exception as e:
        print(f"❌ get_playlist error: {e}")
        return {'error': str(e)}

async def handle_watch_playlist(data: Dict[str, Any]) -> Dict[str, Any]:
    """Get YTM radio/watch playlist (recommended next songs) for a video."""
    try:
        video_id = data.get('video_id', '')
        playlist_id = data.get('playlist_id', '')
        limit = data.get('limit', 25)

        ytm = ytmusic if is_authenticated and ytmusic else YTMusic()
        loop = asyncio.get_event_loop()

        watch_data = await loop.run_in_executor(
            None,
            lambda: ytm.get_watch_playlist(
                videoId=video_id if video_id else None,
                playlistId=playlist_id if playlist_id else None,
                limit=limit,
                radio=True
            )
        )

        tracks = watch_data.get('tracks', [])
        print(f"✅ watch_playlist: videoId={video_id} → {len(tracks)} radio tracks")
        return {'tracks': tracks}

    except Exception as e:
        print(f"❌ watch_playlist error: {e}")
        return {'error': str(e)}

async def handle_create_playlist(data: Dict[str, Any]) -> Dict[str, Any]:
    """Create playlist"""
    if not is_authenticated:
        return {'error': 'Not authenticated'}
    
    try:
        title = data.get('title', '')
        description = data.get('description', '')
        privacy = data.get('privacy', 'PRIVATE')
        video_ids = data.get('video_ids', [])
        
        loop = asyncio.get_event_loop()
        playlist_id = await loop.run_in_executor(
            None,
            lambda: ytmusic.create_playlist(
                title=title,
                description=description,
                privacy_status=privacy,
                video_ids=video_ids
            )
        )
        
        invalidate_cache('playlists')
        
        return {'playlist_id': playlist_id}
    
    except Exception as e:
        return {'error': str(e)}

async def handle_add_to_playlist(data: Dict[str, Any]) -> Dict[str, Any]:
    """Add songs to playlist"""
    if not is_authenticated:
        return {'error': 'Not authenticated'}
    
    try:
        playlist_id = data.get('playlist_id', '')
        video_ids = data.get('video_ids', [])
        
        loop = asyncio.get_event_loop()
        result = await loop.run_in_executor(
            None,
            lambda: ytmusic.add_playlist_items(playlist_id, video_ids)
        )
        
        return {'success': True, 'result': result}
    
    except Exception as e:
        return {'error': str(e)}

async def handle_remove_from_playlist(data: Dict[str, Any]) -> Dict[str, Any]:
    """Remove songs from playlist"""
    if not is_authenticated:
        return {'error': 'Not authenticated'}
    
    try:
        playlist_id = data.get('playlist_id', '')
        video_ids = data.get('video_ids', [])
        
        loop = asyncio.get_event_loop()
        
        # Get playlist to find setVideoIds
        playlist = await loop.run_in_executor(
            None,
            lambda: ytmusic.get_playlist(playlist_id)
        )
        tracks = playlist.get('tracks', [])
        
        set_video_ids = []
        for track in tracks:
            if track.get('videoId') in video_ids:
                set_video_ids.append(track.get('setVideoId'))
        
        result = await loop.run_in_executor(
            None,
            lambda: ytmusic.remove_playlist_items(playlist_id, set_video_ids)
        )
        
        return {'success': True, 'result': result}
    
    except Exception as e:
        return {'error': str(e)}

async def handle_like_song(data: Dict[str, Any]) -> Dict[str, Any]:
    """Like a song"""
    if not is_authenticated:
        return {'error': 'Not authenticated'}
    
    try:
        video_id = data.get('video_id', '')
        loop = asyncio.get_event_loop()
        await loop.run_in_executor(
            None,
            lambda: ytmusic.rate_song(video_id, 'LIKE')
        )
        
        invalidate_cache('library')
        
        return {'success': True}
    
    except Exception as e:
        return {'error': str(e)}

async def handle_unlike_song(data: Dict[str, Any]) -> Dict[str, Any]:
    """Unlike a song"""
    if not is_authenticated:
        return {'error': 'Not authenticated'}
    
    try:
        video_id = data.get('video_id', '')
        loop = asyncio.get_event_loop()
        await loop.run_in_executor(
            None,
            lambda: ytmusic.rate_song(video_id, 'INDIFFERENT')
        )
        
        invalidate_cache('library')
        
        return {'success': True}
    
    except Exception as e:
        return {'error': str(e)}

async def handle_get_home(data: Dict[str, Any]) -> Dict[str, Any]:
    """Get home feed"""
    try:
        ytm = ytmusic if ytmusic else YTMusic()
        loop = asyncio.get_event_loop()
        home = await loop.run_in_executor(
            None,
            lambda: ytm.get_home(limit=20)
        )
        
        return {'home': home}
    
    except Exception as e:
        return {'error': str(e)}

async def handle_get_history(data: Dict[str, Any]) -> Dict[str, Any]:
    """Get recently played history"""
    if not is_authenticated:
        return {'error': 'Not authenticated'}
    try:
        loop = asyncio.get_event_loop()
        history = await loop.run_in_executor(
            None,
            lambda: ytmusic.get_history()
        )
        return {'history': history}
    except Exception as e:
        return {'error': str(e)}

async def handle_get_artist(data: Dict[str, Any]) -> Dict[str, Any]:
    """Get artist details"""
    try:
        browse_id = data.get('browse_id', '')
        
        ytm = ytmusic if ytmusic else YTMusic()
        loop = asyncio.get_event_loop()
        artist = await loop.run_in_executor(
            None,
            lambda: ytm.get_artist(browse_id)
        )
        
        return {'artist': artist}
    
    except Exception as e:
        return {'error': str(e)}

async def handle_search_suggestions(data: Dict[str, Any]) -> Dict[str, Any]:
    """Get search autocomplete suggestions"""
    try:
        query = data.get('query', '')
        if not query:
            return {'suggestions': []}
            
        ytm = ytmusic if ytmusic else YTMusic()
        loop = asyncio.get_event_loop()
        suggestions = await loop.run_in_executor(
            None,
            lambda: ytm.get_search_suggestions(query)
        )
        
        return {'suggestions': suggestions}
    except Exception as e:
        return {'error': str(e)}

# ============================================================================
# MESSAGE ROUTER
# ============================================================================

HANDLERS = {
    'auth_setup': handle_auth_setup,
    'search': handle_search,
    'search_suggestions': handle_search_suggestions,
    'get_stream_url': handle_get_stream_url,   # NEW: yt-dlp stream extraction
    'library_songs': handle_library_songs,
    'library_playlists': handle_library_playlists,
    'get_playlist': handle_get_playlist,
    'watch_playlist': handle_watch_playlist,   # YTM radio queue
    'create_playlist': handle_create_playlist,
    'add_to_playlist': handle_add_to_playlist,
    'remove_from_playlist': handle_remove_from_playlist,
    'like_song': handle_like_song,
    'unlike_song': handle_unlike_song,
    'get_home': handle_get_home,
    'get_history': handle_get_history,
    'get_artist': handle_get_artist,
}

async def handle_message(message_data: Dict[str, Any]) -> Dict[str, Any]:
    """Route message to appropriate handler"""
    action = message_data.get('action', '')
    data = message_data.get('data', {})
    request_id = message_data.get('request_id', '')
    
    # LOG EVERY REQUEST
    print(f"📥 REQUEST: action={action}, request_id={request_id[:8]}..., data_keys={list(data.keys())}")
    
    handler = HANDLERS.get(action)
    if not handler:
        error_result = {
            'request_id': request_id,
            'error': f'Unknown action: {action}'
        }
        print(f"❌ RESPONSE: {error_result}")
        return error_result
    
    result = await handler(data)
    result['request_id'] = request_id
    
    # LOG EVERY RESPONSE
    if 'error' in result:
        print(f"❌ RESPONSE: action={action}, error={result['error']}")
    else:
        print(f"✅ RESPONSE: action={action}, success=True")
    
    return result

# ============================================================================
# WEBSOCKET SERVER
# ============================================================================

async def websocket_handler(websocket):
    """Handle WebSocket connection"""
    print(f"📱 Client connected: {websocket.remote_address}")
    
    try:
        async for message in websocket:
            try:
                # Parse JSON message (no encryption)
                message_data = json.loads(message)
                
                # Handle message asynchronously
                response = await handle_message(message_data)
                
                # Send response as JSON
                response_json = json.dumps(response, default=str)
                await websocket.send(response_json)
                
            except json.JSONDecodeError as e:
                error_response = json.dumps({
                    'error': f'Invalid JSON: {str(e)}'
                })
                await websocket.send(error_response)
            
            except Exception as e:
                error_response = json.dumps({
                    'error': f'Handler error: {str(e)}'
                })
                await websocket.send(error_response)
    
    except websockets.exceptions.ConnectionClosed:
        print(f"📱 Client disconnected: {websocket.remote_address}")
    
    except Exception as e:
        print(f"❌ WebSocket error: {e}")

async def start_server(encryption_key_b64: str = None):
    """Start WebSocket server"""
    # No encryption needed for localhost
    if encryption_key_b64:
        print("⚠️  Encryption key provided but not used (localhost only)")
    
    yt_dlp_status = "✅ yt-dlp available" if YT_DLP_AVAILABLE else "❌ yt-dlp NOT available"
    
    # Start server
    async with serve(websocket_handler, "127.0.0.1", 8765):
        print("🎵 YouTube Music WebSocket Server")
        print("📡 Listening on ws://127.0.0.1:8765")
        print("🔓 No encryption (localhost only)")
        print(f"🎬 Stream extraction: {yt_dlp_status}")
        print("✅ Ready for connections!")
        
        # Keep server running
        await asyncio.Future()

# ============================================================================
# ENTRY POINT
# ============================================================================

def setup_auth(browser_json_path: str) -> Dict[str, Any]:
    """
    Setup authentication from browser.json file.
    Called from Android after WebView login.
    
    Args:
        browser_json_path: Absolute path to browser.json file
        
    Returns:
        Dict with success status and authentication state
    """
    global ytmusic, is_authenticated, _auth_source
    
    try:
        import json
        
        # Read browser.json
        with open(browser_json_path, 'r') as f:
            browser_headers = json.load(f)
        
        # Initialize YTMusic with browser headers
        ytmusic = YTMusic(browser_json_path)
        is_authenticated = True
        _auth_source = "browser.json"
        
        print(f"✅ Authenticated from browser.json: {browser_json_path}")
        
        return {
            'success': True,
            'authenticated': True,
            'message': 'Authentication successful'
        }
        
    except FileNotFoundError:
        error_msg = f"browser.json not found at: {browser_json_path}"
        print(f"❌ {error_msg}")
        return {
            'success': False,
            'authenticated': False,
            'error': error_msg
        }
        
    except Exception as e:
        error_msg = f"Failed to setup auth: {str(e)}"
        print(f"❌ {error_msg}")
        return {
            'success': False,
            'authenticated': False,
            'error': error_msg
        }

def run_server(encryption_key_b64: str = None):
    """Run WebSocket server (called from Android)"""
    try:
        print("🚀 Starting asyncio event loop...")
        asyncio.run(start_server(encryption_key_b64))
    except Exception as e:
        print(f"❌ FATAL: run_server crashed: {e}")
        import traceback
        traceback.print_exc()
        # Write error to file for debugging
        try:
            with open(os.path.join(_FILES_DIR, 'python_server_error.txt'), 'w') as f:
                f.write(f"Error: {e}\n")
                f.write(traceback.format_exc())
        except:
            pass
        raise
