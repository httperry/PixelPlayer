import re

with open("app/src/main/java/com/theveloper/pixelplay/data/network/ytmusic/YTMusicRepository.kt", "r") as f:
    text = f.read()

# getPlayerRawStream using yt-dlp / websocket getStreamUrl
q_stream = '    suspend fun getPlayerRawStream('
idx_s = text.find(q_stream)
end_s = text.find('    suspend fun getHome(', idx_s)
if idx_s != -1 and end_s != -1:
    new_stream = """    suspend fun getPlayerRawStream(videoId: String): YTMPlayerResponse? {
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

"""
    text = text[:idx_s] + new_stream + text[end_s:]

# getArtistProfile using getArtist
q_ap = '    suspend fun getArtistProfile('
idx_ap = text.find(q_ap)
end_ap = text.find('}\n\nprivate fun com.zionhuang.innertube.models.SongItem.toDomainSong()', idx_ap)
if idx_ap != -1 and end_ap != -1:
    new_ap = """    suspend fun getArtistProfile(channelId: String): YTMArtistProfile? {
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

"""
    text = text[:idx_ap] + new_ap + text[end_ap:]

with open("app/src/main/java/com/theveloper/pixelplay/data/network/ytmusic/YTMusicRepository.kt", "w") as f:
    f.write(text)
