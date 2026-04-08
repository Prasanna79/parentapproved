package tv.parentapproved.app.kiosk

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import tv.parentapproved.app.MainActivity

/**
 * Foreground service that ensures HOME returns to ParentApproved.
 *
 * Two mechanisms:
 * 1. Listens for ACTION_CLOSE_SYSTEM_DIALOGS "homekey" broadcast
 * 2. Polls every 2s — if PA was recently in front and now isn't (HOME pressed
 *    but blocked by the foreground app), forces PA back to front
 */
class HomeWatcherService : Service() {

    companion object {
        private const val TAG = "HomeWatcherService"
        private const val CHANNEL_ID = "home_watcher"
        private const val NOTIFICATION_ID = 2001
        private const val OWN_PACKAGE = "tv.parentapproved.app"
        private const val POLL_INTERVAL_MS = 2000L

        // Set by MainActivity when launching an app from the Apps row
        @Volatile
        var lastLaunchedApp: String? = null
        @Volatile
        var lastLaunchTime: Long = 0

        fun start(context: Context) {
            val intent = Intent(context, HomeWatcherService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, HomeWatcherService::class.java))
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var paWasInFront = true
    private var homeDetected = false

    private val homeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_CLOSE_SYSTEM_DIALOGS) {
                val reason = intent.getStringExtra("reason")
                if (reason == "homekey") {
                    Log.i(TAG, "HOME broadcast received")
                    homeDetected = true
                    bringPaToFront()
                }
            }
        }
    }

    // Polling fallback: detects when HOME was pressed but broadcast didn't fire
    private val pollChecker = object : Runnable {
        override fun run() {
            val topPkg = getTopPackage()

            if (topPkg == OWN_PACKAGE) {
                paWasInFront = true
                homeDetected = false
            } else if (homeDetected) {
                // Broadcast said HOME was pressed but we're not in front yet
                Log.i(TAG, "Poll: HOME was pressed, forcing PA to front over $topPkg")
                bringPaToFront()
                homeDetected = false
            }

            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    private fun getTopPackage(): String? {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        val tasks = am.getRunningTasks(1)
        return tasks.firstOrNull()?.topActivity?.packageName
    }

    private fun bringPaToFront() {
        val topPkg = getTopPackage()
        if (topPkg != null && topPkg != OWN_PACKAGE) {
            Log.i(TAG, "Killing $topPkg to return to PA")
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.killBackgroundProcesses(topPkg)
        }

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                    or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            )
        }
        startActivity(launchIntent)
    }

    override fun onCreate() {
        super.onCreate()
        // Must call startForeground IMMEDIATELY — Android kills the service
        // if this isn't done within a few seconds of startForegroundService()
        createNotificationChannel()
        startForeground(
            NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("ParentApproved")
                .setContentText("Managing TV access")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .build()
        )

        registerReceiver(homeReceiver, IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS))
        handler.postDelayed(pollChecker, POLL_INTERVAL_MS)
        Log.i(TAG, "Home watcher started (broadcast + poll)")
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(pollChecker)
        unregisterReceiver(homeReceiver)
        Log.i(TAG, "Home watcher stopped")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Home Watcher",
                NotificationManager.IMPORTANCE_MIN,
            ).apply {
                description = "Keeps ParentApproved as the home screen"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }
}
