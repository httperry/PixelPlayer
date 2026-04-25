import yt_dlp
ydl_opts = {
    'format': '141/251/bestaudio/best',
    'extractor_args': {'youtube': {'player_client': ['tv']}}
}
try:
    with yt_dlp.YoutubeDL(ydl_opts) as ydl:
        info = ydl.extract_info('https://www.youtube.com/watch?v=c6rCRy6SrtU', download=False)
        print("Success! Title:", info.get('title'))
except Exception as e:
    print(f"Error: {e}")
