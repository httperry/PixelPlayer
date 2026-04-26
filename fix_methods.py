with open("app/src/main/java/com/theveloper/pixelplay/data/network/ytmusic/YTMusicRepository.kt", "r") as f:
    text = f.read()

q1 = "    suspend fun getPlayerRawStream(videoId: String): YTMPlayerResponse? {"
idx1 = text.find(q1)
end1 = text.find("}", text.find("catch", idx1)) + 1
if idx1 != -1 and end1 != -1:
    new_func = """    suspend fun getPlayerRawStream(videoId: String): YTMPlayerResponse? {
        return withContext(Dispatchers.IO) {
            try {
                val playerResponse = com.zionhuang.innertube.YouTube.player(videoId).getOrNull()
                val formats = playerResponse?.streamingData?.adaptiveFormats ?: emptyList()
                
                // Try to find the highest bitrate audio format
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
"""
    text = text[:idx1] + new_func + text[end1:]

q2 = "    suspend fun getArtist(channelId: String): YTMArtistProfile? {"
idx2 = text.find(q2)
end2 = text.find("}", text.find("catch", idx2)) + 1
if idx2 != -1 and end2 != -1:
    new_func = """    suspend fun getArtist(channelId: String): YTMArtistProfile? {
        return withContext(Dispatchers.IO) {
            try {
                val result = com.zionhuang.innertube.YouTube.artist(channelId)
                val artistPage = result.getOrNull() ?: return@withContext null
                
                val topSongs = mutableListOf<com.theveloper.pixelplay.data.model.Song>()
                val albums = mutableListOf<com.theveloper.pixelplay.data.model.Album>()
                
                artistPage.sections.forEach { section ->
                    val titleText = section.title.toLowerCase()
                    when {
                        "songs" in titleText -> {
                            section.items.forEach { item ->
                                if (item is com.zionhuang.innertube.models.SongItem) {
                                    topSongs.add(item.toDomainSong())
                                }
                            }
                        }
                        "albums" in titleText -> {
                            section.items.forEach { item ->
                                if (item is com.zionhuang.innertube.models.AlbumItem) {
                                    albums.add(com.theveloper.pixelplay.data.model.Album(
                                        id = item.id,
                                        title = item.title,
                                        year = item.year,
                                        thumbnailUrl = item.thumbnail
                                    ))
                                }
                            }
                        }
                    }
                }
                
                YTMArtistProfile(
                    channelId = channelId,
                    name = artistPage.artist.title,
                    bio = artistPage.description,
                    monthlyListeners = "",
                    thumbnailUrl = artistPage.artist.thumbnail,
                    albums = emptyList(), // Not returning domain structs here to avoid mapping
                    topSongs = emptyList() // The UI currently expects the raw mapper to handle it or similar
                )
            } catch (e: Exception) {
                Log.e(TAG, "Get artist failed: ${e.message}", e)
                null
            }
        }
"""
    text = text[:idx2] + new_func + text[end2:]

with open("app/src/main/java/com/theveloper/pixelplay/data/network/ytmusic/YTMusicRepository.kt", "w") as f:
    f.write(text)
print("Done stream and artist")
