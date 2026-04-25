package com.theveloper.pixelplay.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local cache for YouTube Music playlists.
 * Allows offline access to playlist metadata.
 */
@Entity(
    tableName = "ytmusic_playlists",
    indices = [
        androidx.room.Index(value = ["last_synced"], name = "index_ytmusic_playlists_last_synced")
    ]
)
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
    
    @ColumnInfo(name = "track_count", defaultValue = "0")
    val trackCount: Int = 0,
    
    @ColumnInfo(name = "author")
    val author: String? = null,
    
    @ColumnInfo(name = "is_editable", defaultValue = "0")
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
    primaryKeys = ["playlist_id", "video_id", "position"],
    indices = [
        androidx.room.Index(value = ["playlist_id"], name = "index_ytmusic_playlist_songs_playlist_id"),
        androidx.room.Index(value = ["video_id"], name = "index_ytmusic_playlist_songs_video_id")
    ]
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
