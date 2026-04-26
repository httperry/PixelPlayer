def get_func_bounds(text, func_def):
    start = text.find(func_def)
    if start == -1: return -1, -1
    
    # Find first '{'
    brace_idx = text.find('{', start)
    if brace_idx == -1: return -1, -1
    
    open_braces = 1
    i = brace_idx + 1
    in_str = False
    escape = False
    
    while i < len(text) and open_braces > 0:
        c = text[i]
        if escape:
            escape = False
        elif c == '\\':
            escape = True
        elif c == '"':
            in_str = not in_str
        elif not in_str:
            if c == '{':
                open_braces += 1
            elif c == '}':
                open_braces -= 1
        i += 1
        
    if open_braces == 0:
        return start, i
    return -1, -1

with open("app/src/main/java/com/theveloper/pixelplay/data/network/ytmusic/YTMusicRepository.kt", "r") as f:
    text = f.read()

funcs_to_replace = {
    "private fun connectWithRetry": "",
    "private suspend fun <T> executeWithRetry": "",
    "fun getLibrarySongsFlow": """fun getLibrarySongsFlow(): Flow<PagingData<Song>> {
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
    }""",
    "suspend fun getUserPlaylists": """suspend fun getUserPlaylists(): List<YTMPlaylist> {
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
    }""",
    "suspend fun getPlaylist": """suspend fun getPlaylist(playlistId: String): List<Song> {
        return withContext(Dispatchers.IO) {
            try {
                val result = com.zionhuang.innertube.YouTube.playlist(playlistId)
                val playlist = result.getOrNull() ?: return@withContext emptyList()
                playlist.songs.map { it.toDomainSong() }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }""",
    "suspend fun getRadioQueue": """suspend fun getRadioQueue(videoId: String, limit: Int = 20): List<Song> {
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
    }""",
    "suspend fun getPlayerRawStream": """suspend fun getPlayerRawStream(videoId: String): YTMPlayerResponse? {
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
                                AdaptiveFormat(url = streamUrl, mimeType = audioFormat?.mimeType ?: "audio/webm", bitrate = audioFormat?.bitrate ?: 128000)
                            )
                        )
                    )
                }
                null
            } catch (e: Exception) {
                null
            }
        }
    }""",
    "suspend fun getHome": """suspend fun getHome(): List<YTMAlbumShelf> { return emptyList() }""",
    "suspend fun getArtistProfile": """suspend fun getArtistProfile(channelId: String): YTMArtistProfile? {
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
            } catch (e: Exception) { null }
        }
    }"""
}

# Do replacements
for name, rep in funcs_to_replace.items():
    start, end = get_func_bounds(text, name)
    if start != -1:
        if rep == "":
            text = text[:start] + text[end:]
        else:
            text = text[:start] + "    " + rep.replace("\n", "\n    ") + text[end:]

import re
# Remove the private val webSocketClient parameter
text = re.sub(r'    private val webSocketClient: YTMusicWebSocketClient,\n', '', text)

with open("app/src/main/java/com/theveloper/pixelplay/data/network/ytmusic/YTMusicRepository.kt", "w") as f:
    f.write(text)

