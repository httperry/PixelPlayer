with open("app/src/main/java/com/theveloper/pixelplay/data/network/ytmusic/YTMusicRepository.kt", "r") as f:
    text = f.read()

import re

# 1. Clean the python class dependencies completely
text = re.sub(r' +private val webSocketClient: YTMusicWebSocketClient,?\n', '', text)
text = re.sub(r' +private val pythonClient: YTMusicPythonClient,?\n', '', text)

# 2. Remove init block if left
init_start = text.find("    init {")
if init_start != -1:
    brace_count = 0
    in_block = False
    init_end = init_start
    for i in range(init_start, len(text)):
        if text[i] == '{':
            if not in_block:
                in_block = True
            brace_count += 1
        elif text[i] == '}':
            brace_count -= 1
            if in_block and brace_count == 0:
                init_end = i + 1
                break
    text = text[:init_start] + text[init_end:]

# 3. Remove connectWithRetry / executeWithRetry if left (just in case they returned)
cw_start = text.find("    private fun connectWithRetry()")
if cw_start != -1:
    brace_count = 0
    in_block = False
    cw_end = cw_start
    for i in range(cw_start, len(text)):
        if text[i] == '{':
            in_block = True
            brace_count += 1
        elif text[i] == '}':
            brace_count -= 1
            if in_block and brace_count == 0:
                cw_end = i + 1
                break
    text = text[:cw_start] + text[cw_end:]

ew_start = text.find("    private suspend fun <T> executeWithRetry(")
if ew_start != -1:
    brace_count = 0
    in_block = False
    ew_end = ew_start
    for i in range(ew_start, len(text)):
        if text[i] == '{':
            in_block = True
            brace_count += 1
        elif text[i] == '}':
            brace_count -= 1
            if in_block and brace_count == 0:
                ew_end = i + 1
                break
    text = text[:ew_start] + text[ew_end:]

with open("app/src/main/java/com/theveloper/pixelplay/data/network/ytmusic/YTMusicRepository.kt", "w") as f:
    f.write(text)
print("cleanup done")
