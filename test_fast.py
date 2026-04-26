with open("app/src/main/java/com/theveloper/pixelplay/data/network/ytmusic/YTMusicRepository.kt", "r") as f:
    text = f.read()

import re

# I will write a massive python clean up script because doing it using replace_string_in_file on such a large scale with changing offsets can be error prone, but I will use replace_string_in_file safely if possible.
