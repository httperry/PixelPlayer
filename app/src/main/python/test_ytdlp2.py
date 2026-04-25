import yt_dlp

video_id = "dQw4w9WgXcQ"

for client in ['android', 'web', 'tv_embedded', 'mweb']:
    print(f"\n=== Testing client: {client} ===")
    ydl_opts = {
        'format': 'bestaudio/best',
        'quiet': True,
        'no_warnings': True,
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
            for f in audio_formats:
                print(f"  Format {f.get('format_id')}: {f.get('ext')} - abr: {f.get('abr', 'N/A')}k - tbr: {f.get('tbr', 'N/A')}k - codec: {f.get('acodec')}")
    except Exception as e:
        print(f"Error: {e}")
