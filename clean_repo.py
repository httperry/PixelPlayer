with open("app/src/main/java/com/theveloper/pixelplay/data/network/ytmusic/YTMusicRepository.kt", "r") as f:
    text = f.read()

import re

# 1. Remove connectWithRetry entirely
start_cw = text.find('private fun connectWithRetry')
if start_cw != -1:
    brace_count = 0
    in_block = False
    for i in range(start_cw, len(text)):
        if text[i] == '{':
            if not in_block:
                in_block = True
            brace_count += 1
        elif text[i] == '}':
            brace_count -= 1
            if in_block and brace_count == 0:
                text = text[:start_cw] + text[i+1:]
                break

# 2. Remove executeWithRetry entirely
start_ew = text.find('private suspend fun <T> executeWithRetry')
if start_ew != -1:
    brace_count = 0
    in_block = False
    for i in range(start_ew, len(text)):
        if text[i] == '{':
            if not in_block:
                in_block = True
            brace_count += 1
        elif text[i] == '}':
            brace_count -= 1
            if in_block and brace_count == 0:
                text = text[:start_ew] + text[i+1:]
                break

# 3. Clean up the trailing garbage in getPlayerRawStream
text = re.sub(r'", e\)\n\s+null\n\s+\}\n\s+\}\n', '', text)
text = re.sub(r'\}", e\)', '}', text)
text = re.sub(r'\}, e\)\n\s+Result\.failure\(e\)', '}', text)
text = re.sub(r'\}, error\)\n\s+\}\n\n\s+null', '', text)

# Just clean up the double block endings and ensure proper brace counts. Let's do it manually for stream and artist.
