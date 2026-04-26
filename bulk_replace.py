import re

with open("app/src/main/java/com/theveloper/pixelplay/data/network/ytmusic/YTMusicRepository.kt", "r") as f:
    text = f.read()

# 1. createPlaylist
q_create = '    suspend fun createPlaylist('
idx_c = text.find(q_create)
end_c = text.find('    suspend fun addVideoToPlaylist(', idx_c)
if idx_c != -1 and end_c != -1:
    new_create = """    suspend fun createPlaylist(
        title: String,
        description: String = "",
        videoIds: List<String> = emptyList()
    ): String? {
        return withContext(Dispatchers.IO) {
            try {
                // InnerTube doesn't natively expose a simple create playlist helper in YouTube.kt
                // We stub this or call the raw API if needed. For now, just return null.
                Log.w(TAG, "createPlaylist natively not fully supported yet")
                null
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create playlist: ${e.message}", e)
                null
            }
        }
    }

"""
    text = text[:idx_c] + new_create + text[end_c:]

# 2. addVideoToPlaylist
q_add = '    suspend fun addVideoToPlaylist('
idx_a = text.find(q_add)
end_a = text.find('    suspend fun getPlaylistTracks(', idx_a)
if idx_a != -1 and end_a != -1:
    new_add = """    suspend fun addVideoToPlaylist(playlistId: String, videoId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.w(TAG, "addVideoToPlaylist natively not fully supported yet")
                false
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add video to playlist: ${e.message}", e)
                false
            }
        }
    }

"""
    text = text[:idx_a] + new_add + text[end_a:]

# 3. getStreamUrl
q_stream = '    suspend fun getStreamUrl('
idx_s = text.find(q_stream)
end_s = text.find('    suspend fun getHome(', idx_s)
if idx_s != -1 and end_s != -1:
    new_stream = """    suspend fun getStreamUrl(videoId: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val playerResponse = com.zionhuang.innertube.YouTube.player(videoId).getOrNull()
                val formats = playerResponse?.streamingData?.adaptiveFormats ?: emptyList()
                
                val audioFormat = formats
                    .filter { it.isAudio || it.mimeType.startsWith("audio/") }
                    .maxByOrNull { it.bitrate }
                    
                val streamUrl = audioFormat?.url ?: formats.firstOrNull { it.url != null }?.url
                
                if (streamUrl != null) {
                    Result.success(streamUrl)
                } else {
                    Result.failure(Exception("Stream URL not found"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get stream url from InnerTube: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

"""
    text = text[:idx_s] + new_stream + text[end_s:]

# 4. getSearchSuggestions
q_sugg = '    suspend fun getSearchSuggestions('
idx_sugg = text.find(q_sugg)
end_sugg = text.find('    suspend fun searchSongs(', idx_sugg)
if idx_sugg != -1 and end_sugg != -1:
    new_sugg = """    suspend fun getSearchSuggestions(query: String): List<String> {
        return withContext(Dispatchers.IO) {
            try {
                val result = com.zionhuang.innertube.YouTube.getSearchSuggestions(query)
                result.getOrNull() ?: emptyList()
            } catch (e: Exception) {
                Log.e(TAG, "Search suggestions failed: ${e.message}", e)
                emptyList()
            }
        }
    }

"""
    text = text[:idx_sugg] + new_sugg + text[end_sugg:]


# 5. searchSongs
q_ss = '    suspend fun searchSongs('
idx_ss = text.find(q_ss)
end_ss = text.find('    suspend fun searchArtists(', idx_ss)
if idx_ss != -1 and end_ss != -1:
    new_ss = """    suspend fun searchSongs(query: String): List<Song> {
        return withContext(Dispatchers.IO) {
            try {
                val result = com.zionhuang.innertube.YouTube.search(query, "songs")
                val page = result.getOrNull() ?: return@withContext emptyList()
                page.items.mapNotNull { if (it is com.zionhuang.innertube.models.SongItem) it.toDomainSong() else null }
            } catch (e: Exception) {
                Log.e(TAG, "Search failed for '$query': ${e.message}", e)
                emptyList()
            }
        }
    }

"""
    text = text[:idx_ss] + new_ss + text[end_ss:]


# 6. searchArtists
q_sa = '    suspend fun searchArtists('
idx_sa = text.find(q_sa)
end_sa = text.find('    fun getLibrarySongsFlow(', idx_sa)
if idx_sa != -1 and end_sa != -1:
    new_sa = """    suspend fun searchArtists(query: String): List<ArtistRef> {
        return withContext(Dispatchers.IO) {
            try {
                val result = com.zionhuang.innertube.YouTube.search(query, "artists")
                val page = result.getOrNull() ?: return@withContext emptyList()
                page.items.mapNotNull { 
                    if (it is com.zionhuang.innertube.models.ArtistItem) {
                        ArtistRef(
                            id = it.id,
                            name = it.title,
                            thumbnailUrl = it.thumbnail
                        )
                    } else null 
                }
            } catch (e: Exception) {
                Log.e(TAG, "Search artists failed for '$query': ${e.message}", e)
                emptyList()
            }
        }
    }

"""
    text = text[:idx_sa] + new_sa + text[end_sa:]

# 7. getHome
q_h = '    suspend fun getHome('
idx_h = text.find(q_h)
end_h = text.find('    suspend fun getRecentlyPlayed(', idx_h)
if idx_h != -1 and end_h != -1:
    new_h = """    suspend fun getHome(): List<Song> {
        return emptyList() // Stubbed for now to bypass websocket
    }

"""
    text = text[:idx_h] + new_h + text[end_h:]

# 8. getRecentlyPlayed / getHistory
q_hist = '    suspend fun getRecentlyPlayed('
idx_hist = text.find(q_hist)
end_hist = text.find('    suspend fun getCachedRecentlyPlayed(', idx_hist)
if idx_hist != -1 and end_hist != -1:
    new_hist = """    suspend fun getRecentlyPlayed(): List<Song> {
        return emptyList() // Stubbed for now to bypass websocket
    }

"""
    text = text[:idx_hist] + new_hist + text[end_hist:]


# 9. searchSongsFlow
q_ssf = '    fun searchSongsFlow('
idx_ssf = text.find(q_ssf)
end_ssf = text.find('    suspend fun getSearchSuggestions(', idx_ssf)
if idx_ssf != -1 and end_ssf != -1:
    new_ssf = """    fun searchSongsFlow(query: String): Flow<YTMSearchResults> = flow {
        val result = com.zionhuang.innertube.YouTube.search(query, "songs").getOrNull()
        if (result != null) {
            val songs = result.items.mapNotNull { if (it is com.zionhuang.innertube.models.SongItem) it.toDomainSong() else null }
            emit(YTMSearchResults(songs = songs, hasMore = false))
        } else {
            emit(YTMSearchResults(hasMore = false))
        }
    }

"""
    text = text[:idx_ssf] + new_ssf + text[end_ssf:]

# 10. getArtist
q_art = '    suspend fun getArtist('
idx_art = text.find(q_art)
end_art = text.find('    suspend fun getArtistProfile(', idx_art)
if idx_art != -1 and end_art != -1:
    new_art = """    suspend fun getArtist(channelId: String): com.theveloper.pixelplay.data.model.Artist? {
        return withContext(Dispatchers.IO) {
            try {
                val result = com.zionhuang.innertube.YouTube.artist(channelId)
                val artistPage = result.getOrNull() ?: return@withContext null
                
                val topSongs = mutableListOf<com.theveloper.pixelplay.data.model.Song>()
                val albums = mutableListOf<com.theveloper.pixelplay.data.model.Album>()
                val singles = mutableListOf<com.theveloper.pixelplay.data.model.Album>()
                
                artistPage.sections.forEach { section ->
                    val titleText = section.title.lowercase()
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
                        "singles" in titleText -> {
                            section.items.forEach { item ->
                                if (item is com.zionhuang.innertube.models.AlbumItem) {
                                    singles.add(com.theveloper.pixelplay.data.model.Album(
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
                
                com.theveloper.pixelplay.data.model.Artist(
                    id = artistPage.artist.id,
                    name = artistPage.artist.title,
                    description = artistPage.description,
                    thumbnailUrl = artistPage.artist.thumbnail,
                    topSongs = topSongs,
                    albums = albums,
                    singles = singles,
                    similarArtists = emptyList()
                )
            } catch (e: Exception) {
                Log.e(TAG, "Get artist failed: ${e.message}", e)
                null
            }
        }
    }

"""
    text = text[:idx_art] + new_art + text[end_art:]


with open("app/src/main/java/com/theveloper/pixelplay/data/network/ytmusic/YTMusicRepository.kt", "w") as f:
    f.write(text)
