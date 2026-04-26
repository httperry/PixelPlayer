with open("app/src/main/java/com/theveloper/pixelplay/data/network/ytmusic/YTMusicRepository.kt", "r") as f:
    lines = f.readlines()

output = []
skip = False
for i, line in enumerate(lines):
    if 'suspend fun getHome(): List<Song> {' in line:
        skip = True
        output.append('    suspend fun getHome(): List<Song> {\n')
        output.append('        return emptyList()\n')
        output.append('    }\n')
    elif skip and ('suspend fun createPlaylist' in line):
        skip = False
        output.append(line)
    elif not skip:
        output.append(line)

with open("app/src/main/java/com/theveloper/pixelplay/data/network/ytmusic/YTMusicRepository.kt", "w") as f:
    f.writelines(output)
