from yt_dlp.extractor.youtube import YoutubeIE
print("Supported clients in this yt-dlp version:")
print(YoutubeIE._PLAYERS.keys() if hasattr(YoutubeIE, '_PLAYERS') else "Cannot find clients")
