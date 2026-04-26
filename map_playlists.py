with open("app/src/main/java/com/theveloper/pixelplay/data/network/ytmusic/YTMusicRepository.kt", "r") as f:
    code = f.read()

playlists_old = """                    operation = {
                        YTMusicPythonService.keepAlive()

                        val result = webSocketClient.getLibraryPlaylists()
                        Log.d(TAG, "Raw library_playlists result: success=${result.isSuccess}, count=${result.getOrNull()?.size}")
                        result.getOrNull()?.mapNotNull { playlistData ->
                            Log.d(TAG, "Raw playlist keys: ${playlistData.keys}")
                            val parsed = YTMusicResponseParser.parsePlaylist(playlistData)
                            // ytmusicapi sometimes prepends "VL" to playlist IDs; strip it
                            val rawId = parsed["id"] as? String
                            val id = rawId?.removePrefix("VL")?.takeIf { it.isNotBlank() }
                            Log.d(TAG, "Parsed playlist: id=$id, title=${parsed["title"]}")
                            // Filter out playlists with null or empty IDs
                            if (id.isNullOrBlank()) {
                                Log.w(TAG, "Skipping playlist with blank id, raw keys: ${playlistData.keys}")
                                return@mapNotNull null
                            }
                            val title = parsed["title"] as? String ?: "Unknown"
                            val thumbnailUrl = parsed["thumbnailUrl"] as? String
                            val trackCount = (parsed["count"] as? Number)?.toInt() ?: 0

                            ytMusicDao.insertPlaylist(
                                com.theveloper.pixelplay.data.database.YTMusicPlaylistEntity(
                                    playlistId = id,
                                    title = title,
                                    thumbnailUrl = thumbnailUrl,
                                    trackCount = trackCount,
                                    cachedAt = System.currentTimeMillis()
                                )
                            )

                            com.theveloper.pixelplay.data.model.Playlist(
                                id = id,
                                name = title,
                                songIds = emptyList(), // fetch separately
                                createdAt = System.currentTimeMillis(),
                                source = "YTM",
                                coverImageUri = thumbnailUrl
                            )
                        } ?: emptyList()
                    },"""

playlists_new = """                    operation = {
                        val result = com.zionhuang.innertube.YouTube.likedPlaylists()
                        Log.d(TAG, "Raw library_playlists result: success=${result.isSuccess}")
                        result.getOrNull()?.mapNotNull { playlistItem ->
                            val id = playlistItem.id.removePrefix("VL").takeIf { it.isNotBlank() }
                            if (id.isNullOrBlank()) return@mapNotNull null
                            
                            val title = playlistItem.title
                            val thumbnailUrl = playlistItem.thumbnail
                            val trackCount = playlistItem.songCountText?.filter { it.isDigit() }?.toIntOrNull() ?: 0

                            ytMusicDao.insertPlaylist(
                                com.theveloper.pixelplay.data.database.YTMusicPlaylistEntity(
                                    playlistId = id,
                                    title = title,
                                    thumbnailUrl = thumbnailUrl,
                                    trackCount = trackCount,
                                    cachedAt = System.currentTimeMillis()
                                )
                            )

                            com.theveloper.pixelplay.data.model.Playlist(
                                id = id,
                                name = title,
                                songIds = emptyList(), // fetch separately
                                createdAt = System.currentTimeMillis(),
                                source = "YTM",
                                coverImageUri = thumbnailUrl
                            )
                        } ?: emptyList()
                    },"""

code = code.replace(playlists_old, playlists_new)

# For getPlaylistTracks

old_get_playlist = """                YTMusicPythonService.keepAlive()
                Log.d(TAG, "Fetching tracks for YTM playlist: $playlistId")

                val result = webSocketClient.getPlaylist(playlistId, limit = 500)
                result.onSuccess { playlistData ->
                    @Suppress("UNCHECKED_CAST")
                    val tracks = playlistData["tracks"] as? List<Map<String, Any>> ?: emptyList()
                    Log.d(TAG, "YTM playlist $playlistId has ${tracks.size} tracks")
                    val songs = YTMusicResponseParser.parsePlaylistTracks(tracks)"""

new_get_playlist = """                Log.d(TAG, "Fetching tracks for YTM playlist: $playlistId")

                val result = com.zionhuang.innertube.YouTube.playlist(playlistId)
                result.onSuccess { playlistData ->
                    val tracks = mutableListOf<com.zionhuang.innertube.models.SongItem>()
                    tracks.addAll(playlistData.songs)
                    var continuation = playlistData.songsContinuation
                    while (continuation != null) {
                        val next = com.zionhuang.innertube.YouTube.playlistContinuation(continuation).getOrNull() ?: break
                        tracks.addAll(next.songs)
                        continuation = next.continuation
                    }
                    Log.d(TAG, "YTM playlist $playlistId has ${tracks.size} tracks")
                    val songs = tracks.map { it.toDomainSong() }"""

code = code.replace(old_get_playlist, new_get_playlist)


with open("app/src/main/java/com/theveloper/pixelplay/data/network/ytmusic/YTMusicRepository.kt", "w") as f:
    f.write(code)

print("Updated playists and tracks logic.")
