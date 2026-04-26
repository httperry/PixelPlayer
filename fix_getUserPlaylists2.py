with open("app/src/main/java/com/theveloper/pixelplay/data/network/ytmusic/YTMusicRepository.kt", "r") as f:
    text = f.read()

idx = text.find('    suspend fun getUserPlaylists(): List<com.theveloper.pixelplay.data.model.Playlist> {')
end = text.find('    suspend fun createPlaylist', idx)

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

text = text[:idx] + new_func + text[end:]
with open("app/src/main/java/com/theveloper/pixelplay/data/network/ytmusic/YTMusicRepository.kt", "w") as f:
    f.write(text)
print("done")
