import re
with open("app/src/main/java/com/theveloper/pixelplay/data/network/ytmusic/YTMusicRepository.kt", "r") as f:
    text = f.read()

# Replace searchSongs
q1 = "    suspend fun searchSongs(query: String)"
idx1 = text.find(q1)
end1 = text.find("    private suspend fun cacheSearchSong")
if idx1 != -1 and end1 != -1:
    new_search_songs = """    suspend fun searchSongs(query: String): YTMSearchResults {
        return withContext(Dispatchers.IO) {
            try {
                val searchResult = com.zionhuang.innertube.YouTube.search(query, com.zionhuang.innertube.YouTube.SearchFilter.FILTER_SONG)
                val items = searchResult.getOrNull()?.items ?: emptyList()
                val songs = items.mapNotNull { if (it is com.zionhuang.innertube.models.SongItem) it.toDomainSong() else null }
                songs.forEach { cacheSearchSong(it) }
                YTMSearchResults(songs = songs)
            } catch (e: Exception) {
                Log.e(TAG, "Search failed for '$query': ${e.message}", e)
                YTMSearchResults()
            }
        }
    }

"""
    text = text[:idx1] + new_search_songs + text[end1:]

# Replace searchArtists
q2 = "    suspend fun searchArtists(query: String)"
idx2 = text.find(q2)
end2 = text.find("    // =========================================================================", idx2)
if idx2 != -1 and end2 != -1:
    new_search_artists = """    suspend fun searchArtists(query: String): List<com.theveloper.pixelplay.data.model.Artist> {
        return withContext(Dispatchers.IO) {
            try {
                val searchResult = com.zionhuang.innertube.YouTube.search(query, com.zionhuang.innertube.YouTube.SearchFilter.FILTER_ARTIST)
                val items = searchResult.getOrNull()?.items ?: emptyList()
                items.mapNotNull { item ->
                    if (item is com.zionhuang.innertube.models.ArtistItem) {
                        com.theveloper.pixelplay.data.model.Artist(
                            id = item.id.hashCode().toLong(),
                            name = item.title,
                            songCount = 0,
                            imageUrl = item.thumbnail
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
    text = text[:idx2] + new_search_artists + text[end2:]

with open("app/src/main/java/com/theveloper/pixelplay/data/network/ytmusic/YTMusicRepository.kt", "w") as f:
    f.write(text)
print("done")
