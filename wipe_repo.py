import re
with open("broken.txt", "r") as f:
    text = f.read()

# I want everything from text up to '    fun getSearchSuggestions'
# cleanly removing all the garbage in between '    init {' and '    fun getSearchSuggestions'
idx1 = text.find('    init {')
idx2 = text.find('        // Fetch home feed on initialization')
idx3 = text.find('    fun getSearchSuggestions')

head = text[:idx1] + '    init {\n        // Just initialize\n    }\n\n'
tail = text[idx3:]

# Then clean the garbage block around Artist and Stream
tail = re.sub(r'\}, e\)\s*\n\s*null\s*\n\s*\}\s*\n\s*\}\s*\n\s*\}\s*\n\s*\}\s*\n', '}\n}\n}\n', tail)
tail = re.sub(r'", e\)\s*\n\s*null\s*\n\s*\}\s*\n\s*\}\s*\n\s*\}\s*\n\s*\}\s*\n', '}\n}\n}\n', tail)

lines = tail.split("\n")
new_lines = []
i = 0
while i < len(lines):
    if '", e)' in lines[i] and i+1 < len(lines) and 'null' in lines[i+1]:
        i += 4
        continue
    if '}, e)' in lines[i] or '}, error)' in lines[i]:
        i += 4
        continue
    new_lines.append(lines[i])
    i += 1
    
tail = "\n".join(new_lines)
final_text = head + tail

with open("app/src/main/java/com/theveloper/pixelplay/data/network/ytmusic/YTMusicRepository.kt", "w") as f:
    f.write(final_text)
print("done")
