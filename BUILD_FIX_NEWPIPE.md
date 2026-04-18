# NewPipe Dependency Resolution Fix

## Problem:
```
Could not find com.github.teamnewpipe.NewPipeExtractor:extractor:v0.24.5
```

Gradle is looking for NewPipe with the wrong group name.

## Root Cause:
The dependency is correctly declared as `com.github.TeamNewPipe:NewPipeExtractor:v0.24.5` but Gradle's configuration cache has the wrong resolution.

## Solution:

### Option 1: Clear Configuration Cache (Recommended)
```bash
# Delete configuration cache
rm -rf .gradle/configuration-cache

# Or on Windows PowerShell
Remove-Item -Recurse -Force .gradle\configuration-cache

# Then build
./gradlew --no-configuration-cache assembleDebug
```

### Option 2: Disable Configuration Cache Temporarily
```bash
./gradlew --no-configuration-cache assembleDebug
```

### Option 3: Clean and Rebuild
```bash
./gradlew clean --no-configuration-cache
./gradlew assembleDebug --no-configuration-cache
```

## Verification:

The dependency is correctly declared in `app/build.gradle.kts`:
```kotlin
implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.24.5")
```

JitPack repository is correctly configured in `settings.gradle.kts`:
```kotlin
repositories {
    google()
    mavenCentral()
    maven("https://jitpack.io")
}
```

## Why This Happens:

Gradle's configuration cache stores resolved dependencies. When the dependency was first added, it may have been resolved incorrectly or the cache became corrupted. Clearing the cache forces Gradle to re-resolve all dependencies.

## Alternative: Skip Python for Now

If you want to build without Python support temporarily:

1. Comment out Chaquopy plugin:
```kotlin
// apply(plugin = "com.chaquo.python")
```

2. Comment out Python configuration:
```kotlin
// python {
//     version = "3.11"
//     pip {
//         install("ytmusicapi==1.8.1")
//         install("websockets==12.0")
//         install("cryptography==42.0.0")
//     }
// }
```

3. Comment out Python service start in MainActivity:
```kotlin
// YTMusicPythonService.start(this)
```

This will let you build and test the NewPipe integration first, then add Python support later.
