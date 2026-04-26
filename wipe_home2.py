import re

with open("app/src/main/java/com/theveloper/pixelplay/data/network/ytmusic/YTMusicRepository.kt", "r") as f:
    text = f.read()

# Completely wipe getHome from 360 all the way to 395 and rewrite properly.
new_home = """    suspend fun getHome(): List<YTMAlbumShelf> {
        return withContext(Dispatchers.IO) {
            try {
                emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }"""

text = re.sub(r'    suspend fun getHome\(\): List<YTMAlbumShelf> \{[\s\S]*?        \}\n    \}', new_home, text)

with open("app/src/main/java/com/theveloper/pixelplay/data/network/ytmusic/YTMusicRepository.kt", "w") as f:
    f.write(text)
