"""
YouTube Music API Server - Always Running Background Service

This Python server runs as a background service and provides a fast HTTP API
for YouTube Music operations. It stays initialized and ready for instant responses.

Architecture:
- Runs on localhost:8765
- Pre-initialized ytmusicapi instance
- yt-dlp for audio stream URL extraction (replaces NewPipe)
- Progressive search with offset/limit pagination
- Fast response times (no startup delay)
- Handles authentication via cookies
"""

from flask import Flask, request, jsonify
from ytmusicapi import YTMusic
import json
import os
import threading
import time

# yt-dlp for stream URL extraction
try:
    import yt_dlp
    YT_DLP_AVAILABLE = True
except ImportError:
    YT_DLP_AVAILABLE = False

app = Flask(__name__)

# Global YTMusic instance (stays initialized)
ytmusic = None
cookies_path = None
is_authenticated = False

# Performance: Cache frequently accessed data
cache = {
    'library': {'data': None, 'timestamp': 0, 'ttl': 300},  # 5 min cache
    'playlists': {'data': None, 'timestamp': 0, 'ttl': 300},
}

# Stream URL cache (yt-dlp URLs expire ~6h, cache 5h)
stream_cache = {}
STREAM_CACHE_TTL = 5 * 60 * 60  # 5 hours

def get_cached(key):
    """Get cached data if still valid"""
    if cache[key]['data'] and (time.time() - cache[key]['timestamp']) < cache[key]['ttl']:
        return cache[key]['data']
    return None

def set_cached(key, data):
    """Cache data with timestamp"""
    cache[key]['data'] = data
    cache[key]['timestamp'] = time.time()

def get_cached_stream_url(video_id):
    entry = stream_cache.get(video_id)
    if entry and (time.time() - entry['timestamp']) < STREAM_CACHE_TTL:
        return entry['url']
    return None

def set_cached_stream_url(video_id, url):
    stream_cache[video_id] = {'url': url, 'timestamp': time.time()}

# ============================================================================
# AUTHENTICATION
# ============================================================================

@app.route('/auth/setup', methods=['POST'])
def setup_auth():
    """Initialize ytmusicapi with cookies"""
    global ytmusic, is_authenticated, cookies_path
    
    try:
        data = request.json
        cookies = data.get('cookies', '')
        
        if not cookies:
            return jsonify({'error': 'No cookies provided'}), 400
        
        # Save cookies to temp file
        cookies_path = '/data/data/com.theveloper.pixelplay/files/ytm_cookies.txt'
        with open(cookies_path, 'w') as f:
            f.write(cookies)
        
        # Initialize YTMusic with cookies
        ytmusic = YTMusic(cookies_path)
        is_authenticated = True
        
        # Clear cache on new auth
        for key in cache:
            cache[key]['data'] = None
        
        return jsonify({'success': True, 'authenticated': True})
    
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@app.route('/auth/status', methods=['GET'])
def auth_status():
    """Check if authenticated"""
    return jsonify({
        'authenticated': is_authenticated,
        'ready': ytmusic is not None
    })

# ============================================================================
# SEARCH (with offset/limit for progressive loading)
# ============================================================================

@app.route('/search', methods=['POST'])
def search():
    """
    Search YouTube Music with progressive pagination.
    
    Body params:
      query       - search string
      filter      - 'songs', 'albums', 'artists', 'playlists'
      limit       - max results per page (default 10)
      offset      - skip first N results (default 0)
    """
    try:
        data = request.json
        query = data.get('query', '')
        filter_type = data.get('filter', 'songs')
        limit = int(data.get('limit', 10))
        offset = int(data.get('offset', 0))
        
        if not ytmusic:
            ytmusic_instance = YTMusic()
        else:
            ytmusic_instance = ytmusic
        
        # Fetch total_needed items then slice
        total_needed = offset + limit
        fetch_limit = max(total_needed, 10)
        
        results = ytmusic_instance.search(query, filter=filter_type, limit=fetch_limit)
        results_page = results[offset:offset + limit] if offset < len(results) else []
        
        return jsonify({
            'results': results_page,
            'offset': offset,
            'limit': limit,
            'total_fetched': len(results)
        })
    
    except Exception as e:
        return jsonify({'error': str(e)}), 500

# ============================================================================
# STREAM URL — yt-dlp (replaces NewPipe Extractor)
# ============================================================================

@app.route('/stream/<video_id>', methods=['GET'])
def get_stream_url(video_id):
    """
    Get audio stream URL for a YouTube video ID using yt-dlp.
    
    yt-dlp is more reliable and actively maintained compared to NewPipe.
    It handles YouTube's cipher obfuscation automatically.
    
    URL is cached for 5 hours (YouTube stream URLs expire after ~6 hours).
    """
    if not YT_DLP_AVAILABLE:
        return jsonify({'error': 'yt-dlp not available'}), 503
    
    # Check cache first
    cached_url = get_cached_stream_url(video_id)
    if cached_url:
        return jsonify({'stream_url': cached_url, 'cached': True})
    
    try:
        url = f'https://www.youtube.com/watch?v={video_id}'
        
        ydl_opts = {
            'format': 'bestaudio/best',
            'noplaylist': True,
            'quiet': True,
            'no_warnings': True,
            'skip_download': True,
            'writesubtitles': False,
            'writeautomaticsub': False
        }
        
        stream_url = None
        mime_type = 'audio/webm'
        bitrate = 0
        
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(url, download=False)
            if info:
                # Try direct URL first
                direct_url = info.get('url')
                if direct_url:
                    stream_url = direct_url
                    ext = info.get('ext', 'webm')
                    mime_type = f'audio/{ext}'
                    bitrate = int((info.get('tbr', 0) or 0) * 1000)
                else:
                    # Pick best audio-only format
                    formats = info.get('formats', [])
                    audio_formats = [
                        f for f in formats
                        if f.get('acodec') not in (None, 'none')
                        and f.get('vcodec') in (None, 'none', 'video only')
                        and f.get('url')
                    ]
                    if not audio_formats:
                        audio_formats = [f for f in formats if f.get('url')]
                    
                    if audio_formats:
                        best = max(
                            audio_formats,
                            key=lambda f: f.get('tbr', 0) or f.get('abr', 0) or 0
                        )
                        stream_url = best['url']
                        ext = best.get('ext', 'webm')
                        mime_type = f'audio/{ext}'
                        bitrate = int((best.get('tbr', 0) or best.get('abr', 0) or 0) * 1000)
        
        if not stream_url:
            return jsonify({'error': f'No stream URL found for {video_id}'}), 404
        
        # Cache result
        set_cached_stream_url(video_id, stream_url)
        
        return jsonify({
            'stream_url': stream_url,
            'mime_type': mime_type,
            'bitrate': bitrate,
            'cached': False
        })
    
    except Exception as e:
        return jsonify({'error': str(e)}), 500

# ============================================================================
# LIBRARY
# ============================================================================

@app.route('/library/songs', methods=['GET'])
def get_library_songs():
    """Get user's library songs"""
    if not is_authenticated:
        return jsonify({'error': 'Not authenticated'}), 401
    
    try:
        cached = get_cached('library')
        if cached:
            return jsonify({'songs': cached})
        
        songs = ytmusic.get_library_songs(limit=None)
        set_cached('library', songs)
        
        return jsonify({'songs': songs})
    
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@app.route('/library/playlists', methods=['GET'])
def get_library_playlists():
    """Get user's playlists"""
    if not is_authenticated:
        return jsonify({'error': 'Not authenticated'}), 401
    
    try:
        cached = get_cached('playlists')
        if cached:
            return jsonify({'playlists': cached})
        
        playlists = ytmusic.get_library_playlists(limit=None)
        set_cached('playlists', playlists)
        
        return jsonify({'playlists': playlists})
    
    except Exception as e:
        return jsonify({'error': str(e)}), 500

# ============================================================================
# PLAYLISTS
# ============================================================================

@app.route('/playlist/<playlist_id>', methods=['GET'])
def get_playlist(playlist_id):
    """Get playlist details and songs"""
    try:
        limit = request.args.get('limit', 100, type=int)
        
        if not ytmusic:
            ytmusic_instance = YTMusic()
        else:
            ytmusic_instance = ytmusic
        
        playlist = ytmusic_instance.get_playlist(playlist_id, limit=limit)
        
        return jsonify({'playlist': playlist})
    
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@app.route('/playlist/create', methods=['POST'])
def create_playlist():
    """Create a new playlist"""
    if not is_authenticated:
        return jsonify({'error': 'Not authenticated'}), 401
    
    try:
        data = request.json
        title = data.get('title', '')
        description = data.get('description', '')
        privacy = data.get('privacy', 'PRIVATE')
        video_ids = data.get('video_ids', [])
        
        playlist_id = ytmusic.create_playlist(
            title=title,
            description=description,
            privacy_status=privacy,
            video_ids=video_ids
        )
        
        cache['playlists']['data'] = None
        
        return jsonify({'playlist_id': playlist_id})
    
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@app.route('/playlist/<playlist_id>/add', methods=['POST'])
def add_to_playlist(playlist_id):
    """Add songs to playlist"""
    if not is_authenticated:
        return jsonify({'error': 'Not authenticated'}), 401
    
    try:
        data = request.json
        video_ids = data.get('video_ids', [])
        
        result = ytmusic.add_playlist_items(playlist_id, video_ids)
        
        return jsonify({'success': True, 'result': result})
    
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@app.route('/playlist/<playlist_id>/remove', methods=['POST'])
def remove_from_playlist(playlist_id):
    """Remove songs from playlist"""
    if not is_authenticated:
        return jsonify({'error': 'Not authenticated'}), 401
    
    try:
        data = request.json
        video_ids = data.get('video_ids', [])
        
        playlist = ytmusic.get_playlist(playlist_id)
        tracks = playlist.get('tracks', [])
        
        set_video_ids = []
        for track in tracks:
            if track.get('videoId') in video_ids:
                set_video_ids.append(track.get('setVideoId'))
        
        result = ytmusic.remove_playlist_items(playlist_id, set_video_ids)
        
        return jsonify({'success': True, 'result': result})
    
    except Exception as e:
        return jsonify({'error': str(e)}), 500

# ============================================================================
# LIKES
# ============================================================================

@app.route('/like/<video_id>', methods=['POST'])
def like_song(video_id):
    """Like a song"""
    if not is_authenticated:
        return jsonify({'error': 'Not authenticated'}), 401
    
    try:
        ytmusic.rate_song(video_id, 'LIKE')
        cache['library']['data'] = None
        
        return jsonify({'success': True})
    
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@app.route('/unlike/<video_id>', methods=['POST'])
def unlike_song(video_id):
    """Unlike a song"""
    if not is_authenticated:
        return jsonify({'error': 'Not authenticated'}), 401
    
    try:
        ytmusic.rate_song(video_id, 'INDIFFERENT')
        cache['library']['data'] = None
        
        return jsonify({'success': True})
    
    except Exception as e:
        return jsonify({'error': str(e)}), 500

# ============================================================================
# RECOMMENDATIONS
# ============================================================================

@app.route('/home', methods=['GET'])
def get_home():
    """Get home feed recommendations"""
    try:
        if not ytmusic:
            ytmusic_instance = YTMusic()
        else:
            ytmusic_instance = ytmusic
        
        home = ytmusic_instance.get_home(limit=20)
        
        return jsonify({'home': home})
    
    except Exception as e:
        return jsonify({'error': str(e)}), 500

# ============================================================================
# ARTIST
# ============================================================================

@app.route('/artist/<browse_id>', methods=['GET'])
def get_artist(browse_id):
    """Get artist details"""
    try:
        if not ytmusic:
            ytmusic_instance = YTMusic()
        else:
            ytmusic_instance = ytmusic
        
        artist = ytmusic_instance.get_artist(browse_id)
        
        return jsonify({'artist': artist})
    
    except Exception as e:
        return jsonify({'error': str(e)}), 500

# ============================================================================
# HEALTH CHECK
# ============================================================================

@app.route('/health', methods=['GET'])
def health():
    """Health check endpoint"""
    return jsonify({
        'status': 'ok',
        'authenticated': is_authenticated,
        'ytmusic_ready': ytmusic is not None,
        'yt_dlp_available': YT_DLP_AVAILABLE
    })

# ============================================================================
# SERVER STARTUP
# ============================================================================

def run_server():
    """Run Flask server"""
    app.run(host='127.0.0.1', port=8765, debug=False, threaded=True)

if __name__ == '__main__':
    print("🎵 YouTube Music API Server Starting...")
    print("📡 Listening on http://127.0.0.1:8765")
    print(f"🎬 yt-dlp: {'✅ available' if YT_DLP_AVAILABLE else '❌ not available'}")
    print("✅ Ready for requests!")
    run_server()
