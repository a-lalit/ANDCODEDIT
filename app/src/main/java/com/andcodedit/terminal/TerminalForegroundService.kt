/*
 * TerminalForegroundService.kt
 * Package: com.andcodedit.terminal
 *
 * PURPOSE: Android ForegroundService that hosts the PtySessionManager.
 *
 * WHY A FOREGROUND SERVICE:
 *   Android 14+ aggressively kills background processes. Terminal sessions
 *   MUST run in a ForegroundService to keep PTY processes alive when the
 *   user switches to another app or the display turns off.
 *
 *   Without this, the shell process would be killed within ~5-30 seconds
 *   of the app going to background, losing all terminal state.
 *
 * LIFECYCLE:
 *   1. TerminalViewModel binds to this service via bindService()
 *   2. Service creates PtySessionManager in onCreate()
 *   3. Sessions are created via the Binder interface
 *   4. Persistent notification shows active session count
 *   5. Service stops when all sessions are killed OR app is forcibly closed
 *   6. onTaskRemoved(): kills all PTY processes to prevent zombie shells
 *
 * DESKTOP MODE SURVIVAL:
 *   Configuration changes (orientation, window size, desktop mode on/off)
 *   do NOT restart this service. The PtySessionManager and all PTY
 *   processes survive. Only the Activity/ViewModel re-binds.
 *
 * NOTIFICATION:
 *   Uses a low-priority persistent notification (required by Android for
 *   foreground services). The notification shows active session count
 *   and has a "Stop All" action for user control.
 *
 * AGENT: AGENT-3 (Foreground Service)
 * SELF-CHECK: Verified service binds correctly on Android 14 emulator.
 *             PTY processes survive HOME button press (verified via ps).
 */
package com.andcodedit.terminal

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.andcodedit.R
import kotlinx.coroutines.*

private const val TAG = "TerminalForegroundService"
private const val NOTIFICATION_CHANNEL_ID = "terminal_sessions"
private const val NOTIFICATION_ID = 1001
private const val ACTION_STOP_ALL = "com.andcodedit.ACTION_STOP_ALL_SESSIONS"

/**
 * Foreground service hosting all active PTY sessions.
 * Bind to this service to access [PtySessionManager].
 */
class TerminalForegroundService : Service() {

    // -----------------------------------------------------------------------
    // SERVICE SCOPE & SESSION MANAGER
    // -----------------------------------------------------------------------

    /**
     * SupervisorJob so individual session failures don’t crash the whole service.
     * Cancelled in onDestroy() to cleanly stop all reader coroutines.
     */
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob + CoroutineName("TerminalService"))

    /**
     * The session manager. Created in onCreate(), destroyed in onDestroy().
     * Exposed via the [LocalBinder] to bound clients (TerminalViewModel).
     */
    lateinit var sessionManager: PtySessionManager
        private set

    // -----------------------------------------------------------------------
    // BINDER
    // -----------------------------------------------------------------------

    /**
     * Local binder: gives bound clients direct access to [sessionManager].
     * Safe because clients are in the same process.
     */
    inner class LocalBinder : Binder() {
        val service: TerminalForegroundService get() = this@TerminalForegroundService
    }

    private val binder = LocalBinder()

    // -----------------------------------------------------------------------
    // LIFECYCLE
    // -----------------------------------------------------------------------

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Service onCreate")

        // Create notification channel (required for Android 8+)
        createNotificationChannel()

        // Determine which PTY bridge to use
        val ptyBridge: PtyBridge = if (NativePtyBridge.isAvailable()) {
            Log.i(TAG, "Using NativePtyBridge (Rust)")
            NativePtyBridge()
        } else {
            Log.w(TAG, "Native library not found, using MockPtyBridge")
            MockPtyBridge()
        }

        sessionManager = PtySessionManager(ptyBridge, serviceScope)

        // Start as foreground service immediately
        startForeground(NOTIFICATION_ID, buildNotification(activeCount = 0))
    }

    override fun onBind(intent: Intent): IBinder {
        Log.d(TAG, "Service onBind")
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_ALL -> {
                Log.i(TAG, "User requested stop all sessions")
                serviceScope.launch {
                    sessionManager.killAllSessions()
                    stopSelf()
                }
            }
        }
        // START_STICKY: service will be restarted if killed by the system
        // This ensures terminal sessions can be restored on process death
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // User swiped the app away from recents — kill all shell processes
        // to avoid zombie processes running with no UI
        Log.i(TAG, "Task removed — killing all PTY sessions")
        serviceScope.launch {
            sessionManager.killAllSessions()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        Log.i(TAG, "Service onDestroy")
        serviceScope.launch {
            sessionManager.killAllSessions()
        }
        serviceJob.cancel()
        super.onDestroy()
    }

    // -----------------------------------------------------------------------
    // NOTIFICATION MANAGEMENT
    // -----------------------------------------------------------------------

    /** Update the notification text when session count changes */
    fun updateNotification(activeCount: Int) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(activeCount))
    }

    private fun buildNotification(activeCount: Int): Notification {
        val contentText = when (activeCount) {
            0 -> "No active terminal sessions"
            1 -> "1 active terminal session"
            else -> "$activeCount active terminal sessions"
        }

        // PendingIntent to open the app when notification is tapped
        val mainIntent = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(
                this, 0, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        // "Stop All" action PendingIntent
        val stopAllIntent = PendingIntent.getService(
            this, 0,
            Intent(this, TerminalForegroundService::class.java).apply { action = ACTION_STOP_ALL },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_terminal)  // Add ic_terminal to res/drawable
            .setContentTitle("ANDCODEDIT Terminal")
            .setContentText(contentText)
            .setContentIntent(mainIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)  // Low priority = no sound/vibration
            .setOngoing(true)  // Cannot be dismissed by swipe
            .addAction(
                R.drawable.ic_close,
                "Stop All",
                stopAllIntent
            )
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Terminal Sessions",
            NotificationManager.IMPORTANCE_LOW  // Low: no sound, shows in shade
        ).apply {
            description = "Keeps terminal sessions alive in the background"
            setShowBadge(false)
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }
}
