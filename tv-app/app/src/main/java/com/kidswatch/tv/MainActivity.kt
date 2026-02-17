package com.kidswatch.tv

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.kidswatch.tv.server.KidsWatchServer
import com.kidswatch.tv.ui.navigation.AppNavigation
import com.kidswatch.tv.ui.theme.KidsWatchTVTheme

class MainActivity : ComponentActivity() {

    private var server: KidsWatchServer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Start Ktor server
        server = KidsWatchServer(this).also { it.start() }

        setContent {
            KidsWatchTVTheme {
                AppNavigation()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        server?.stop()
    }
}
