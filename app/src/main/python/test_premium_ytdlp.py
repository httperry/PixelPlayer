import yt_dlp
import os

video_id = "dQw4w9WgXcQ" # Premium or normal music video
cookie_file = os.path.join("/Users/niranjana/System/PixelPlayer/PixelPlayer/app/src/main/python", "netscape_cookies.txt")

for client in ['tv', 'mweb', 'web', 'android', 'tv_embedded', 'android_creator']:
    print(f"\n=== Testing client: {client} ===")
    ydl_opts = {
        'format': 'bestaudio/best',
        'quiet': True,
        'no_warnings': True,
        'cookiefile': cookie_file if os.path.exists(cookie_file) else None,
        'extractor_args': {
            'youtube': {
                'player_client': [client],
                'player_skip': ['webpage', 'configs'],
            }
        }
    }
    
    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(f'https://www.youtube.com/watch?v={video_id}', download=False)
            formats = info.get('formats', [])
            audio_formats = [f for f in formats if f.get('acodec') != 'none' and f.get('vcodec') == 'none']
            
            print(f"Found {len(audio_formats)} audio-only formats")
            best_tbr = 0
            best_format = None
            for f in audio_formats:
                print(f"  Format {f.get('format_id')}: {f.get('ext')} - abr: {f.get('abr', 'N/A')}k - tbr: {f.get('tbr', 'N/A')}k - codec: {f.get('acodec')}")
                tbr = f.get('tbr') or 0
                if tbr > best_tbr:
                    best_tbr = tbr
                    best_format = f
            if best_format:
                print(f"  --> BEST: {best_format.get('format_id')} at {best_tbr}k")
    except Exception as e:
        print(f"Error: {e}")
