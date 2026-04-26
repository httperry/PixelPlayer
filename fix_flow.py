with open("app/src/main/java/com/theveloper/pixelplay/data/network/ytmusic/YTMusicRepository.kt", "r") as f:
    text = f.read()

idx = text.find('    fun getLibrarySongsFlow(pageSize: Int = 50): Flow<List<Song>> = flow {')
end = text.find('    suspend fun getUserPlaylists()', idx)

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

text = text[:idx] + new_func + text[end:]

with open("app/src/main/java/com/theveloper/pixelplay/data/network/ytmusic/YTMusicRepository.kt", "w") as f:
    f.write(text)
print("done")
