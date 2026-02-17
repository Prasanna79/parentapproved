package com.kidswatch.tv.util

object OfflineSimulator {
    @Volatile
    var isOffline: Boolean = false

    fun toggle() {
        isOffline = !isOffline
        AppLogger.log("Offline mode: $isOffline")
    }
}
