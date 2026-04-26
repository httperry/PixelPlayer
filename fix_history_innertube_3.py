import re
with open("innertube/src/main/java/com/zionhuang/innertube/YouTube.kt", "r") as f:
    text = f.read()

pattern = re.compile(r"    suspend fun getHistory\(\): Result.*?\}\n    \}", re.DOTALL)

original_getHistory = """    suspend fun getHistory(): Result<List<com.zionhuang.innertube.models.SongItem>> = runCatching {
        val response = innerTube.browse(WEB_REMIX, "FEmusic_history", setLogin = true).body<com.zionhuang.innertube.models.response.BrowseResponse>()
        response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents?.mapNotNull { it.musicShelfRenderer }?.flatMap { it.contents ?: emptyList() }?.mapNotNull { com.zionhuang.innertube.pages.PlaylistPage.fromMusicResponsiveListItemRenderer(it.musicResponsiveListItemRenderer) } ?: emptyList()
    }"""
text = pattern.sub(original_getHistory, text)

with open("innertube/src/main/java/com/zionhuang/innertube/YouTube.kt", "w") as f:
    f.write(text)

