package com.kidswatch.feasibility.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class DebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d("KW-Debug", "Received: $action extras=${intent.extras}")

        when (action) {
            // Navigation
            "com.kidswatch.feasibility.NAV" -> {
                val route = intent.getStringExtra("route") ?: return
                DebugActionBus.post(DebugAction.Navigate(route))
            }

            // Test 5
            "com.kidswatch.feasibility.TEST5_RESOLVE" -> {
                val index = intent.getIntExtra("index", 0)
                DebugActionBus.post(DebugAction.ResolvePlaylist(index))
            }

            // Test 6
            "com.kidswatch.feasibility.TEST6_EXTRACT" -> {
                val index = intent.getIntExtra("index", 0)
                DebugActionBus.post(DebugAction.ExtractStreams(index))
            }
            "com.kidswatch.feasibility.TEST6_PLAY_PROG" -> {
                val res = intent.getStringExtra("res") ?: "360p"
                DebugActionBus.post(DebugAction.PlayProgressive(res))
            }
            "com.kidswatch.feasibility.TEST6_PLAY_MERGE" -> {
                val res = intent.getStringExtra("res") ?: "720p"
                DebugActionBus.post(DebugAction.PlayMerged(res))
            }
            "com.kidswatch.feasibility.TEST6_STOP" -> {
                DebugActionBus.post(DebugAction.StopPlayer)
            }

            // Common
            "com.kidswatch.feasibility.CLEAR_LOGS" -> {
                DebugActionBus.post(DebugAction.ClearLogs)
            }
        }
    }
}
