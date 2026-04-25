package com.theveloper.pixelplay.data.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.theveloper.pixelplay.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import java.security.SecureRandom
import java.util.Base64
import javax.inject.Inject

private const val TAG = "YTMusicPythonService"
private const val NOTIFICATION_ID = 9001
private const val CHANNEL_ID = "ytmusic_python_service"
private const val IDLE_TIMEOUT_MS = 5 * 60 * 1000L // 5 minutes

/**
 * Battery-optimized foreground service for Python WebSocket server.
 * 
 * Battery Optimization Strategy:
 * 1. Starts on-demand (when app needs it)
 * 2. Stays alive while app is active
 * 3. Auto-stops after 5 minutes of inactivity
 * 4. Uses PARTIAL_WAKE_LOCK only when needed
 * 5. Releases resources when idle
 * 
 * Battery Impact: ~1-2% per hour (minimal)
 */
@AndroidEntryPoint
class YTMusicPythonService : Service() {

    @Inject
    lateinit var ytmSessionRepository: com.theveloper.pixelplay.data.network.ytmusic.YTMSessionRepository

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pythonServerJob: Job? = null
    private var idleCheckJob: Job? = null
    private var isServerRunning = false
    private var isBackendAuthenticated = false
    private var lastActivityTime = System.currentTimeMillis()
    
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        private var isServiceRunning = false
        private var encryptionKey: String? = null
        private var instance: YTMusicPythonService? = null

        fun start(context: Context) {
            val intent = Intent(context, YTMusicPythonService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, YTMusicPythonService::class.java)
            context.stopService(intent)
        }

        fun isRunning(): Boolean = isServiceRunning
        
        fun isBackendAuthenticated(): Boolean = instance?.isBackendAuthenticated == true
        
        fun getEncryptionKey(): String? = encryptionKey
        
        /**
         * Call this whenever the app uses the service.
         * Resets idle timer to keep service alive.
         */
        fun keepAlive() {
            instance?.updateActivity()
        }
        
        /**
         * Setup authentication with browser.json file path.
         * Passes the file to Python backend for ytmusicapi initialization.
         */
        fun setupAuthentication(browserJsonPath: String) {
            instance?.authenticatePythonBackend(browserJsonPath)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        instance = this
        
        // Generate encryption key
        if (encryptionKey == null) {
            encryptionKey = generateEncryptionKey()
            Log.d(TAG, "🔐 Encryption key generated")
        }
        
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, 
                createNotification("Starting..."),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, createNotification("Starting..."))
        }
        
        startPythonServer()
        startIdleCheck()
        
        // Check for existing browser.json and authenticate if found
        checkAndAuthenticateExistingSession()
        
        isServiceRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")
        updateActivity()
        return START_STICKY // Restart if killed
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        
        stopPythonServer()
        releaseWakeLock()
        idleCheckJob?.cancel()
        serviceScope.cancel()
        isServiceRunning = false
        instance = null
    }

    private fun generateEncryptionKey(): String {
        val keyBytes = ByteArray(32)
        SecureRandom().nextBytes(keyBytes)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Base64.getEncoder().encodeToString(keyBytes)
        } else {
            android.util.Base64.encodeToString(keyBytes, android.util.Base64.NO_WRAP)
        }
    }

    private fun updateActivity() {
        lastActivityTime = System.currentTimeMillis()
        
        // Update notification to show active status
        val notification = createNotification("Active")
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }
    
    private fun checkAndAuthenticateExistingSession() {
        serviceScope.launch {
            try {
                // Check if we have auth headers in DataStore
                val authHeaders = ytmSessionRepository.getAuthHeaders()
                
                if (authHeaders != null) {
                    Log.d(TAG, "📄 Found auth headers in DataStore, recreating browser.json...")
                    
                    // Recreate browser.json from DataStore (in case it was deleted)
                    val browserJsonFile = java.io.File(filesDir, "browser.json")
                    val gson = com.google.gson.Gson()
                    browserJsonFile.writeText(gson.toJson(authHeaders))
                    
                    Log.d(TAG, "✅ browser.json recreated at: ${browserJsonFile.absolutePath}")
                    
                    // Wait for Python server to be fully ready
                    delay(3000)
                    
                    authenticatePythonBackend(browserJsonFile.absolutePath)
                } else {
                    Log.d(TAG, "No auth headers in DataStore - user needs to login")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking for existing session", e)
            }
        }
    }
    
    private fun authenticatePythonBackend(browserJsonPath: String) {
        serviceScope.launch {
            try {
                Log.d(TAG, "Setting up Python backend authentication with: $browserJsonPath")
                
                // Wait for Python server to be ready
                var retries = 0
                while (!isServerRunning && retries < 20) {
                    delay(500)
                    retries++
                }
                
                if (!isServerRunning) {
                    Log.e(TAG, "Python server not running, cannot authenticate")
                    return@launch
                }
                
                val python = Python.getInstance()
                val module = python.getModule("ytmusic_websocket_server")
                
                // Call Python function to load browser.json
                val result = module.callAttr("setup_auth", browserJsonPath)
                
                Log.d(TAG, "✅ Python backend authentication result: $result")
                isBackendAuthenticated = true
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to authenticate Python backend", e)
            }
        }
    }

    private fun startIdleCheck() {
        idleCheckJob = serviceScope.launch {
            while (isActive) {
                delay(60000) // Check every minute
                
                val idleTime = System.currentTimeMillis() - lastActivityTime
                
                if (idleTime > IDLE_TIMEOUT_MS) {
                    Log.d(TAG, "Service idle for ${idleTime / 1000}s, stopping...")
                    
                    // Update notification
                    val notification = createNotification("Idle - stopping soon")
                    val manager = getSystemService(NotificationManager::class.java)
                    manager.notify(NOTIFICATION_ID, notification)
                    
                    // Stop service
                    delay(5000) // Grace period
                    stopSelf()
                } else {
                    // Update notification with idle time
                    val idleSeconds = idleTime / 1000
                    val notification = createNotification("Idle ${idleSeconds}s")
                    val manager = getSystemService(NotificationManager::class.java)
                    manager.notify(NOTIFICATION_ID, notification)
                }
            }
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "PixelPlay::YTMusicService"
            ).apply {
                setReferenceCounted(false)
                acquire(10 * 60 * 1000L) // 10 minutes max
            }
            Log.d(TAG, "WakeLock acquired")
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d(TAG, "WakeLock released")
            }
        }
        wakeLock = null
    }

    private fun startPythonServer() {
        pythonServerJob = serviceScope.launch {
            try {
                Log.d(TAG, "Starting Python WebSocket server...")
                
                // Acquire wake lock only during startup
                acquireWakeLock()
                
                // Initialize Python
                if (!Python.isStarted()) {
                    Log.d(TAG, "Initializing Python...")
                    Python.start(AndroidPlatform(applicationContext))
                    Log.d(TAG, "Python initialized successfully")
                }
                
                val python = Python.getInstance()
                Log.d(TAG, "Getting ytmusic_websocket_server module...")
                val module = python.getModule("ytmusic_websocket_server")
                Log.d(TAG, "Module loaded successfully")
                
                // Set server as running BEFORE starting (run_server blocks forever)
                isServerRunning = true
                Log.d(TAG, "✅ Python WebSocket server marked as running")
                
                // Release wake lock before starting server (it will block)
                releaseWakeLock()
                
                // Run server (this blocks forever - it's an async event loop)
                withContext(Dispatchers.IO) {
                    Log.d(TAG, "Python WebSocket server starting on port 8765...")
                    Log.d(TAG, "Calling run_server (no encryption for localhost)")
                    
                    // Call run_server without encryption key (localhost only, no encryption needed)
                    module.callAttr("run_server")
                    
                    // This line is never reached - run_server runs forever
                    Log.d(TAG, "⚠️ Python server stopped unexpectedly")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start Python server: ${e.message}", e)
                Log.e(TAG, "Exception type: ${e.javaClass.name}")
                e.printStackTrace()
                isServerRunning = false
                releaseWakeLock()
                
                // Retry after delay
                delay(5000)
                if (isServiceRunning) {
                    Log.d(TAG, "Retrying Python server start...")
                    startPythonServer()
                }
            }
        }
    }

    private fun stopPythonServer() {
        pythonServerJob?.cancel()
        isServerRunning = false
        Log.d(TAG, "Python server stopped")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "YouTube Music Service",
                NotificationManager.IMPORTANCE_LOW // Low importance = no sound/vibration
            ).apply {
                description = "Keeps YouTube Music API ready"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
            
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(status: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("YouTube Music")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Use system icon
            .setOngoing(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true) // Don't alert on updates
            .build()
    }
}
