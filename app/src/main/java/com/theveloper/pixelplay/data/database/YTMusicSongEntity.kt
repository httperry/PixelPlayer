package com.theveloper.pixelplay.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local cache for YouTube Music songs.
 * Reduces dependency on WebSocket server for frequently accessed data.
 */
@Entity(
    tableName = "ytmusic_songs",
    indices = [
        androidx.room.Index(value = ["last_accessed"], name = "index_ytmusic_songs_last_accessed")
    ]
)
data class YTMusicSongEntity(
    @PrimaryKey
    @ColumnInfo(name = "video_id")
    val videoId: String,
    
    @ColumnInfo(name = "title")
    val title: String,
    
    @ColumnInfo(name = "artist")
    val artist: String,
    
    @ColumnInfo(name = "album")
    val album: String? = null,
    
    @ColumnInfo(name = "duration_seconds")
    val durationSeconds: Long,
    
    @ColumnInfo(name = "thumbnail_url")
    val thumbnailUrl: String,
    
    @ColumnInfo(name = "is_explicit", defaultValue = "0")
    val isExplicit: Boolean = false,
    
    @ColumnInfo(name = "year")
    val year: Int? = null,
    
    @ColumnInfo(name = "cached_at")
    val cachedAt: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "last_accessed")
    val lastAccessed: Long = System.currentTimeMillis()
)
