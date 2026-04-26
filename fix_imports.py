import re

files_to_check = [
    "app/src/main/java/com/theveloper/pixelplay/MainActivity.kt",
    "app/src/main/java/com/theveloper/pixelplay/presentation/ytmusic/auth/YTLoginActivity.kt",
    "app/src/main/java/com/theveloper/pixelplay/data/network/ytmusic/YTMusicRepository.kt"
]

for fp in files_to_check:
    with open(fp, "r") as f:
        content = f.read()
    content = re.sub(r'import com\.theveloper\.pixelplay\.data\.service\.YTMusicPythonService\n', '', content)
    with open(fp, "w") as f:
        f.write(content)

with open("app/src/main/java/com/theveloper/pixelplay/data/network/ytmusic/YTMusicRepository.kt", "r") as f:
    content = f.read()

if 'import com.zionhuang.innertube.pages.SearchResult' not in content:
    content = content.replace('import javax.inject.Singleton', 'import javax.inject.Singleton\nimport com.zionhuang.innertube.pages.SearchResult')

with open("app/src/main/java/com/theveloper/pixelplay/data/network/ytmusic/YTMusicRepository.kt", "w") as f:
    f.write(content)
