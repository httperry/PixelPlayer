with open("app/src/main/java/com/theveloper/pixelplay/data/network/ytmusic/YTMusicRepository.kt", "r") as f:
    text = f.read()

import re

# 1. Clean the python retry garbage out!
text = re.sub(r'    private fun connectWithRetry\(\) \{[\s\S]*?    private suspend fun <T> executeWithRetry\([\s\S]*?            return cacheProvider\?\.invoke\(\)\n        \}\n    \}', '', text)

# 2. Fix the getArtistProfile garbage
text = re.sub(r'\}, e\)\s*\n\s*null\s*\n\s*\}\s*\n\s*\}\s*\n\s*\}\s*\n\s*\}\s*\n', '}\n}\n}\n', text)
text = re.sub(r'", e\)\s*\n\s*null\s*\n\s*\}\s*\n\s*\}\s*\n\s*\}\s*\n\s*\}\s*\n', '}\n}\n}\n', text)

# Let me just cleanly wipe any unmatched string literal ", e) and the lines below it.
lines = text.split("\n")
new_lines = []
i = 0
while i < len(lines):
    if '", e)' in lines[i] and 'null' in lines[i+1]:
        i += 4
        continue
    if '}, e)' in lines[i]:
        i += 4
        continue
    if '}, error)' in lines[i]:
        i += 4
        continue
    new_lines.append(lines[i])
    i += 1

with open("app/src/main/java/com/theveloper/pixelplay/data/network/ytmusic/YTMusicRepository.kt", "w") as f:
    f.write("\n".join(new_lines))
