with open("app/src/main/java/com/theveloper/pixelplay/data/network/ytmusic/YTMusicRepository.kt", "r") as f:
    text = f.read()

import re

# Remove the leftover comments
text = re.sub(r'    // [\s\S]*?private suspend fun <T> executeWithRetry\(', '', text)
text = re.sub(r'    /\*\*[\s\S]*?     \* Execute a WebSocket request[\s\S]*?     \*/\n', '', text)

with open("app/src/main/java/com/theveloper/pixelplay/data/network/ytmusic/YTMusicRepository.kt", "w") as f:
    f.write(text)
