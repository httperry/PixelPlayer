with open("app/src/main/java/com/theveloper/pixelplay/data/network/ytmusic/YTMusicRepository.kt", "r") as f:
    text = f.read()

old_recent = """    suspend fun getRecentlyPlayed(): List<Song> {
        return withContext(Dispatchers.IO) {
            try {
                YTMusicPythonService.keepAlive()

                // Wait for Python backend to be authenticated (max 5 seconds)
                var authRetries = 0
                while (!YTMusicPythonService.isBackendAuthenticated() && authRetries < 10) {
                    kotlinx.coroutines.delay(500)
                    authRetries++
                }

                val result = webSocketClient.getHistory()
                result.onSuccess { history ->
                    val songs = history.mapNotNull { item ->
                        val videoId = item["videoId"] as? String ?: return@mapNotNull null
                        val songTitle = item["title"] as? String ?: "Unknown"
                        
                        @Suppress("UNCHECKED_CAST")
                        val artists = item["artists"] as? List<Map<String, Any>>
                        val artistName = artists?.firstOrNull()?.get("name") as? String ?: ""
                        
                        @Suppress("UNCHECKED_CAST")
                        val thumbnails = item["thumbnails"] as? List<Map<String, Any>>
                        val thumbnailUrl = YTMusicResponseParser.getBestThumbnail(thumbnails, 544)
                        
                        Song(
                            id = videoId,
                            title = songTitle,
                            artist = artistName,
                            artistId = -1L,
                            artists = emptyList(),
                            album = "",
                            albumId = -1L,
                            path = "",
                            contentUriString = "ytm://$videoId",
                            albumArtUriString = thumbnailUrl,
                            duration = 0L,
                            mimeType = "audio/webm",
                            bitrate = null,
                            sampleRate = null,
                            ytmusicId = videoId
                        )
                    }
                    Log.d(TAG, "YTM getHistory returned ${songs.size} items")
                    return@withContext songs
                }.onFailure { error ->
                    Log.e(TAG, "getHistory failed: ${error.message}", error)
                }

                emptyList()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get history: ${e.message}", e)
                emptyList()
            }
        }
    }"""

new_recent = """    suspend fun getRecentlyPlayed(): List<Song> {
        return withContext(Dispatchers.IO) {
            try {
                val result = com.zionhuang.innertube.YouTube.getHistory()
                val items = result.getOrNull() ?: emptyList()
                val songs = items.map { it.toDomainSong() }
                Log.d(TAG, "YTM getHistory returned ${songs.size} items")
                return@withContext songs
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get history: ${e.message}", e)
                emptyList()
            }
        }
    }"""
text = text.replace(old_recent, new_recent)

with open("app/src/main/java/com/theveloper/pixelplay/data/network/ytmusic/YTMusicRepository.kt", "w") as f:
    f.write(text)

