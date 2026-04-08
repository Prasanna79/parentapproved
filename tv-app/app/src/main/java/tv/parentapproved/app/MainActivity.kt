package tv.parentapproved.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import tv.parentapproved.app.data.events.PlayEventRecorder
import tv.parentapproved.app.kiosk.HomeWatcherService
import tv.parentapproved.app.server.ParentApprovedServer
import tv.parentapproved.app.ui.navigation.AppNavigation
import tv.parentapproved.app.ui.theme.ParentApprovedTheme

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private var server: ParentApprovedServer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Start Ktor server on background thread to avoid ANR on slow devices
        Thread {
            server = ParentApprovedServer(this).also { it.start() }
        }.start()

        setContent {
            ParentApprovedTheme {
                AppNavigation()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        enterLockTaskIfEnabled()
        startHomeWatcherIfNeeded()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // HOME button pressed — singleTask redelivers intent here.
        // The NavController stays on whatever screen the user was on.
        Log.d(TAG, "onNewIntent: ${intent.action}")
    }

    override fun onStop() {
        super.onStop()
        PlayEventRecorder.flushCurrentEvent()
    }

    override fun onDestroy() {
        super.onDestroy()
        server?.stop()
    }

    fun exitLockTaskIfNeeded() {
        if (ServiceLocator.kioskManager.isInLockTaskMode()) {
            stopLockTask()
            Log.i(TAG, "Exited lock task mode")
        }
    }

    private fun startHomeWatcherIfNeeded() {
        if (!ServiceLocator.isInitialized()) return
        lifecycleScope.launch {
            val config = ServiceLocator.database.kioskDao().getConfig()
            if (config?.kioskEnabled == true) {
                HomeWatcherService.start(this@MainActivity)
            }
        }
    }

    private fun enterLockTaskIfEnabled() {
        if (!ServiceLocator.isInitialized()) return
        val kiosk = ServiceLocator.kioskManager
        if (!kiosk.isDeviceOwner()) return

        lifecycleScope.launch {
            val config = ServiceLocator.database.kioskDao().getConfig()
            if (config?.kioskEnabled == true && kiosk.isLockTaskPermitted()) {
                if (!kiosk.isInLockTaskMode()) {
                    startLockTask()
                    Log.i(TAG, "Entered lock task mode")
                }
            }
        }
    }
}
