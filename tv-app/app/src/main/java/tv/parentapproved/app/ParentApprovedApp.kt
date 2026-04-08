package tv.parentapproved.app

import android.app.Application
import tv.parentapproved.app.data.ChannelMeta
import tv.parentapproved.app.data.ContentSourceRepository
import tv.parentapproved.app.util.AppLogger
import tv.parentapproved.app.kiosk.HomeWatcherService
import tv.parentapproved.app.util.NewPipeDownloader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.NewPipe

class ParentApprovedApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        CrashHandler.install(this)
        if (CrashHandler.hasCrashLog(this)) {
            AppLogger.log("Previous crash detected — crash log available via /api/crash-log")
        }
        NewPipe.init(NewPipeDownloader.instance)
        ServiceLocator.init(this)
        // Only connect relay if user has enabled remote access
        if (ServiceLocator.isRelayEnabled()) {
            ServiceLocator.relayConnector.connect()
        }
        // Start home watcher if kiosk is enabled (delayed to avoid ANR on slow devices)
        appScope.launch {
            try {
                kotlinx.coroutines.delay(5_000) // Wait for app to fully initialize
                val config = ServiceLocator.database.kioskDao().getConfig()
                if (config?.kioskEnabled == true) {
                    HomeWatcherService.start(this@ParentApprovedApp)
                    AppLogger.log("Home watcher service started")
                }
            } catch (e: Exception) {
                AppLogger.error("Failed to start home watcher: ${e.message}")
            }
        }
        // Auto-refresh all sources in background
        appScope.launch {
            try {
                val db = ServiceLocator.database
                val channels = db.channelDao().getAll()
                if (channels.isNotEmpty()) {
                    AppLogger.log("Auto-refreshing ${channels.size} sources on startup")
                    val metas = channels.map { entity ->
                        ChannelMeta(
                            id = entity.id,
                            sourceType = entity.sourceType,
                            sourceId = entity.sourceId,
                            sourceUrl = entity.sourceUrl,
                            displayName = entity.displayName,
                        )
                    }
                    ContentSourceRepository.resolveAllChannels(metas, db)
                    AppLogger.success("Startup auto-refresh complete")
                }
            } catch (e: Exception) {
                AppLogger.error("Startup auto-refresh failed: ${e.message}")
            }
        }
    }
}
