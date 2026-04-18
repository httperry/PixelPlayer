package com.theveloper.pixelplay.di

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import javax.inject.Singleton
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import androidx.media3.exoplayer.offline.DownloadManager
import java.util.concurrent.Executors
import androidx.media3.datasource.DefaultHttpDataSource

@Module
@InstallIn(SingletonComponent::class)
object CacheModule {

    @Provides
    @Singleton
    @UnstableApi
    fun provideDatabaseProvider(@ApplicationContext context: Context): DatabaseProvider {
        return StandaloneDatabaseProvider(context)
    }

    @Provides
    @Singleton
    @UnstableApi
    fun provideExoPlayerCache(
        @ApplicationContext context: Context,
        databaseProvider: DatabaseProvider,
        userPreferencesRepository: UserPreferencesRepository
    ): Cache {
        val cacheDir = File(context.cacheDir, "yt_media_cache")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }

        // We read the initial size synchronously during injection.
        // It's perfectly acceptable for initialization, dynamic updates can clear cache or restart it.
        val cacheSizeMb = runBlocking { userPreferencesRepository.ytmCacheSizeMbFlow.first() }
        val maxBytes = cacheSizeMb * 1024L * 1024L

        val evictor = LeastRecentlyUsedCacheEvictor(maxBytes)
        return SimpleCache(cacheDir, evictor, databaseProvider)
    }

    @Provides
    @Singleton
    @UnstableApi
    fun provideDownloadManager(
        @ApplicationContext context: Context,
        databaseProvider: DatabaseProvider,
        cache: Cache
    ): DownloadManager {
        val dataSourceFactory = DefaultHttpDataSource.Factory()
        val executor = Executors.newFixedThreadPool(4)
        return DownloadManager(
            context,
            databaseProvider,
            cache,
            dataSourceFactory,
            executor
        )
    }
}
