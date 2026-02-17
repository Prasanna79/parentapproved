package com.kidswatch.tv.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kidswatch.tv.data.FirebaseManager
import com.kidswatch.tv.data.events.PlayEventRecorder
import com.kidswatch.tv.util.AppLogger
import com.kidswatch.tv.util.OfflineSimulator

class DebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "com.kidswatch.tv.DEBUG_RESET_PAIRING" -> {
                AppLogger.log("ADB: Reset pairing")
                FirebaseManager.deleteDeviceDoc()
                FirebaseManager.signOut()
            }
            "com.kidswatch.tv.DEBUG_REFRESH_PLAYLISTS" -> {
                AppLogger.log("ADB: Refresh playlists requested")
                // This is handled by the ViewModel; the broadcast just logs
            }
            "com.kidswatch.tv.DEBUG_FORCE_FLUSH_EVENTS" -> {
                AppLogger.log("ADB: Force flush events")
                PlayEventRecorder.flush()
            }
            "com.kidswatch.tv.DEBUG_SIMULATE_OFFLINE" -> {
                OfflineSimulator.toggle()
                AppLogger.log("ADB: Offline mode = ${OfflineSimulator.isOffline}")
            }
        }
    }
}
