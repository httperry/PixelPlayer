import re
with open("innertube/src/main/java/com/zionhuang/innertube/YouTube.kt", "r") as f:
    text = f.read()

pattern = re.compile(r"    suspend fun getHistory\(\): Result\<List\<com\.zionhuang\.innertube\.models\.SongItem\>\> = runCatching \{(.*?)\}", re.DOTALL)

new_getHistory = """    suspend fun getHistory(): Result<List<com.zionhuang.innertube.models.SongItem>> {
        return browse("FEmusic_history").map {
            it.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents?.flatMap { section ->
                section.musicShelfRenderer?.contents?.mapNotNull { item -> item.musicResponsiveListItemRenderer?.let(SongItem::from) } ?: emptyList()
            } ?: emptyList()
        }
    }"""
text = pattern.sub(new_getHistory, text)

with open("innertube/src/main/java/com/zionhuang/innertube/YouTube.kt", "w") as f:
    f.write(text)

