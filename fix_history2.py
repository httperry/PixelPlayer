import re

with open("app/src/main/java/com/theveloper/pixelplay/data/network/ytmusic/YTMusicRepository.kt", "r") as f:
    text = f.read()

pattern = re.compile(r"    suspend fun getRecentlyPlayed\(\): List<Song> \{(.*?)\s*\} catch \(e: Exception\) \{\n                Log\.e\(TAG, \"Failed to get history:(.*?)\n            \}\n        \}\n    \}", re.DOTALL)

new_recent = """    suspend fun getRecentlyPlayed(): List<Song> {
        return withContext(Dispatchers.IO) {
            try {
                val result = com.zionhuang.innertube.YouTube.getHistory()
                val items = result.getOrNull() ?: emptyList()
                val songs = items.map { it.toDomainSong() }
                Log.d(TAG, "YTM getHistory returned ${songs.size} items")
                return@withContext songs
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get history: ${e.message}", e)
                emptyList()
            }
        }
    }"""

text = pattern.sub(new_recent, text)

with open("app/src/main/java/com/theveloper/pixelplay/data/network/ytmusic/YTMusicRepository.kt", "w") as f:
    f.write(text)

