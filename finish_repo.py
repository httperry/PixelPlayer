with open("app/src/main/java/com/theveloper/pixelplay/data/network/ytmusic/YTMusicRepository.kt", "r") as f:
    text = f.read()

import re

# 5. Fix getLibrarySongsFlow
q1 = '    fun getLibrarySongsFlow(pageSize: Int = 50): Flow<List<Song>> = flow {'
idx1 = text.find(q1)
end1 = text.find('    suspend fun getUserPlaylists()', idx1)
if idx1 != -1 and end1 != -1:
    new_func = """    fun getLibrarySongsFlow(pageSize: Int = 50): Flow<List<Song>> = flow {
        val allSongs = mutableListOf<Song>()
        
        // Use InnerTube's native playlist fetcher on the "LM" (Liked Music) playlist
        val initialResult = com.zionhuang.innertube.YouTube.playlist("LM")
        val playlistPage = initialResult.getOrNull()
        
        if (playlistPage != null) {
            val firstPageSongs = playlistPage.songs.mapNotNull {
                if (it is com.zionhuang.innertube.models.SongItem) it.toDomainSong() else null
            }
            if (firstPageSongs.isNotEmpty()) {
                allSongs.addAll(firstPageSongs)
                emit(allSongs.toList())
            }
            
            var currentContinuation = playlistPage.continuation
            while (currentContinuation != null) {
                val contResult = com.zionhuang.innertube.YouTube.playlistContinuation(currentContinuation)
                val contPage = contResult.getOrNull() ?: break
                
                val contSongs = contPage.songs.mapNotNull {
                    if (it is com.zionhuang.innertube.models.SongItem) it.toDomainSong() else null
                }
                
                if (contSongs.isNotEmpty()) {
                    allSongs.addAll(contSongs)
                    emit(allSongs.toList())
                }
                
                currentContinuation = contPage.continuation
            }
        }
    }

"""
    text = text[:idx1] + new_func + text[end1:]

# 6. Fix getUserPlaylists
q2 = '    suspend fun getUserPlaylists(): List<com.theveloper.pixelplay.data.model.Playlist> {'
idx2 = text.find(q2)
end2 = text.find('    suspend fun createPlaylist', idx2)
if idx2 != -1 and end2 != -1:
    new_func = """    suspend fun getUserPlaylists(): List<com.theveloper.pixelplay.data.model.Playlist> {
        return withContext(Dispatchers.IO) {
            try {
                val result = com.zionhuang.innertube.YouTube.likedPlaylists()
                val playlists = result.getOrNull() ?: emptyList()
                
                val domainPlaylists = playlists.mapNotNull { item ->
                    val id = item.id.removePrefix("VL").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val title = item.title ?: "Unknown"
                    val thumbnailUrl = item.thumbnail
                    val trackCount = item.songCountText?.filter { it.isDigit() }?.toIntOrNull() ?: 0

                    ytMusicDao.insertPlaylist(
                        com.theveloper.pixelplay.data.database.YTMusicPlaylistEntity(
                            playlistId = id,
                            title = title,
                            thumbnailUrl = thumbnailUrl,
                            trackCount = trackCount,
                            cachedAt = System.currentTimeMillis(),
                            lastSynced = System.currentTimeMillis()
                        )
                    )

                    com.theveloper.pixelplay.data.model.Playlist(
                        id = id,
                        name = title,
                        songIds = emptyList(),
                        createdAt = System.currentTimeMillis(),
                        source = "YTM",
                        coverImageUri = thumbnailUrl,
                        externalTrackCount = trackCount
                    )
                }

                domainPlaylists
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch YTM playlists: ${e.message}", e)
                emptyList()
            }
        }
    }

"""
    text = text[:idx2] + new_func + text[end2:]

# 7. Fix playlist mutation commands
q3 = '    suspend fun createPlaylist('
idx3 = text.find(q3)
end3 = text.find('    suspend fun addToPlaylist(', idx3)
if idx3 != -1 and end3 != -1:
    new_func = """    suspend fun createPlaylist(
        title: String,
        description: String = "",
        videoIds: List<String> = emptyList()
    ): String? {
        return withContext(Dispatchers.IO) {
            try {
                val result = com.zionhuang.innertube.YouTube.innerTube.createPlaylist(title, description, videoIds)
                result.getOrNull()?.playlistId
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create playlist: ${e.message}", e)
                null
            }
        }
    }

"""
    text = text[:idx3] + new_func + text[end3:]


with open("app/src/main/java/com/theveloper/pixelplay/data/network/ytmusic/YTMusicRepository.kt", "w") as f:
    f.write(text)
print("done finish_repo")
