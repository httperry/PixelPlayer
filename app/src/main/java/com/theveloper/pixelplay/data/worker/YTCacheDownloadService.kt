package com.theveloper.pixelplay.data.worker

import android.app.Notification
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.PlatformScheduler
import androidx.media3.exoplayer.scheduler.Scheduler
import com.theveloper.pixelplay.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@UnstableApi
@AndroidEntryPoint
class YTCacheDownloadService : DownloadService(
    FOREGROUND_NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    CHANNEL_ID,
    R.string.ytm_download_channel_name,
    R.string.ytm_download_channel_description
) {

    @Inject
    lateinit var injectedDownloadManager: DownloadManager

    override fun getDownloadManager(): DownloadManager {
        return injectedDownloadManager
    }

    override fun getScheduler(): Scheduler? {
        return PlatformScheduler(this, JOB_ID)
    }

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int
    ): Notification {
        // Here we ideally use a NotificationBuilder to show progress.
        // For simplicity, we create a basic notification.
        return android.app.Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Use a valid icon
            .setContentTitle("Downloading YouTube Music")
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "ytm_download_channel"
        private const val JOB_ID = 1000
        private const val FOREGROUND_NOTIFICATION_ID = 1001
    }
}
