import re

with open("app/src/main/java/com/theveloper/pixelplay/data/network/ytmusic/YTMusicRepository.kt", "r") as f:
    content = f.read()

# 1. Remove the private val webSocketClient parameter
content = re.sub(r'    private val webSocketClient: YTMusicWebSocketClient,\n', '', content)

# 2. Remove connectWithRetry() completely
content = re.sub(r'    private fun connectWithRetry\(\) \{[\s\S]*?        \}\n    \}\n', '', content)

# 3. Remove executeWithRetry completely
content = re.sub(r'    private suspend fun <T> executeWithRetry\([\s\S]*?        return null\n    \}\n', '', content)

# 4. getLibrarySongsFlow that contains webSocketClient.getLibrarySongs
# Rewrite it simply to InnerTube
content = re.sub(r'    fun getLibrarySongsFlow\(\): Flow<PagingData<Song>> \{[\s\S]*?            \}\n        \}\n    \}', r'''    fun getLibrarySongsFlow(): Flow<PagingData<Song>> {
        return Pager(
            config = PagingConfig(pageSize = 50, enablePlaceholders = false),
            pagingSourceFactory = {
                object : PagingSource<Int, Song>() {
                    override fun getRefreshKey(state: PagingState<Int, Song>): Int? = null
                    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Song> {
                        return try {
                            val result = com.zionhuang.innertube.YouTube.playlist("LM")
                            val playlist = result.getOrNull() ?: return LoadResult.Error(Exception("YTM Library error"))
                            val songs = playlist.songs.map { it.toDomainSong() }
                            LoadResult.Page(data = songs, prevKey = null, nextKey = null)
                        } catch (e: Exception) {
                            LoadResult.Error(e)
                        }
                    }
                }
            }
        ).flow
    }''', content)

# 5. getUserPlaylists that contains webSocketClient.getLibraryPlaylists()
content = re.sub(r'    suspend fun getUserPlaylists\(\): List<YTMPlaylist> \{[\s\S]*?        \}\n    \}', r'''    suspend fun getUserPlaylists(): List<YTMPlaylist> {
        return withContext(Dispatchers.IO) {
            try {
                val result = com.zionhuang.innertube.YouTube.likedPlaylists()
                val lists = result.getOrNull() ?: return@withContext emptyList()
                lists.map { playlist ->
                    YTMPlaylist(
                        id = playlist.id,
                        title = playlist.title,
                        author = playlist.author ?: "YouTube Music",
                        itemCount = playlist.songCountText ?: "",
                        thumbnails = playlist.thumbnail?.let { listOf(MapThumbnail(it, 0, 0)) } ?: emptyList()
                    )
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }''', content)

# 6. getPlaylist that contains webSocketClient.getPlaylist()
content = re.sub(r'    suspend fun getPlaylist\(playlistId: String\): List<Song> \{[\s\S]*?        \}\n    \}', r'''    suspend fun getPlaylist(playlistId: String): List<Song> {
        return withContext(Dispatchers.IO) {
            try {
                val result = com.zionhuang.innertube.YouTube.playlist(playlistId)
                val playlist = result.getOrNull() ?: return@withContext emptyList()
                playlist.songs.map { it.toDomainSong() }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }''', content)

# 7. getRadioQueue that contains webSocketClient.getWatchPlaylist()
content = re.sub(r'    suspend fun getRadioQueue\(videoId: String, limit: Int = 20\): List<Song> \{[\s\S]*?        \}\n    \}', r'''    suspend fun getRadioQueue(videoId: String, limit: Int = 20): List<Song> {
        return withContext(Dispatchers.IO) {
            try {
                val result = com.zionhuang.innertube.YouTube.next(videoId, null)
                val nextResult = result.getOrNull() ?: return@withContext emptyList()
                val playlistId = nextResult.playlistId
                if (playlistId != null) {
                    val pResult = com.zionhuang.innertube.YouTube.playlist(playlistId)
                    val playlist = pResult.getOrNull() ?: return@withContext emptyList()
                    return@withContext playlist.songs.map { it.toDomainSong() }
                }
                emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }''', content)

# 8. getPlayerRawStream that contains webSocketClient.getStreamUrl
content = re.sub(r'    suspend fun getPlayerRawStream\(videoId: String\): YTMPlayerResponse\? \{[\s\S]*?        \}\n    \}', r'''    suspend fun getPlayerRawStream(videoId: String): YTMPlayerResponse? {
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
                null
            }
        }
    }''', content)

with open("app/src/main/java/com/theveloper/pixelplay/data/network/ytmusic/YTMusicRepository.kt", "w") as f:
    f.write(content)
