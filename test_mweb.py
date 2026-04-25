import yt_dlp
video_id = 'c6rCRy6SrtU'
ydl_opts = {
    'format': '141/251/bestaudio/best',
    'extractor_args': {
        'youtube': {
            'player_client': ['mweb', 'web'],
        }
    }
}
try:
    with yt_dlp.YoutubeDL(ydl_opts) as ydl:
        info = ydl.extract_info(f'https://www.youtube.com/watch?v={video_id}', download=False)
        print("Success!")
except Exception as e:
    print(f"Error: {e}")
