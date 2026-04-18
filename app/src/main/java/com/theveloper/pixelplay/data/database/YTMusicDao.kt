package com.theveloper.pixelplay.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * DAO for YouTube Music local cache.
 * Provides offline access and reduces WebSocket dependency.
 */
@Dao
interface YTMusicDao {
    
    // ========== Songs ==========
    
    @Query("SELECT * FROM ytmusic_songs WHERE video_id = :videoId")
    suspend fun getSong(videoId: String): YTMusicSongEntity?
    
    @Query("SELECT * FROM ytmusic_songs ORDER BY last_accessed DESC LIMIT :limit")
    fun getRecentSongs(limit: Int = 50): Flow<List<YTMusicSongEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: YTMusicSongEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<YTMusicSongEntity>)
    
    @Query("UPDATE ytmusic_songs SET last_accessed = :timestamp WHERE video_id = :videoId")
    suspend fun updateLastAccessed(videoId: String, timestamp: Long = System.currentTimeMillis())
    
    @Query("DELETE FROM ytmusic_songs WHERE cached_at < :olderThan")
    suspend fun deleteOldSongs(olderThan: Long)
    
    @Query("SELECT COUNT(*) FROM ytmusic_songs")
    suspend fun getSongCount(): Int
    
    // ========== Playlists ==========
    
    @Query("SELECT * FROM ytmusic_playlists ORDER BY last_synced DESC")
    fun getAllPlaylists(): Flow<List<YTMusicPlaylistEntity>>
    
    @Query("SELECT * FROM ytmusic_playlists WHERE playlist_id = :playlistId")
    suspend fun getPlaylist(playlistId: String): YTMusicPlaylistEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: YTMusicPlaylistEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylists(playlists: List<YTMusicPlaylistEntity>)
    
    @Query("UPDATE ytmusic_playlists SET last_synced = :timestamp WHERE playlist_id = :playlistId")
    suspend fun updatePlaylistSyncTime(playlistId: String, timestamp: Long = System.currentTimeMillis())
    
    @Query("DELETE FROM ytmusic_playlists WHERE playlist_id = :playlistId")
    suspend fun deletePlaylist(playlistId: String)
    
    @Query("SELECT COUNT(*) FROM ytmusic_playlists")
    suspend fun getPlaylistCount(): Int
    
    // ========== Playlist Songs ==========
    
    @Query("""
        SELECT s.* FROM ytmusic_songs s
        INNER JOIN ytmusic_playlist_songs ps ON s.video_id = ps.video_id
        WHERE ps.playlist_id = :playlistId
        ORDER BY ps.position ASC
    """)
    fun getPlaylistSongs(playlistId: String): Flow<List<YTMusicSongEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistSong(playlistSong: YTMusicPlaylistSongEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistSongs(playlistSongs: List<YTMusicPlaylistSongEntity>)
    
    @Query("DELETE FROM ytmusic_playlist_songs WHERE playlist_id = :playlistId")
    suspend fun deletePlaylistSongs(playlistId: String)
    
    @Query("DELETE FROM ytmusic_playlist_songs WHERE playlist_id = :playlistId AND video_id = :videoId")
    suspend fun removeFromPlaylist(playlistId: String, videoId: String)
    
    // ========== Cache Management ==========
    
    @Query("SELECT last_synced FROM ytmusic_playlists ORDER BY last_synced DESC LIMIT 1")
    suspend fun getLastSyncTime(): Long?
    
    @Transaction
    suspend fun clearAllCache() {
        clearAllPlaylists()
        clearAllSongs()
    }
    
    @Query("DELETE FROM ytmusic_playlists")
    suspend fun clearAllPlaylists()
    
    @Query("DELETE FROM ytmusic_songs")
    suspend fun clearAllSongs()
    
    @Query("DELETE FROM ytmusic_playlist_songs")
    suspend fun clearAllPlaylistSongs()
}
