package tv.parentapproved.app

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CrashHandler(
    private val context: Context?,
    private val previousHandler: Thread.UncaughtExceptionHandler?,
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val entry = buildCrashEntry(throwable)
            val file = context?.let { getCrashFile(it) }
            if (file != null) writeCrashLogTo(file, entry)
        } catch (_: Exception) {
            // Don't let crash logging cause another crash
        }
        // Delegate to previous handler to let the system terminate normally
        previousHandler?.uncaughtException(thread, throwable)
    }

    private fun buildCrashEntry(throwable: Throwable): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val stackTrace = throwable.stackTraceToString()
        return buildString {
            appendLine("=== CRASH $timestamp ===")
            appendLine("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine(stackTrace)
            appendLine()
        }
    }

    internal fun writeCrashLogTo(file: File, entry: String) {
        val existing = if (file.exists()) file.readText() else ""
        val allEntries = existing.split("=== CRASH ").filter { it.isNotBlank() }

        // Keep last 4 entries + the new one = 5 total
        val trimmed = allEntries.takeLast(4).joinToString("") { "=== CRASH $it" }
        val newContent = trimmed + entry

        // Cap at 100KB
        val toWrite = if (newContent.length > MAX_SIZE) {
            newContent.takeLast(MAX_SIZE)
        } else {
            newContent
        }
        file.writeText(toWrite)
    }

    companion object {
        private const val CRASH_FILE = "crash_log.txt"
        internal const val MAX_SIZE = 100 * 1024

        fun install(context: Context) {
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler(CrashHandler(context, previous))
        }

        fun getCrashFile(context: Context): File = File(context.filesDir, CRASH_FILE)

        fun readCrashLog(context: Context): String? {
            val file = getCrashFile(context)
            return if (file.exists() && file.length() > 0) file.readText() else null
        }

        fun hasCrashLog(context: Context): Boolean {
            val file = getCrashFile(context)
            return file.exists() && file.length() > 0
        }
    }
}
