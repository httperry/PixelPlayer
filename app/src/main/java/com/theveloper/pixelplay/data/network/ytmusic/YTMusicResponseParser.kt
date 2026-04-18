package com.theveloper.pixelplay.data.network.ytmusic

import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.model.ArtistRef

/**
 * Parser for ytmusicapi responses.
 * 
 * Converts Python ytmusicapi responses (Map<String, Any>) into
 * PixelPlay's domain models (Song, Artist, etc.)
 * 
 * Handles:
 * - High-quality thumbnail selection
 * - Metadata extraction
 * - Type conversions
 */
object YTMusicResponseParser {

    /**
     * Get best quality thumbnail URL from thumbnails array.
     * 
     * ytmusicapi returns thumbnails in multiple sizes:
     * - Search results: [60x60, 120x120] (limited)
     * - Album/detailed: [60x60, 120x120, 226x226, 544x544]
     * 
     * Strategy:
     * 1. Select the largest available thumbnail
     * 2. If it's smaller than preferred size, upgrade the URL
     * 3. YouTube Music images support URL manipulation: =w{size}-h{size}
     */
    fun getBestThumbnail(thumbnails: List<Map<String, Any>>?, preferredSize: Int = 544): String? {
        if (thumbnails.isNullOrEmpty()) return null
        
        // Find the largest available thumbnail
        val best = thumbnails
            .mapNotNull { thumb ->
                val url = thumb["url"] as? String
                val width = (thumb["width"] as? Number)?.toInt() ?: 0
                val height = (thumb["height"] as? Number)?.toInt() ?: 0
                
                if (url != null) {
                    Triple(url, width, height)
                } else null
            }
            .maxByOrNull { (_, width, height) -> width * height }
            ?: return null
        
        val (url, width, height) = best
        
        // If the image is already high quality, return as-is
        if (width >= preferredSize && height >= preferredSize) {
            return url
        }
        
        // Otherwise, upgrade the URL to preferred size
        return upgradeImageUrl(url, preferredSize)
    }
    
    /**
     * Upgrade YouTube Music image URL to higher quality.
     * 
     * YouTube Music uses Google's image serving with these URL patterns:
     * - =w{width}-h{height}-l90-rj (specific size)
     * - =s{size} (square, max size)
     * - =s0 (original/max quality)
     * 
     * We replace the size parameters to get higher quality.
     */
    private fun upgradeImageUrl(url: String, targetSize: Int): String {
        // Remove existing size parameters
        val baseUrl = url.substringBefore("=w")
            .substringBefore("=s")
        
        // Add high-quality size parameter
        // Format: =w{size}-h{size}-l90-rj
        // l90 = quality level, rj = format
        return "$baseUrl=w$targetSize-h$targetSize-l90-rj"
    }
    
    /**
     * Get maximum quality thumbnail (original size).
     * Use this for artist banners, album art in now playing, etc.
     */
    fun getMaxQualityThumbnail(thumbnails: List<Map<String, Any>>?): String? {
        if (thumbnails.isNullOrEmpty()) return null
        
        val largest = thumbnails
            .mapNotNull { it["url"] as? String }
            .firstOrNull() ?: return null
        
        // Remove size parameters to get original quality
        val baseUrl = largest.substringBefore("=w")
            .substringBefore("=s")
        
        // =s0 means "original size, no scaling"
        return "$baseUrl=s0"
    }

    /**
     * Parse search result into Song.
     * Creates a Song object suitable for YouTube Music streaming.
     */
    fun parseSearchResult(result: Map<String, Any>): Song? {
        try {
            val videoId = result["videoId"] as? String ?: return null
            val title = result["title"] as? String ?: "Unknown"
            
            // Artists
            @Suppress("UNCHECKED_CAST")
            val artistsList = result["artists"] as? List<Map<String, Any>> ?: emptyList()
            val artistRefs = artistsList.mapNotNull { artist ->
                val name = artist["name"] as? String
                val id = artist["id"] as? String
                if (name != null) {
                    ArtistRef(
                        id = id?.hashCode()?.toLong() ?: 0L,
                        name = name,
                        isPrimary = artistsList.indexOf(artist) == 0
                    )
                } else null
            }
            
            // Primary artist for backward compatibility
            val primaryArtist = artistRefs.firstOrNull()?.name ?: "Unknown Artist"
            val primaryArtistId = artistRefs.firstOrNull()?.id ?: 0L
            
            // Album
            @Suppress("UNCHECKED_CAST")
            val albumData = result["album"] as? Map<String, Any>
            val albumName = albumData?.get("name") as? String ?: "Unknown Album"
            val albumIdStr = albumData?.get("id") as? String
            val albumId = albumIdStr?.hashCode()?.toLong() ?: 0L
            
            // Duration
            val durationSeconds = (result["duration_seconds"] as? Number)?.toInt() ?: 0
            val durationMs = durationSeconds * 1000L
            
            // Thumbnails - GET BEST QUALITY!
            @Suppress("UNCHECKED_CAST")
            val thumbnails = result["thumbnails"] as? List<Map<String, Any>>
            val thumbnailUrl = getBestThumbnail(thumbnails, preferredSize = 544)
            
            // Stream info (if available from ytmusicapi get_song)
            val bitrate = (result["bitrate"] as? Number)?.toInt()
            val sampleRate = (result["sampleRate"] as? Number)?.toInt()
            val mimeType = result["mimeType"] as? String ?: "audio/webm"
            
            // Year (if available)
            val year = (result["year"] as? Number)?.toInt() ?: 0
            
            return Song(
                id = videoId,
                title = title,
                artist = primaryArtist,
                artistId = primaryArtistId,
                artists = artistRefs,
                album = albumName,
                albumId = albumId,
                path = "", // Streaming - no local path
                contentUriString = "ytm://$videoId", // Use Python ytmusicapi backend (NOT NewPipe)
                albumArtUriString = thumbnailUrl,
                duration = durationMs,
                mimeType = mimeType,
                bitrate = bitrate,
                sampleRate = sampleRate,
                year = year,
                ytmusicId = videoId
            )
            
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Parse library song into Song.
     * Creates a Song object suitable for YouTube Music streaming.
     */
    fun parseLibrarySong(song: Map<String, Any>): Song? {
        try {
            val videoId = song["videoId"] as? String ?: return null
            val title = song["title"] as? String ?: "Unknown"
            
            // Artists
            @Suppress("UNCHECKED_CAST")
            val artistsList = song["artists"] as? List<Map<String, Any>> ?: emptyList()
            val artistRefs = artistsList.mapNotNull { artist ->
                val name = artist["name"] as? String
                val id = artist["id"] as? String
                if (name != null) {
                    ArtistRef(
                        id = id?.hashCode()?.toLong() ?: 0L,
                        name = name,
                        isPrimary = artistsList.indexOf(artist) == 0
                    )
                } else null
            }
            
            // Primary artist for backward compatibility
            val primaryArtist = artistRefs.firstOrNull()?.name ?: "Unknown Artist"
            val primaryArtistId = artistRefs.firstOrNull()?.id ?: 0L
            
            // Album
            @Suppress("UNCHECKED_CAST")
            val albumData = song["album"] as? Map<String, Any>
            val albumName = albumData?.get("name") as? String ?: "Unknown Album"
            val albumIdStr = albumData?.get("id") as? String
            val albumId = albumIdStr?.hashCode()?.toLong() ?: 0L
            
            // Duration
            val durationSeconds = (song["duration_seconds"] as? Number)?.toInt() ?: 0
            val durationMs = durationSeconds * 1000L
            
            // Thumbnails - HIGH QUALITY!
            @Suppress("UNCHECKED_CAST")
            val thumbnails = song["thumbnails"] as? List<Map<String, Any>>
            val thumbnailUrl = getBestThumbnail(thumbnails, preferredSize = 544)
            
            // Like status
            val likeStatus = song["likeStatus"] as? String
            val isFavorite = likeStatus == "LIKE"
            
            // Stream info (if available from ytmusicapi get_song)
            val bitrate = (song["bitrate"] as? Number)?.toInt()
            val sampleRate = (song["sampleRate"] as? Number)?.toInt()
            val mimeType = song["mimeType"] as? String ?: "audio/webm"
            
            // Year (if available)
            val year = (song["year"] as? Number)?.toInt() ?: 0
            
            return Song(
                id = videoId,
                title = title,
                artist = primaryArtist,
                artistId = primaryArtistId,
                artists = artistRefs,
                album = albumName,
                albumId = albumId,
                path = "", // Streaming - no local path
                contentUriString = "ytm://$videoId", // Use Python ytmusicapi backend (NOT NewPipe)
                albumArtUriString = thumbnailUrl,
                duration = durationMs,
                isFavorite = isFavorite,
                mimeType = mimeType,
                bitrate = bitrate,
                sampleRate = sampleRate,
                year = year,
                ytmusicId = videoId
            )
            
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Parse playlist into simplified model.
     */
    fun parsePlaylist(playlist: Map<String, Any>): Map<String, Any> {
        val id = playlist["id"] as? String ?: ""
        val title = playlist["title"] as? String ?: "Unknown"
        val description = playlist["description"] as? String ?: ""
        val count = (playlist["count"] as? Number)?.toInt() ?: 0
        
        // Thumbnails - HIGH QUALITY!
        @Suppress("UNCHECKED_CAST")
        val thumbnails = playlist["thumbnails"] as? List<Map<String, Any>>
        val thumbnailUrl = getBestThumbnail(thumbnails, preferredSize = 544)
        
        return mapOf(
            "id" to id,
            "title" to title,
            "description" to description,
            "count" to count,
            "thumbnailUrl" to (thumbnailUrl ?: "")
        )
    }

    /**
     * Parse playlist tracks into Songs.
     * Creates Song objects suitable for YouTube Music streaming.
     */
    fun parsePlaylistTracks(tracks: List<Map<String, Any>>): List<Song> {
        return tracks.mapNotNull { track ->
            try {
                val videoId = track["videoId"] as? String ?: return@mapNotNull null
                val title = track["title"] as? String ?: "Unknown"
                
                // Artists
                @Suppress("UNCHECKED_CAST")
                val artistsList = track["artists"] as? List<Map<String, Any>> ?: emptyList()
                val artistRefs = artistsList.mapNotNull { artist ->
                    val name = artist["name"] as? String
                    val id = artist["id"] as? String
                    if (name != null) {
                        ArtistRef(
                            id = id?.hashCode()?.toLong() ?: 0L,
                            name = name,
                            isPrimary = artistsList.indexOf(artist) == 0
                        )
                    } else null
                }
                
                // Primary artist for backward compatibility
                val primaryArtist = artistRefs.firstOrNull()?.name ?: "Unknown Artist"
                val primaryArtistId = artistRefs.firstOrNull()?.id ?: 0L
                
                // Album
                @Suppress("UNCHECKED_CAST")
                val albumData = track["album"] as? Map<String, Any>
                val albumName = albumData?.get("name") as? String ?: "Unknown Album"
                val albumIdStr = albumData?.get("id") as? String
                val albumId = albumIdStr?.hashCode()?.toLong() ?: 0L
                
                // Duration
                val durationSeconds = (track["duration_seconds"] as? Number)?.toInt() ?: 0
                val durationMs = durationSeconds * 1000L
                
                // Thumbnails - HIGH QUALITY!
                @Suppress("UNCHECKED_CAST")
                val thumbnails = track["thumbnails"] as? List<Map<String, Any>>
                val thumbnailUrl = getBestThumbnail(thumbnails, preferredSize = 544)
                
                // Stream info (if available from ytmusicapi get_song)
                val bitrate = (track["bitrate"] as? Number)?.toInt()
                val sampleRate = (track["sampleRate"] as? Number)?.toInt()
                val mimeType = track["mimeType"] as? String ?: "audio/webm"
                
                // Year (if available)
                val year = (track["year"] as? Number)?.toInt() ?: 0
                
                Song(
                    id = videoId,
                    title = title,
                    artist = primaryArtist,
                    artistId = primaryArtistId,
                    artists = artistRefs,
                    album = albumName,
                    albumId = albumId,
                    path = "", // Streaming - no local path
                    contentUriString = "ytm://$videoId", // Use Python ytmusicapi backend (NOT NewPipe)
                    albumArtUriString = thumbnailUrl,
                    duration = durationMs,
                    mimeType = mimeType,
                    bitrate = bitrate,
                    sampleRate = sampleRate,
                    year = year,
                    ytmusicId = videoId
                )
                
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Parse artist details.
     */
    fun parseArtist(artist: Map<String, Any>): Map<String, Any> {
        val name = artist["name"] as? String ?: "Unknown"
        val description = artist["description"] as? String ?: ""
        val subscribers = artist["subscribers"] as? String ?: ""
        
        // Thumbnails - HIGH QUALITY!
        @Suppress("UNCHECKED_CAST")
        val thumbnails = artist["thumbnails"] as? List<Map<String, Any>>
        val thumbnailUrl = getBestThumbnail(thumbnails, preferredSize = 544)
        
        return mapOf(
            "name" to name,
            "description" to description,
            "subscribers" to subscribers,
            "thumbnailUrl" to (thumbnailUrl ?: "")
        )
    }

    /**
     * Parse home feed sections.
     */
    fun parseHomeFeed(home: List<Map<String, Any>>): List<Map<String, Any>> {
        return home.mapNotNull { section ->
            try {
                val title = section["title"] as? String ?: return@mapNotNull null
                
                @Suppress("UNCHECKED_CAST")
                val contents = section["contents"] as? List<Map<String, Any>> ?: emptyList()
                
                // Parse contents and get high-quality thumbnails
                val parsedContents = contents.map { item ->
                    @Suppress("UNCHECKED_CAST")
                    val thumbnails = item["thumbnails"] as? List<Map<String, Any>>
                    val thumbnailUrl = getBestThumbnail(thumbnails, preferredSize = 544)
                    
                    item.toMutableMap().apply {
                        put("thumbnailUrl", thumbnailUrl ?: "")
                    }
                }
                
                mapOf(
                    "title" to title,
                    "contents" to parsedContents
                )
                
            } catch (e: Exception) {
                null
            }
        }
    }
}
