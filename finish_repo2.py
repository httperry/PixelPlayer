with open("app/src/main/java/com/theveloper/pixelplay/data/network/ytmusic/YTMusicRepository.kt", "r") as f:
    text = f.read()

import re

# 8. Fix addToPlaylist
q = '    suspend fun addToPlaylist('
idx = text.find(q)
end = text.find('    suspend fun getPlaylistTracks(', idx)
if idx != -1 and end != -1:
    new_func = """    suspend fun addToPlaylist(playlistId: String, videoId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val result = com.zionhuang.innertube.YouTube.innerTube.addPlaylistItems(playlistId, listOf(videoId))
                result.getOrNull()?.status == "STATUS_SUCCEEDED"
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add video to playlist: ${e.message}", e)
                false
            }
        }
    }

"""
    text = text[:idx] + new_func + text[end:]

# 9. Fix getPlaylistTracks
q2 = '    suspend fun getPlaylistTracks('
idx2 = text.find(q2)
end2 = text.find('    suspend fun getRadioQueue(', idx2)
if idx2 != -1 and end2 != -1:
    new_func = """    suspend fun getPlaylistTracks(playlistId: String): List<Song> {
        return withContext(Dispatchers.IO) {
            try {
                val result = com.zionhuang.innertube.YouTube.playlist(playlistId).getOrNull() ?: return@withContext emptyList()
                result.songs.mapNotNull { if (it is com.zionhuang.innertube.models.SongItem) it.toDomainSong() else null }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get playlist tracks for $playlistId: ${e.message}", e)
                emptyList()
            }
        }
    }

"""
    text = text[:idx2] + new_func + text[end2:]

# 10. Fix getRadioQueue
q3 = '    suspend fun getRadioQueue('
idx3 = text.find(q3)
end3 = text.find('    suspend fun getPlayerRawStream(', idx3)
if idx3 != -1 and end3 != -1:
    new_func = """    suspend fun getRadioQueue(videoId: String, limit: Int = 100): List<Song> {
        return withContext(Dispatchers.IO) {
            try {
                val result = com.zionhuang.innertube.YouTube.next(videoId).getOrNull() ?: return@withContext emptyList()
                result.items.take(limit).mapNotNull { if (it is com.zionhuang.innertube.models.SongItem) it.toDomainSong() else null }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get radio queue for $videoId: ${e.message}", e)
                emptyList()
            }
        }
    }

"""
    text = text[:idx3] + new_func + text[end3:]

with open("app/src/main/java/com/theveloper/pixelplay/data/network/ytmusic/YTMusicRepository.kt", "w") as f:
    f.write(text)
print("done finish_repo2")
