import re

with open("app/src/main/java/com/theveloper/pixelplay/data/network/ytmusic/YTMusicRepository.kt", "r") as f:
    text = f.read()

# Replace getPlayerRawStream using purely regex matching everything until getHome
text = re.sub(r'    suspend fun getPlayerRawStream\(videoId: String\): YTMPlayerResponse\? \{[\s\S]*?    suspend fun getHome', r'''    suspend fun getPlayerRawStream(videoId: String): YTMPlayerResponse? {
        return withContext(Dispatchers.IO) {
            try {
                val playerResponse = com.zionhuang.innertube.YouTube.player(videoId).getOrNull()
                val formats = playerResponse?.streamingData?.adaptiveFormats ?: emptyList()
                
                val audioFormat = formats
                    .filter { it.isAudio || it.mimeType.startsWith("audio/") }
                    .maxByOrNull { it.bitrate }
                    
                val streamUrl = audioFormat?.url ?: formats.firstOrNull { it.url != null }?.url
                
                if (streamUrl != null) {
                    return@withContext YTMPlayerResponse(
                        videoDetails = VideoDetails(videoId = videoId),
                        streamingData = StreamingData(
                            adaptiveFormats = listOf(
                                AdaptiveFormat(
                                    url = streamUrl,
                                    mimeType = audioFormat?.mimeType ?: "audio/webm",
                                    bitrate = audioFormat?.bitrate ?: 128000
                                )
                            )
                        )
                    )
                } else {
                    return@withContext null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get player raw stream", e)
                null
            }
        }
    }

    suspend fun getHome''', text)

text = re.sub(r'    suspend fun getHome\(\): List<Song> \{[\s\S]*?    suspend fun createPlaylist', r'''    suspend fun getHome(): List<Song> {
        return emptyList()
    }

    suspend fun createPlaylist''', text)


text = re.sub(r'    suspend fun getArtistProfile\(channelId: String\): YTMArtistProfile\? \{[\s\S]*?\}\s*private fun', r'''    suspend fun getArtistProfile(channelId: String): YTMArtistProfile? {
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
    }
}

private fun''', text)

with open("app/src/main/java/com/theveloper/pixelplay/data/network/ytmusic/YTMusicRepository.kt", "w") as f:
    f.write(text)
