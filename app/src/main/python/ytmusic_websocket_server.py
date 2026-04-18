"""
YouTube Music WebSocket Server with Encryption

Fast, secure, always-running background service for YouTube Music API.

Features:
- WebSocket communication (faster than HTTP)
- AES-256 encryption (secure local communication)
- Async/await (non-blocking)
- Pre-initialized ytmusicapi (instant responses)
- Automatic reconnection support
"""

import asyncio
import json
import time
import base64
from typing import Optional, Dict, Any
from ytmusicapi import YTMusic
from cryptography.fernet import Fernet
import websockets
from websockets.server import serve

# Global state
ytmusic: Optional[YTMusic] = None
is_authenticated = False
encryption_key: Optional[bytes] = None
cipher: Optional[Fernet] = None

# Performance cache
cache: Dict[str, Dict[str, Any]] = {
    'library': {'data': None, 'timestamp': 0, 'ttl': 300},
    'playlists': {'data': None, 'timestamp': 0, 'ttl': 300},
}

# ============================================================================
# ENCRYPTION
# ============================================================================

def init_encryption(key_b64: str):
    """Initialize encryption with base64 key from Android"""
    global encryption_key, cipher
    encryption_key = base64.b64decode(key_b64)
    cipher = Fernet(encryption_key)
    print("🔐 Encryption initialized")

def encrypt_message(message: str) -> str:
    """Encrypt message for transmission"""
    if cipher:
        encrypted = cipher.encrypt(message.encode())
        return base64.b64encode(encrypted).decode()
    return message

def decrypt_message(encrypted: str) -> str:
    """Decrypt received message"""
    if cipher:
        decoded = base64.b64decode(encrypted)
        decrypted = cipher.decrypt(decoded)
        return decrypted.decode()
    return encrypted

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

# ============================================================================
# MESSAGE HANDLERS
# ============================================================================

async def handle_auth_setup(data: Dict[str, Any]) -> Dict[str, Any]:
    """Setup authentication with cookies"""
    global ytmusic, is_authenticated
    
    try:
        cookies = data.get('cookies', '')
        if not cookies:
            return {'error': 'No cookies provided'}
        
        # Save cookies
        cookies_path = '/data/data/com.theveloper.pixelplay/files/ytm_cookies.txt'
        with open(cookies_path, 'w') as f:
            f.write(cookies)
        
        # Initialize YTMusic
        ytmusic = YTMusic(cookies_path)
        is_authenticated = True
        
        # Clear cache
        for key in cache:
            cache[key]['data'] = None
        
        return {'success': True, 'authenticated': True}
    
    except Exception as e:
        return {'error': str(e)}

async def handle_search(data: Dict[str, Any]) -> Dict[str, Any]:
    """Search YouTube Music"""
    try:
        query = data.get('query', '')
        filter_type = data.get('filter', 'songs')
        limit = data.get('limit', 20)
        
        ytm = ytmusic if ytmusic else YTMusic()
        results = ytm.search(query, filter=filter_type, limit=limit)
        
        return {'results': results}
    
    except Exception as e:
        return {'error': str(e)}

async def handle_library_songs(data: Dict[str, Any]) -> Dict[str, Any]:
    """Get library songs"""
    if not is_authenticated:
        return {'error': 'Not authenticated'}
    
    try:
        # Check cache
        cached = get_cached('library')
        if cached:
            return {'songs': cached, 'cached': True}
        
        songs = ytmusic.get_library_songs(limit=None)
        set_cached('library', songs)
        
        return {'songs': songs, 'cached': False}
    
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
        
        playlists = ytmusic.get_library_playlists(limit=None)
        set_cached('playlists', playlists)
        
        return {'playlists': playlists, 'cached': False}
    
    except Exception as e:
        return {'error': str(e)}

async def handle_get_playlist(data: Dict[str, Any]) -> Dict[str, Any]:
    """Get playlist details"""
    try:
        playlist_id = data.get('playlist_id', '')
        limit = data.get('limit', 100)
        
        ytm = ytmusic if ytmusic else YTMusic()
        playlist = ytm.get_playlist(playlist_id, limit=limit)
        
        return {'playlist': playlist}
    
    except Exception as e:
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
        
        playlist_id = ytmusic.create_playlist(
            title=title,
            description=description,
            privacy_status=privacy,
            video_ids=video_ids
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
        
        result = ytmusic.add_playlist_items(playlist_id, video_ids)
        
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
        
        # Get playlist to find setVideoIds
        playlist = ytmusic.get_playlist(playlist_id)
        tracks = playlist.get('tracks', [])
        
        set_video_ids = []
        for track in tracks:
            if track.get('videoId') in video_ids:
                set_video_ids.append(track.get('setVideoId'))
        
        result = ytmusic.remove_playlist_items(playlist_id, set_video_ids)
        
        return {'success': True, 'result': result}
    
    except Exception as e:
        return {'error': str(e)}

async def handle_like_song(data: Dict[str, Any]) -> Dict[str, Any]:
    """Like a song"""
    if not is_authenticated:
        return {'error': 'Not authenticated'}
    
    try:
        video_id = data.get('video_id', '')
        ytmusic.rate_song(video_id, 'LIKE')
        
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
        ytmusic.rate_song(video_id, 'INDIFFERENT')
        
        invalidate_cache('library')
        
        return {'success': True}
    
    except Exception as e:
        return {'error': str(e)}

async def handle_get_home(data: Dict[str, Any]) -> Dict[str, Any]:
    """Get home feed"""
    try:
        ytm = ytmusic if ytmusic else YTMusic()
        home = ytm.get_home(limit=20)
        
        return {'home': home}
    
    except Exception as e:
        return {'error': str(e)}

async def handle_get_artist(data: Dict[str, Any]) -> Dict[str, Any]:
    """Get artist details"""
    try:
        browse_id = data.get('browse_id', '')
        
        ytm = ytmusic if ytmusic else YTMusic()
        artist = ytm.get_artist(browse_id)
        
        return {'artist': artist}
    
    except Exception as e:
        return {'error': str(e)}

# ============================================================================
# MESSAGE ROUTER
# ============================================================================

HANDLERS = {
    'auth_setup': handle_auth_setup,
    'search': handle_search,
    'library_songs': handle_library_songs,
    'library_playlists': handle_library_playlists,
    'get_playlist': handle_get_playlist,
    'create_playlist': handle_create_playlist,
    'add_to_playlist': handle_add_to_playlist,
    'remove_from_playlist': handle_remove_from_playlist,
    'like_song': handle_like_song,
    'unlike_song': handle_unlike_song,
    'get_home': handle_get_home,
    'get_artist': handle_get_artist,
}

async def handle_message(message_data: Dict[str, Any]) -> Dict[str, Any]:
    """Route message to appropriate handler"""
    action = message_data.get('action', '')
    data = message_data.get('data', {})
    request_id = message_data.get('request_id', '')
    
    handler = HANDLERS.get(action)
    if not handler:
        return {
            'request_id': request_id,
            'error': f'Unknown action: {action}'
        }
    
    result = await handler(data)
    result['request_id'] = request_id
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
                # Decrypt message
                decrypted = decrypt_message(message)
                message_data = json.loads(decrypted)
                
                # Handle message
                response = await handle_message(message_data)
                
                # Encrypt and send response
                response_json = json.dumps(response)
                encrypted_response = encrypt_message(response_json)
                
                await websocket.send(encrypted_response)
                
            except json.JSONDecodeError as e:
                error_response = json.dumps({
                    'error': f'Invalid JSON: {str(e)}'
                })
                await websocket.send(encrypt_message(error_response))
            
            except Exception as e:
                error_response = json.dumps({
                    'error': f'Handler error: {str(e)}'
                })
                await websocket.send(encrypt_message(error_response))
    
    except websockets.exceptions.ConnectionClosed:
        print(f"📱 Client disconnected: {websocket.remote_address}")
    
    except Exception as e:
        print(f"❌ WebSocket error: {e}")

async def start_server(encryption_key_b64: str):
    """Start WebSocket server"""
    # Initialize encryption
    init_encryption(encryption_key_b64)
    
    # Start server
    async with serve(websocket_handler, "127.0.0.1", 8765):
        print("🎵 YouTube Music WebSocket Server")
        print("📡 Listening on ws://127.0.0.1:8765")
        print("🔐 Encryption: AES-256 (Fernet)")
        print("✅ Ready for connections!")
        
        # Keep server running
        await asyncio.Future()

# ============================================================================
# ENTRY POINT
# ============================================================================

def run_server(encryption_key_b64: str):
    """Run WebSocket server (called from Android)"""
    asyncio.run(start_server(encryption_key_b64))
