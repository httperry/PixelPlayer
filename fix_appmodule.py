with open("app/src/main/java/com/theveloper/pixelplay/di/AppModule.kt", "r") as f:
    lines = f.readlines()

new_lines = []
skip = False
for line in lines:
    if "fun provideYTMusicWebSocketClient" in line:
        skip = True
    if skip and "}" in line:
        skip = False
        continue
    if not skip:
        new_lines.append(line)

with open("app/src/main/java/com/theveloper/pixelplay/di/AppModule.kt", "w") as f:
    f.writelines(new_lines)
print("done")
