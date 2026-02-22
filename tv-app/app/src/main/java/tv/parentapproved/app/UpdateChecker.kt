package tv.parentapproved.app

import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import tv.parentapproved.app.util.AppLogger
import java.util.concurrent.TimeUnit

@Serializable
data class VersionInfo(
    val latest: String,
    val latestCode: Int,
    val url: String,
    val releaseNotes: String = "",
)

class UpdateChecker(
    private val versionCheckUrl: String = BuildConfig.VERSION_CHECK_URL,
    private val currentVersionCode: Int = BuildConfig.VERSION_CODE,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build(),
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var checkJob: Job? = null

    var latestVersion: VersionInfo? = null
        private set

    val isUpdateAvailable: Boolean
        get() = latestVersion?.let { it.latestCode > currentVersionCode } ?: false

    fun startPeriodicCheck(intervalMs: Long = 24 * 60 * 60 * 1000L) {
        checkJob?.cancel()
        checkJob = scope.launch {
            // Check immediately on start
            checkForUpdate()
            // Then periodically
            while (isActive) {
                delay(intervalMs)
                checkForUpdate()
            }
        }
    }

    fun stop() {
        checkJob?.cancel()
        checkJob = null
    }

    internal suspend fun checkForUpdate(): VersionInfo? {
        return try {
            val request = Request.Builder().url(versionCheckUrl).build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return null
                val info = json.decodeFromString<VersionInfo>(body)
                latestVersion = info
                if (info.latestCode > currentVersionCode) {
                    AppLogger.log("Update available: v${info.latest} (current: ${BuildConfig.VERSION_NAME})")
                }
                info
            } else {
                null
            }
        } catch (e: Exception) {
            AppLogger.log("Version check failed: ${e.message}")
            null
        }
    }
}
