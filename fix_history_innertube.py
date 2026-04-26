with open("innertube/src/main/java/com/zionhuang/innertube/YouTube.kt", "r") as f:
    text = f.read()

old_getHistory = """    suspend fun getHistory(): Result<List<SongItem>> {
        return innerTube.browse("FEmusic_history").map {
            it.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents?.flatMap {
                it.musicShelfRenderer?.contents?.mapNotNull { it.musicResponsiveListItemRenderer?.let(SongItem::from) } ?: emptyList()
            } ?: emptyList()
        }
    }"""

new_getHistory = """    suspend fun getHistory(): Result<List<SongItem>> {
        return browse("FEmusic_history").map {
            it.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents?.flatMap { section ->
                section.musicShelfRenderer?.contents?.mapNotNull { item -> item.musicResponsiveListItemRenderer?.let(SongItem::from) } ?: emptyList()
            } ?: emptyList()
        }
    }"""
text = text.replace(old_getHistory, new_getHistory)
with open("innertube/src/main/java/com/zionhuang/innertube/YouTube.kt", "w") as f:
    f.write(text)

