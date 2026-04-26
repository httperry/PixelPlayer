import re
with open("innertube/src/main/java/com/zionhuang/innertube/YouTube.kt", "r") as f:
    text = f.read()

pattern = re.compile(r"    suspend fun getHistory\(\).*?    \}", re.DOTALL)

valid_getHistory = """    suspend fun getHistory(): Result<List<com.zionhuang.innertube.models.SongItem>> = runCatching {
        val response = browse("FEmusic_history").getOrThrow()
        response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents?.flatMap { 
            it.musicShelfRenderer?.contents?.mapNotNull { item -> item.musicResponsiveListItemRenderer?.let(com.zionhuang.innertube.models.SongItem.Companion::from) } ?: emptyList()
        } ?: emptyList()
    }"""
text = pattern.sub(valid_getHistory, text)

with open("innertube/src/main/java/com/zionhuang/innertube/YouTube.kt", "w") as f:
    f.write(text)

