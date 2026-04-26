with open("app/src/main/java/com/theveloper/pixelplay/data/network/ytmusic/YTMusicRepository.kt", "r") as f:
    lines = f.readlines()

new_lines = []
skip = False

i = 0
while i < len(lines):
    line = lines[i]
    
    # 1. Remove connectWithRetry block
    if "private fun connectWithRetry" in line:
        brace_count = 0
        in_block = False
        while i < len(lines):
            line_str = lines[i]
            for ch in line_str:
                if ch == '{':
                    in_block = True
                    brace_count += 1
                elif ch == '}':
                    brace_count -= 1
            i += 1
            if in_block and brace_count == 0:
                break
        continue
    
    # 2. Remove executeWithRetry block
    if "private suspend fun <T> executeWithRetry" in line:
        brace_count = 0
        in_block = False
        while i < len(lines):
            line_str = lines[i]
            for ch in line_str:
                if ch == '{':
                    in_block = True
                    brace_count += 1
                elif ch == '}':
                    brace_count -= 1
            i += 1
            if in_block and brace_count == 0:
                break
        continue
    
    # 3. Remove garbage string literal remaining
    if '", e)' in line and 'null' in lines[i+1] and '}' in lines[i+2] and '}' in lines[i+3]:
        # skip 4 lines
        i += 4
        continue

    if line.strip() == '}", e)':
        new_lines.append('}\n')
        i += 1
        continue
        
    if line.strip() == '}, e)':
        # skip this and next 3 lines until the end of block
        new_lines.append('}\n')
        new_lines.append('}\n')
        while i < len(lines) and 'Result.failure(e)' in lines[i] or '}' in lines[0:1]:
           break
        # basically manually resolving this is tedious
        pass

    new_lines.append(line)
    i += 1
    
with open("app/src/main/java/com/theveloper/pixelplay/data/network/ytmusic/YTMusicRepository.kt", "w") as f:
    f.writelines(new_lines)
