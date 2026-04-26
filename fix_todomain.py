import re
with open("app/src/main/java/com/theveloper/pixelplay/data/network/ytmusic/YTMusicRepository.kt", "r") as f:
    text = f.read()

old_func = """private fun com.zionhuang.innertube.models.SongItem.toDomainSong(): Song {
    return Song(
        id = this.id,
        title = this.title,
        artist = this.artists.firstOrNull()?.name ?: "Unknown",
        artistId = -1L,
        artists = this.artists.map { com.theveloper.pixelplay.data.model.ArtistRef(id = -1L, name = it.name, isPrimary = true) },
        album = this.album?.name ?: "",
        albumId = -1L,
        path = "",
        contentUriString = "ytm://${this.id}",
        albumArtUriString = this.thumbnail,
        duration = (this.duration ?: 0) * 1000L,
        mimeType = "audio/webm",
        bitrate = null,
        sampleRate = null,
        ytmusicId = this.id
    )
}"""

new_func = """private fun com.zionhuang.innertube.models.SongItem.toDomainSong(): Song {
    val primaryArtistId = this.artists.firstOrNull()?.id?.hashCode()?.toLong() ?: -1L
    return Song(
        id = this.id,
        title = this.title,
        artist = this.artists.firstOrNull()?.name ?: "Unknown",
        artistId = primaryArtistId,
        artists = this.artists.map { it ->
            com.theveloper.pixelplay.data.model.ArtistRef(
                id = it.id?.hashCode()?.toLong() ?: -1L,
                name = it.name,
                isPrimary = true
            )
        },
        album = this.album?.name ?: "",
        albumId = this.album?.id?.hashCode()?.toLong() ?: -1L,
        path = "",
        contentUriString = "ytm://${this.id}",
        albumArtUriString = this.thumbnail,
        duration = (this.duration ?: 0) * 1000L,
        mimeType = "audio/webm",
        bitrate = null,
        sampleRate = null,
        ytmusicId = this.id
    )
}"""

if old_func in text:
    text = text.replace(old_func, new_func)
    with open("app/src/main/java/com/theveloper/pixelplay/data/network/ytmusic/YTMusicRepository.kt", "w") as f:
        f.write(text)
    print("Done")
else:
    print("Fail: Not found")
