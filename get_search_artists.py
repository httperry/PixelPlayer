with open("app/src/main/java/com/theveloper/pixelplay/data/network/ytmusic/YTMusicRepository.kt", "r") as f:
    text = f.read()
q = "    suspend fun searchArtists(query: String)"
idx = text.find(q)
end = text.find("    suspend fun searchAlbums(query: String)", idx)
if end == -1: end = text.find("    fun getLibrarySongsFlow", idx)
print(text[idx:end])
