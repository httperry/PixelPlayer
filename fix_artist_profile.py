with open("app/src/main/java/com/theveloper/pixelplay/data/network/ytmusic/YTMusicRepository.kt", "r") as f:
    text = f.read()

q = "    suspend fun getArtistProfile(channelId: String): YTMArtistProfile? {"
idx = text.find(q)
end = text.find("}", text.find("catch", idx)) + 1
if idx != -1 and end != -1:
    new_func = """    suspend fun getArtistProfile(channelId: String): YTMArtistProfile? {
        return withContext(Dispatchers.IO) {
            try {
                val result = com.zionhuang.innertube.YouTube.artist(channelId)
                val artistPage = result.getOrNull() ?: return@withContext null
                
                YTMArtistProfile(
                    channelId = channelId,
                    name = artistPage.artist.title,
                    bio = artistPage.description,
                    monthlyListeners = "",
                    thumbnailUrl = artistPage.artist.thumbnail,
                    albums = emptyList(),
                    topSongs = emptyList()
                )
            } catch (e: Exception) {
                Log.e(TAG, "Get artist failed: ${e.message}", e)
                null
            }
        }
"""
    text = text[:idx] + new_func + text[end:]
    with open("app/src/main/java/com/theveloper/pixelplay/data/network/ytmusic/YTMusicRepository.kt", "w") as f:
        f.write(text)
    print("Done getArtistProfile")
