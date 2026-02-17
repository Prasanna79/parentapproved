package com.kidswatch.tv.util

import android.util.Log
import androidx.compose.runtime.mutableStateListOf

data class LogLine(val message: String, val level: String = "INFO", val timestamp: Long = System.currentTimeMillis())

object AppLogger {
    val lines = mutableStateListOf<LogLine>()
    private const val MAX_LINES = 500

    fun log(message: String, level: String = "INFO") {
        Log.d("KidsWatch", "[$level] $message")
        if (lines.size >= MAX_LINES) {
            lines.removeRange(0, lines.size - MAX_LINES + 100)
        }
        lines.add(LogLine(message, level))
    }

    fun error(message: String) = log(message, "ERROR")
    fun warn(message: String) = log(message, "WARN")
    fun success(message: String) = log(message, "OK")

    fun clear() { lines.clear() }
}
