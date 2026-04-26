with open("app/src/main/java/com/theveloper/pixelplay/data/network/ytmusic/YTMusicRepository.kt", "r") as f:
    text = f.read()

q = "    suspend fun getArtist(channelId: String): com.theveloper.pixelplay.data.model.Artist? {"
idx = text.find(q)
end = text.find("}", text.find("catch", idx)) + 1
if idx != -1 and end != -1:
    new_func = """    suspend fun getArtist(channelId: String): com.theveloper.pixelplay.data.model.Artist? {
        return withContext(Dispatchers.IO) {
            try {
                val result = com.zionhuang.innertube.YouTube.artist(channelId)
                val artistPage = result.getOrNull() ?: return@withContext null
                
                val topSongs = mutableListOf<com.theveloper.pixelplay.data.model.Song>()
                val albums = mutableListOf<com.theveloper.pixelplay.data.model.Album>()
                val singles = mutableListOf<com.theveloper.pixelplay.data.model.Album>()
                
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
                Log.e(TAG, "Failed to fetch artist details: ${e.message}", e)
                null
            }
        }
"""
    text = text[:idx] + new_func + text[end:]
    with open("app/src/main/java/com/theveloper/pixelplay/data/network/ytmusic/YTMusicRepository.kt", "w") as f:
        f.write(text)
    print("Done getArtist")
