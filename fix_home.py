import re
with open("app/src/main/java/com/theveloper/pixelplay/data/network/ytmusic/YTMusicRepository.kt", "r") as f:
    text = f.read()

q = "    suspend fun getHomeDiscoverFeed(): List<YTMAlbumShelf> {"
idx = text.find(q)
end = text.find("    suspend fun getCachedHomeDiscoverFeed", idx)
if idx != -1 and end != -1:
    new_func = """    suspend fun getHomeDiscoverFeed(): List<YTMAlbumShelf> {
        return withContext(Dispatchers.IO) {
            try {
                val result = com.zionhuang.innertube.YouTube.home()
                val homePage = result.getOrNull()
                
                if (homePage != null) {
                    val shelves = homePage.sections.map { section ->
                        val title = section.title
                        val browseId = section.endpoint?.browseId
                        
                        val songs = section.items.mapNotNull { item ->
                            when (item) {
                                is com.zionhuang.innertube.models.SongItem -> item.toDomainSong()
                                else -> null
                            }
                        }
                        
                        YTMAlbumShelf(title, browseId, songs)
                    }.filter { it.songs.isNotEmpty() }
                    
                    // Save to cache
                    prefs.edit().putString("home_feed_cache", gson.toJson(shelves)).apply()
                    return@withContext shelves
                }

                // Fallback to cache
                val cached = prefs.getString("home_feed_cache", null)
                if (cached != null) {
                    val type = object : com.google.gson.reflect.TypeToken<List<YTMAlbumShelf>>() {}.type
                    return@withContext gson.fromJson(cached, type)
                }
                emptyList()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get YTM home feed: ${e.message}", e)
                val cached = prefs.getString("home_feed_cache", null)
                if (cached != null) {
                    val type = object : com.google.gson.reflect.TypeToken<List<YTMAlbumShelf>>() {}.type
                    return@withContext gson.fromJson(cached, type)
                }
                emptyList()
            }
        }
    }

"""
    text = text[:idx] + new_func + text[end:]
    with open("app/src/main/java/com/theveloper/pixelplay/data/network/ytmusic/YTMusicRepository.kt", "w") as f:
        f.write(text)
    print("Done getHome")
else:
    print("Could not find bounds")
