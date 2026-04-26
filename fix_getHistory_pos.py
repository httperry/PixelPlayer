import re

with open("innertube/src/main/java/com/zionhuang/innertube/YouTube.kt", "r") as f:
    text = f.read()

# find position of DEFAULT_VISITOR_DATA = "CgtsZG1ySnZiQWtSbyiMjuGSBg%3D%3D"
target_str = 'const val DEFAULT_VISITOR_DATA = "CgtsZG1ySnZiQWtSbyiMjuGSBg%3D%3D"\n}'

if target_str in text:
    idx = text.index(target_str)
    # truncate everything from idx onwards except our new correct string
    valid_text = text[:idx]
    
    new_getHistory_block = """    const val DEFAULT_VISITOR_DATA = "CgtsZG1ySnZiQWtSbyiMjuGSBg%3D%3D"

    suspend fun getHistory(): Result<List<com.zionhuang.innertube.models.SongItem>> = runCatching {
        val response = innerTube.browse(WEB_REMIX, "FEmusic_history", setLogin = true).body<com.zionhuang.innertube.models.response.BrowseResponse>()
        response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents?.mapNotNull { it.musicShelfRenderer }?.flatMap { it.contents ?: emptyList() }?.mapNotNull { com.zionhuang.innertube.pages.PlaylistPage.fromMusicResponsiveListItemRenderer(it.musicResponsiveListItemRenderer) } ?: emptyList()
    }
}
"""
    final_text = valid_text + new_getHistory_block
    with open("innertube/src/main/java/com/zionhuang/innertube/YouTube.kt", "w") as f:
        f.write(final_text)
    print("Successfully replaced.")
else:
    print("Could not find Target string.")

