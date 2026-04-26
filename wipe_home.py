import re

with open("app/src/main/java/com/theveloper/pixelplay/data/network/ytmusic/YTMusicRepository.kt", "r") as f:
    text = f.read()

# I will replace from webSocketClient.getHome() down to where songs are populated
text = re.sub(r'                val result = webSocketClient\.getHome\(\)[\s\S]*?                        \).*?\n', r'''                val result = com.zionhuang.innertube.YouTube.home()
                val homeFeed = result.getOrNull() ?: return@withContext emptyList()
                
                // Return an empty list for now since we don't need home feed parsing directly.
                return@withContext emptyList()
''', text)

with open("app/src/main/java/com/theveloper/pixelplay/data/network/ytmusic/YTMusicRepository.kt", "w") as f:
    f.write(text)
