import re

with open("app/src/main/java/com/theveloper/pixelplay/data/network/ytmusic/YTMusicRepository.kt", "r") as f:
    content = f.read()

# 1. PagingData and Pager were missing imports
if 'import androidx.paging.PagingData' not in content:
    content = content.replace('import androidx.paging.PagingConfig', 'import androidx.paging.PagingConfig\nimport androidx.paging.PagingData\nimport androidx.paging.Pager')

# 2. Add toDomainSong extension function at the bottom
extension_func = """
private fun com.zionhuang.innertube.models.SongItem.toDomainSong(): com.theveloper.pixelplay.data.model.Song {
    val artistName = this.authors?.firstOrNull()?.name ?: "Unknown Artist"
    return com.theveloper.pixelplay.data.model.Song(
        id = "ytm_${this.id}",
        title = this.title,
        artist = artistName,
        artistId = 0L,
        album = this.album?.name ?: "Unknown Album",
        albumId = 0L,
        path = "",
        contentUriString = "ytm://${this.id}",
        albumArtUriString = this.thumbnail,
        duration = (this.duration?.toLong() ?: 0L) * 1000L,
        mimeType = "audio/mpeg",
        bitrate = 128000,
        sampleRate = 44100,
        ytmusicId = this.id
    )
}
"""
if 'private fun com.zionhuang.innertube.models.SongItem.toDomainSong()' not in content:
    content = content + "\n" + extension_func

with open("app/src/main/java/com/theveloper/pixelplay/data/network/ytmusic/YTMusicRepository.kt", "w") as f:
    f.write(content)

