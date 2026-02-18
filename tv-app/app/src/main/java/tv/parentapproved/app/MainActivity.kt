package tv.parentapproved.app

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import tv.parentapproved.app.server.ParentApprovedServer
import tv.parentapproved.app.ui.navigation.AppNavigation
import tv.parentapproved.app.ui.theme.ParentApprovedTheme

class MainActivity : ComponentActivity() {

    private var server: ParentApprovedServer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Start Ktor server
        server = ParentApprovedServer(this).also { it.start() }

        setContent {
            ParentApprovedTheme {
                AppNavigation()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        server?.stop()
    }
}
