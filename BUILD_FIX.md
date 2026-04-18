# Build Fix - YTMusicDao Dependency

## Error
```
[ksp] ModuleProcessingStep was unable to process 'com.theveloper.pixelplay.di.AppModule' 
because 'error.NonExistentClass' could not be resolved.
```

## Root Cause
The `YTMusicRepository` constructor was updated to include `ytMusicDao` parameter, but the Hilt provider function `provideYTMusicRepository()` in `AppModule.kt` was not updated to pass this parameter.

## Fix Applied

### File: `app/src/main/java/com/theveloper/pixelplay/di/AppModule.kt`

**Before:**
```kotlin
@Singleton
fun provideYTMusicRepository(
    webSocketClient: YTMusicWebSocketClient,
    newPipeExtractor: NewPipeYTMusicExtractor
): YTMusicRepository {
    return YTMusicRepository(
        webSocketClient = webSocketClient,
        newPipeExtractor = newPipeExtractor
    )
}
```

**After:**
```kotlin
@Singleton
fun provideYTMusicRepository(
    webSocketClient: YTMusicWebSocketClient,
    newPipeExtractor: NewPipeYTMusicExtractor,
    ytMusicDao: YTMusicDao  // ADDED
): YTMusicRepository {
    return YTMusicRepository(
        webSocketClient = webSocketClient,
        newPipeExtractor = newPipeExtractor,
        ytMusicDao = ytMusicDao  // ADDED
    )
}
```

## Status
✅ **FIXED** - Build should now succeed

The `ytMusicDao` parameter is now properly injected by Hilt using the existing `provideYTMusicDao()` function that was already defined in `AppModule.kt`.
