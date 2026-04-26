import re
with open("app/src/main/java/com/theveloper/pixelplay/data/network/ytmusic/YTMusicRepository.kt", "r") as f:
    text = f.read()

idx = text.find("suspend fun getStreamUrl")
print(text[idx:idx+800])
