"""
YouTube Music API Server - Always Running Background Service

This Python server runs as a background service and provides a fast HTTP API
for YouTube Music operations. It stays initialized and ready for instant responses.

Architecture:
- Runs on localhost:8765
- Pre-initialized ytmusicapi instance
- Fast response times (no startup delay)
- Handles authentication via cookies
"""

from flask import Flask, request, jsonify
from ytmusicapi import YTMusic
import json
import os
import threading
import time

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

def get_cached(key):
    """Get cached data if still valid"""
    if cache[key]['data'] and (time.time() - cache[key]['timestamp']) < cache[key]['ttl']:
        return cache[key]['data']
    return None

def set_cached(key, data):
    """Cache data with timestamp"""
    cache[key]['data'] = data
    cache[key]['timestamp'] = time.time()

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
# SEARCH
# ============================================================================

@app.route('/search', methods=['POST'])
def search():
    """Search YouTube Music"""
    try:
        data = request.json
        query = data.get('query', '')
        filter_type = data.get('filter', 'songs')  # songs, albums, artists, playlists
        limit = data.get('limit', 20)
        
        if not ytmusic:
            ytmusic_instance = YTMusic()
        else:
            ytmusic_instance = ytmusic
        
        results = ytmusic_instance.search(query, filter=filter_type, limit=limit)
        
        return jsonify({'results': results})
    
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
        # Check cache first
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
        # Check cache first
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
        
        # Invalidate playlists cache
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
        
        # Get playlist to find setVideoIds
        playlist = ytmusic.get_playlist(playlist_id)
        tracks = playlist.get('tracks', [])
        
        # Find setVideoIds for the videos to remove
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
        
        # Invalidate library cache
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
        
        # Invalidate library cache
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
        'ytmusic_ready': ytmusic is not None
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
    print("✅ Ready for requests!")
    run_server()
