package tv.parentapproved.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tv.parentapproved.app.ServiceLocator
import tv.parentapproved.app.timelimits.LockReason
import tv.parentapproved.app.timelimits.TimeLimitStatus
import tv.parentapproved.app.ui.theme.KidBackground
import tv.parentapproved.app.ui.theme.KidText
import tv.parentapproved.app.ui.theme.KidTextDim
import tv.parentapproved.app.ui.theme.ParentAccent

@Composable
fun LockScreen(
    reason: String,
    onUnlocked: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    var requestSent by remember { mutableStateOf(false) }
    var requestCooldown by remember { mutableStateOf(false) }

    val lockReason = try {
        LockReason.valueOf(reason.uppercase())
    } catch (_: Exception) {
        LockReason.MANUAL_LOCK
    }

    val icon = when (lockReason) {
        LockReason.DAILY_LIMIT -> Icons.Rounded.Timer
        LockReason.BEDTIME -> Icons.Rounded.Bedtime
        LockReason.MANUAL_LOCK -> Icons.Rounded.Lock
    }

    val title = when (lockReason) {
        LockReason.DAILY_LIMIT -> "All done for today!"
        LockReason.BEDTIME -> "Time for bed!"
        LockReason.MANUAL_LOCK -> "Taking a break!"
    }

    val subtitle = when (lockReason) {
        LockReason.DAILY_LIMIT -> "See you tomorrow!"
        LockReason.BEDTIME -> "TV time starts again in the morning."
        LockReason.MANUAL_LOCK -> "Ask your parent to unlock."
    }

    // Poll canPlay() every 5 seconds — navigate away when Allowed
    LaunchedEffect(Unit) {
        while (true) {
            delay(5_000)
            val status = ServiceLocator.timeLimitManager.canPlay()
            if (status is TimeLimitStatus.Allowed) {
                onUnlocked()
                return@LaunchedEffect
            }
        }
    }

    // Request focus
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(KidBackground)
            .focusRequester(focusRequester)
            .onKeyEvent { true } // Intercept all keys including Back
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = KidTextDim,
                modifier = Modifier.size(72.dp),
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                color = KidText,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = KidTextDim,
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    if (!requestCooldown) {
                        scope.launch {
                            try {
                                requestSent = true
                                requestCooldown = true
                                // Fire request to local server
                                val client = java.net.URL("http://localhost:8080/time-limits/request")
                                    .openConnection() as java.net.HttpURLConnection
                                client.requestMethod = "POST"
                                client.connectTimeout = 2000
                                client.readTimeout = 2000
                                try { client.responseCode } catch (_: Exception) { }
                                client.disconnect()
                            } catch (_: Exception) { }
                        }
                    }
                },
                enabled = !requestCooldown,
                colors = ButtonDefaults.buttonColors(containerColor = ParentAccent),
            ) {
                Text(
                    text = if (requestSent) "Request sent!" else "Request More Time",
                    color = KidText,
                )
            }
        }
    }
}
