import re
with open("app/src/main/java/com/theveloper/pixelplay/data/network/ytmusic/YTMusicRepository.kt", "r") as f:
    text = f.read()

# Replace getPlaylistTracks
q1 = "    suspend fun getPlaylistTracks(playlistId: String): List<Song>"
idx1 = text.find(q1)
end1 = text.find("    suspend fun getRadioQueue", idx1)
if idx1 != -1 and end1 != -1:
    new_tracks = """    suspend fun getPlaylistTracks(playlistId: String): List<Song> {
        return withContext(Dispatchers.IO) {
            try {
                val result = com.zionhuang.innertube.YouTube.playlist(playlistId)
                val playlistPage = result.getOrNull() ?: return@withContext emptyList()
                return@withContext playlistPage.songs.mapNotNull {
                    if (it is com.zionhuang.innertube.models.SongItem) it.toDomainSong() else null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get playlist tracks for $playlistId: ${e.message}", e)
                emptyList()
            }
        }
    }

"""
    text = text[:idx1] + new_tracks + text[end1:]

# Replace getRadioQueue
q2 = "    suspend fun getRadioQueue(videoId: String, limit: Int = 25): List<Song>"
idx2 = text.find(q2)
end2 = text.find("    // =========================================================================", idx2)
if idx2 != -1 and end2 != -1:
    new_queue = """    suspend fun getRadioQueue(videoId: String, limit: Int = 25): List<Song> {
        return withContext(Dispatchers.IO) {
            try {
                // To get radio/up-next queue natively, we use InnerTube's next API with a radio playlistId
                val endpoint = com.zionhuang.innertube.models.WatchEndpoint(videoId = videoId, playlistId = "RDAMVM$videoId")
                val result = com.zionhuang.innertube.YouTube.next(endpoint)
                
                val nextResult = result.getOrNull() ?: return@withContext emptyList()
                return@withContext nextResult.items.mapNotNull { it.toDomainSong() }.take(limit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get radio queue for $videoId: ${e.message}", e)
                emptyList()
            }
        }
    }

"""
    text = text[:idx2] + new_queue + text[end2:]

with open("app/src/main/java/com/theveloper/pixelplay/data/network/ytmusic/YTMusicRepository.kt", "w") as f:
    f.write(text)

print("done")
