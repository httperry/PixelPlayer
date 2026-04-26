with open("app/src/main/java/com/theveloper/pixelplay/presentation/components/player/FullPlayerContent.kt", "r") as f:
    text = f.read()

# Add missing imports if needed
if "import coil.compose.AsyncImage" not in text:
    # insert at top
    text = text.replace("import androidx.compose.foundation.layout.Box", "import androidx.compose.foundation.layout.Box\nimport coil.compose.AsyncImage\nimport androidx.compose.ui.layout.ContentScale\nimport androidx.compose.foundation.border\nimport androidx.compose.ui.draw.clip", 1)

old_player_song_info = """        AutoScrollingTextOnDemand(
            text = artist,
            style = artistStyle,
            gradientEdgeColor = gradientEdgeColor,
            expansionFractionProvider = expansionFractionProvider,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {"""

new_player_song_info = """        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        Timber.d("ArtistClick: Artist text clicked: $artist (artistId=$artistId)")
                        if (isNavigatingToArtist) {
                            Timber.d("ArtistClick: Already navigating, ignoring click")
                            return@combinedClickable
                        }
                        coroutineScope.launch {
                            isNavigatingToArtist = true
                            try {
                                onClickArtist()
                            } finally {
                                isNavigatingToArtist = false
                            }
                        }
                    },
                    onLongClick = {
                        Timber.d("ArtistClick: Artist text long-clicked: $artist (resolvedArtistId=$resolvedArtistId)")
                        if (isNavigatingToArtist) {
                            Timber.d("ArtistClick: Already navigating, ignoring long click")
                            return@combinedClickable
                        }
                        coroutineScope.launch {
                            isNavigatingToArtist = true
                            try {
                                playerViewModel.triggerArtistNavigationFromPlayer(resolvedArtistId)
                            } finally {
                                isNavigatingToArtist = false
                            }
                        }
                    }
                )
        ) {
            val validArtists = artists.filter { !it.imageUrl.isNullOrBlank() }
            if (validArtists.isNotEmpty()) {
                Box(modifier = Modifier.padding(end = 8.dp)) {
                    validArtists.take(3).reversed().forEachIndexed { index, artistObj -> // stack order reversed
                        val trueIndex = minOf(validArtists.size - 1, 2) - index
                        AsyncImage(
                            model = artistObj.imageUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .padding(start = (trueIndex * 16).dp)
                                .size(28.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape)
                        )
                    }
                }
            }

            AutoScrollingTextOnDemand(
                text = artist,
                style = artistStyle,
                gradientEdgeColor = gradientEdgeColor,
                expansionFractionProvider = expansionFractionProvider,
                modifier = Modifier.weight(1f)
            )
        }
        
        // Remove the original combinedClickable from here down (we handle it below via string repl)"""

# I should use regex to replace the entire old `AutoScrollingTextOnDemand` block properly to avoid syntax errors.
import re
pattern = re.compile(r"        AutoScrollingTextOnDemand\(\n            text = artist,.*?isNavigatingToArtist = false\n                            \}\n                        \}\n                    \}\n                \)\n", re.DOTALL)
text = pattern.sub(new_player_song_info, text)

with open("app/src/main/java/com/theveloper/pixelplay/presentation/components/player/FullPlayerContent.kt", "w") as f:
    f.write(text)
print("Updated player song info.")
