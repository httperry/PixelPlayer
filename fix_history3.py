with open("app/src/main/java/com/theveloper/pixelplay/data/network/ytmusic/YTMusicRepository.kt", "r") as f:
    text = f.read()

start_str = "    suspend fun getRecentlyPlayed(): List<Song> {"
end_str = "    suspend fun getArtist(channelId: String): YTMArtistProfile? {"

idx1 = text.find(start_str)
idx2 = text.find(end_str)

if idx1 != -1 and idx2 != -1:
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
    }
    
"""
    text = text[:idx1] + new_recent + text[idx2:]
    with open("app/src/main/java/com/theveloper/pixelplay/data/network/ytmusic/YTMusicRepository.kt", "w") as f:
        f.write(text)
    print("Fixed!")
else:
    print(f"Indices: {idx1}, {idx2}")

