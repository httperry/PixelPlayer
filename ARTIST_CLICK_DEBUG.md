# Artist Click in Player - Debug Investigation

## Status: ✅ ALREADY IMPLEMENTED (Added Debug Logging)

## Summary

The artist click functionality in the full player **is already fully implemented**. The code flow is complete from UI to navigation. I've added debug logging to help diagnose why it might not be working for the user.

## Complete Implementation Flow

### 1. UI Layer (PlayerSongInfo)
**File**: `app/src/main/java/com/theveloper/pixelplay/presentation/components/player/FullPlayerContent.kt` (line ~2150)

- Artist text has `combinedClickable` modifier
- **onClick**: Calls `onClickArtist()` callback
- **onLongClick**: Directly calls `playerViewModel.triggerArtistNavigationFromPlayer(resolvedArtistId)`
- ✅ **Added logging**: Now logs when artist text is clicked/long-clicked

### 2. Metadata Section (FullPlayerSongMetadataSection)
**File**: `app/src/main/java/com/theveloper/pixelplay/presentation/components/player/FullPlayerContent.kt` (line ~1275)

- Receives `onArtistClick: () -> Unit` parameter
- Passes it to `SongMetadataDisplaySection`
- Which passes it to `PlayerSongInfo`

### 3. Full Player Content (FullPlayerContent)
**File**: `app/src/main/java/com/theveloper/pixelplay/presentation/components/player/FullPlayerContent.kt` (line ~400)

```kotlin
val onSongMetadataArtistClick = {
    val resolvedArtistId = currentSongArtists.firstOrNull()?.id ?: song.artistId
    if (currentSongArtists.size > 1) {
        showArtistPicker = true  // Show picker for multiple artists
    } else {
        playerViewModel.triggerArtistNavigationFromPlayer(resolvedArtistId)
    }
}
```

- ✅ **Added logging**: Now logs artist count, resolved ID, and action taken

### 4. ViewModel (PlayerViewModel)
**File**: `app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt` (line ~2310)

```kotlin
fun triggerArtistNavigationFromPlayer(artistId: Long) {
    if (artistId <= 0) {
        Log.d("ArtistDebug", "triggerArtistNavigationFromPlayer ignored invalid artistId=$artistId")
        return
    }
    // ... collapses player sheet
    _artistNavigationRequests.emit(artistId)
}
```

- Already has logging for invalid artist IDs
- Emits to `_artistNavigationRequests` flow

### 5. Navigation Effect (PlayerArtistNavigationEffect)
**File**: `app/src/main/java/com/theveloper/pixelplay/presentation/components/scoped/PlayerArtistNavigationEffect.kt`

```kotlin
LaunchedEffect(navController) {
    playerViewModel.artistNavigationRequests.collectLatest { artistId ->
        sheetMotionController.snapCollapsed(latestSheetCollapsedTargetY)
        playerViewModel.collapsePlayerSheet()
        navController.navigateSafely(Screen.ArtistDetail.createRoute(artistId))
    }
}
```

- Listens to `artistNavigationRequests` flow
- Collapses player sheet
- Navigates to `Screen.ArtistDetail`

### 6. Integration (UnifiedPlayerSheetV2)
**File**: `app/src/main/java/com/theveloper/pixelplay/presentation/components/UnifiedPlayerSheetV2.kt` (line ~269)

```kotlin
PlayerArtistNavigationEffect(
    navController = navController,
    sheetCollapsedTargetY = sheetCollapsedTargetY,
    sheetMotionController = sheetMotionController,
    playerViewModel = playerViewModel
)
```

- Effect is properly registered in the player sheet

## Bonus: Artist Picker for Multiple Artists

**File**: `app/src/main/java/com/theveloper/pixelplay/presentation/components/player/PlayerArtistPickerBottomSheet.kt`

When a song has multiple artists, a bottom sheet picker is shown allowing the user to select which artist to navigate to.

## Debug Logs Added

### In `onSongMetadataArtistClick`:
```
ArtistClick: currentSongArtists.size=X, resolvedArtistId=Y, song.artistId=Z
ArtistClick: Showing artist picker with X artists
ArtistClick: Triggering navigation to artist Y
```

### In `PlayerSongInfo` onClick:
```
ArtistClick: Artist text clicked: [artist name] (artistId=X)
ArtistClick: Already navigating, ignoring click
```

### In `PlayerSongInfo` onLongClick:
```
ArtistClick: Artist text long-clicked: [artist name] (resolvedArtistId=X)
ArtistClick: Already navigating, ignoring long click
```

### Existing logs in `PlayerViewModel`:
```
ArtistDebug: triggerArtistNavigationFromPlayer ignored invalid artistId=X
ArtistDebug: triggerArtistNavigationFromPlayer ignored; navigation already in progress for artistId=X
ArtistDebug: triggerArtistNavigationFromPlayer: artistId=X, songId=Y, title=Z
```

## How to Test

1. Build and run the app
2. Play a song
3. Expand the full player
4. Click on the artist name below the song title
5. Check logcat for "ArtistClick" and "ArtistDebug" tags

## Possible Issues to Check

If artist click still doesn't work after this, check:

1. **Invalid Artist ID**: Artist ID might be 0 or negative (check logs)
2. **Artist Data Not Loaded**: `currentSongArtists` might be empty (check logs)
3. **Navigation Blocked**: Check if there are any navigation guards or back stack issues
4. **Touch Target**: The artist text might be too small or overlapped by another element
5. **Sheet State**: Player sheet might not be fully expanded (check `expansionFractionProvider()`)

## Files Modified

1. `app/src/main/java/com/theveloper/pixelplay/presentation/components/player/FullPlayerContent.kt`
   - Added debug logging to `onSongMetadataArtistClick`
   - Added debug logging to `PlayerSongInfo` onClick and onLongClick

## Next Steps

Run the app and check the logs. The logs will tell us exactly where the flow is breaking:
- If no "Artist text clicked" log → Touch target issue
- If "Artist text clicked" but no "Triggering navigation" → Callback not wired
- If "Triggering navigation" but no ViewModel log → ViewModel method not called
- If ViewModel log but no navigation → Navigation effect not working
- If "invalid artistId" log → Artist data issue
