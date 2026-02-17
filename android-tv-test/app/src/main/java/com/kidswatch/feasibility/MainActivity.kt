package com.kidswatch.feasibility

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.kidswatch.feasibility.ui.navigation.AppNavigation
import com.kidswatch.feasibility.ui.theme.KidsWatchFeasibilityTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KidsWatchFeasibilityTheme {
                AppNavigation()
            }
        }
    }
}
