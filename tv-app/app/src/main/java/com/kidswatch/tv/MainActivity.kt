package com.kidswatch.tv

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.kidswatch.tv.data.FirebaseManager
import com.kidswatch.tv.data.events.PlayEventRecorder
import com.kidswatch.tv.ui.navigation.AppNavigation
import com.kidswatch.tv.ui.theme.KidsWatchTVTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        FirebaseManager.signInAnonymously { success ->
            Log.d("KidsWatch", "Anonymous auth: $success, uid=${FirebaseManager.uid}")
        }

        setContent {
            KidsWatchTVTheme {
                AppNavigation()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        PlayEventRecorder.flush()
    }

    override fun onStop() {
        super.onStop()
        PlayEventRecorder.flush()
    }
}
