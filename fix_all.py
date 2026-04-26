import re

with open("app/src/main/java/com/theveloper/pixelplay/data/network/ytmusic/YTMusicRepository.kt", "r") as f:
    content = f.read()

# Remove the private val webSocketClient: YTMusicWebSocketClient, from constructor
content = re.sub(r'    private val webSocketClient: YTMusicWebSocketClient,\n', '', content)

# Keep YTMusicPythonService.keepAlive() but remove webSocketClient.connect() from init
content = re.sub(r'        GlobalScope\.launch\(Dispatchers\.IO\) {\n            webSocketClient\.connect\(\)\n            YTMusicPythonService\.keepAlive\(\)\n        }\n', r'''        GlobalScope.launch(Dispatchers.IO) {
            YTMusicPythonService.keepAlive()
        }
''', content)

# Remove the retry wrappers: connectWithRetry and executeWithRetry
content = re.sub(r'    suspend fun connectWithRetry\(\) \{[\s\S]*?        \}\n    \}\n\n', '', content)
content = re.sub(r'    private suspend fun <T> executeWithRetry\(block: suspend \(\) -> T\): T \{[\s\S]*?    \}\n\n', '', content)

# Replace library methods
# getLibrarySongsFlow
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

# getUserPlaylists
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


# getPlaylist
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


# getRadioQueue
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

# getPlayerRawStream
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

# getHome
content = re.sub(r'    suspend fun getHome\(\): List<YTMAlbumShelf> \{[\s\S]*?        \}\n    \}', r'''    suspend fun getHome(): List<YTMAlbumShelf> {
        return emptyList() // Stubbed out unsupported features
    }''', content)


# getArtistProfile
content = re.sub(r'    suspend fun getArtistProfile\(channelId: String\): YTMArtistProfile\? \{[\s\S]*?        \}\n    \}', r'''    suspend fun getArtistProfile(channelId: String): YTMArtistProfile? {
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
                null
            }
        }
    }''', content)

with open("app/src/main/java/com/theveloper/pixelplay/data/network/ytmusic/YTMusicRepository.kt", "w") as f:
    f.write(content)

