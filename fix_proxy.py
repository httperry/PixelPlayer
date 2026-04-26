with open("app/src/main/java/com/theveloper/pixelplay/data/network/ytmusic/YTMusicStreamProxy.kt", "r") as f:
    text = f.read()

import re

# Remove YTMusicWebSocketClient dependency
text = re.sub(r' +private val webSocketClient: YTMusicWebSocketClient,?\n', '', text)

text_new = text

# Change resolveStreamUrl to use InnerTube player
q = "    private suspend fun resolveStreamUrl(id: String): String? {"
idx = text_new.find(q)
end = text_new.find("}", text_new.find("catch", idx)) + 1

if idx != -1 and end != -1:
    new_func = """    private suspend fun resolveStreamUrl(id: String): String? {
        // Just bypass this entirely by using inner tube natively
        try {
            val playerResponse = com.zionhuang.innertube.YouTube.player(id).getOrNull()
            val formats = playerResponse?.streamingData?.adaptiveFormats ?: emptyList()
            
            // Try to find the highest bitrate audio format
            val audioFormat = formats
                .filter { it.isAudio || it.mimeType.startsWith("audio/") }
                .maxByOrNull { it.bitrate }
                
            val streamUrl = audioFormat?.url ?: formats.firstOrNull { it.url != null }?.url
            
            if (streamUrl != null) {
                // Cache it
                streamUrlCache.put(id, streamUrl)
                return streamUrl
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed resolving stream URL for InnerTube sync", e)
        }
        return null
"""
    text_new = text_new[:idx] + new_func + text_new[end:]

with open("app/src/main/java/com/theveloper/pixelplay/data/network/ytmusic/YTMusicStreamProxy.kt", "w") as f:
    f.write(text_new)
print("Done proxy")
