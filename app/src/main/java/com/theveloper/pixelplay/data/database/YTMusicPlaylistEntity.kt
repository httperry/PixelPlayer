package com.theveloper.pixelplay.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local cache for YouTube Music playlists.
 * Allows offline access to playlist metadata.
 */
@Entity(tableName = "ytmusic_playlists")
data class YTMusicPlaylistEntity(
    @PrimaryKey
    @ColumnInfo(name = "playlist_id")
    val playlistId: String,
    
    @ColumnInfo(name = "title")
    val title: String,
    
    @ColumnInfo(name = "description")
    val description: String? = null,
    
    @ColumnInfo(name = "thumbnail_url")
    val thumbnailUrl: String? = null,
    
    @ColumnInfo(name = "track_count")
    val trackCount: Int = 0,
    
    @ColumnInfo(name = "author")
    val author: String? = null,
    
    @ColumnInfo(name = "is_editable")
    val isEditable: Boolean = false,
    
    @ColumnInfo(name = "cached_at")
    val cachedAt: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "last_synced")
    val lastSynced: Long = System.currentTimeMillis()
)

/**
 * Junction table for playlist-song relationships.
 */
@Entity(
    tableName = "ytmusic_playlist_songs",
    primaryKeys = ["playlist_id", "video_id", "position"]
)
data class YTMusicPlaylistSongEntity(
    @ColumnInfo(name = "playlist_id")
    val playlistId: String,
    
    @ColumnInfo(name = "video_id")
    val videoId: String,
    
    @ColumnInfo(name = "position")
    val position: Int,
    
    @ColumnInfo(name = "added_at")
    val addedAt: Long = System.currentTimeMillis()
)
