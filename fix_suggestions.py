import re
with open("app/src/main/java/com/theveloper/pixelplay/data/network/ytmusic/YTMusicRepository.kt", "r") as f:
    text = f.read()

pattern = re.compile(r"    suspend fun getSearchSuggestions\(query: String\): List<String> \{.*?    \}", re.DOTALL)

new_func = """    suspend fun getSearchSuggestions(query: String): List<String> {
        return withContext(Dispatchers.IO) {
            try {
                if (query.isBlank()) return@withContext emptyList()
                val result = com.zionhuang.innertube.YouTube.searchSuggestions(query)
                result.getOrNull()?.queries ?: emptyList()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get search suggestions: ${e.message}")
                emptyList()
            }
        }
    }"""

text = pattern.sub(new_func, text)
with open("app/src/main/java/com/theveloper/pixelplay/data/network/ytmusic/YTMusicRepository.kt", "w") as f:
    f.write(text)
