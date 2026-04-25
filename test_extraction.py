import yt_dlp

video_id = 'c6rCRy6SrtU' # from the user's logs
url = f'https://www.youtube.com/watch?v={video_id}'

ydl_opts = {
    'format': '141/251/bestaudio/best',
    'extractor_args': {
        'youtube': {
            'player_client': ['tv_embedded', 'android_creator', 'ios'],
        }
    }
}

try:
    with yt_dlp.YoutubeDL(ydl_opts) as ydl:
        info = ydl.extract_info(url, download=False)
        print("Success! Title:", info.get('title'))
        print("Formats available:")
        for f in info.get('formats', []):
            if f.get('acodec') != 'none' and f.get('vcodec') == 'none':
                print(f" - {f.get('format_id')}: {f.get('acodec')} @ {f.get('abr') or f.get('tbr')}kbps")
except Exception as e:
    print(f"Error: {e}")
