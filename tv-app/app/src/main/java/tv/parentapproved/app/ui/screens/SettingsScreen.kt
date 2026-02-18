package tv.parentapproved.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tv.parentapproved.app.BuildConfig
import tv.parentapproved.app.ServiceLocator
import tv.parentapproved.app.relay.RelayConnectionState
import tv.parentapproved.app.data.events.PlayEventRecorder
import tv.parentapproved.app.ui.components.LogPanel
import tv.parentapproved.app.ui.theme.OverscanPadding
import tv.parentapproved.app.ui.theme.TvBackground
import tv.parentapproved.app.ui.theme.TvPrimary
import tv.parentapproved.app.ui.theme.TvText
import tv.parentapproved.app.ui.theme.TvTextDim
import tv.parentapproved.app.ui.theme.TvAccent
import tv.parentapproved.app.ui.theme.TvWarning
import tv.parentapproved.app.util.AppLogger
import tv.parentapproved.app.util.OfflineSimulator

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    var showLog by remember { mutableStateOf(false) }
    var displayPin by remember { mutableStateOf(ServiceLocator.pinManager.getCurrentPin()) }
    var sessionCount by remember { mutableStateOf(ServiceLocator.sessionManager.getActiveSessionCount()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TvBackground)
            .padding(OverscanPadding)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Settings", style = MaterialTheme.typography.headlineMedium, color = TvText)
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(containerColor = TvPrimary),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("Back", color = TvText)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- General ---
        Text("General", style = MaterialTheme.typography.titleMedium, color = TvText)
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsBtn("Refresh Videos") { onRefresh() }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text("v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = TvTextDim)

        Spacer(modifier = Modifier.height(24.dp))

        // --- Connection ---
        Text("Connection", style = MaterialTheme.typography.titleMedium, color = TvText)
        Spacer(modifier = Modifier.height(8.dp))
        Text("PIN: $displayPin", style = MaterialTheme.typography.bodySmall, color = TvTextDim)
        Text("Active sessions: $sessionCount", style = MaterialTheme.typography.bodySmall, color = TvTextDim)

        Spacer(modifier = Modifier.height(16.dp))

        // --- Remote Access ---
        Text("Remote Access", style = MaterialTheme.typography.titleMedium, color = TvText)
        Spacer(modifier = Modifier.height(8.dp))

        var relayEnabled by remember { mutableStateOf(ServiceLocator.isRelayEnabled()) }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsBtn(if (relayEnabled) "Disable Remote" else "Enable Remote") {
                relayEnabled = !relayEnabled
                ServiceLocator.setRelayEnabled(relayEnabled)
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (relayEnabled) {
            // Relay status — poll every 2s so it updates after connect/disconnect
            var relayState by remember { mutableStateOf(
                try { ServiceLocator.relayConnector.state } catch (_: Exception) { null }
            ) }
            LaunchedEffect(relayEnabled) {
                while (true) {
                    delay(2000)
                    relayState = try { ServiceLocator.relayConnector.state } catch (_: Exception) { null }
                }
            }
            relayState?.let { state ->
                val statusText = when (state) {
                    RelayConnectionState.CONNECTED -> "Connected"
                    RelayConnectionState.CONNECTING -> "Connecting..."
                    RelayConnectionState.DISCONNECTED -> "Disconnected"
                }
                val relayColor = when (state) {
                    RelayConnectionState.CONNECTED -> TvAccent
                    RelayConnectionState.CONNECTING -> TvWarning
                    RelayConnectionState.DISCONNECTED -> TvTextDim
                }
                Text("Relay: $statusText", style = MaterialTheme.typography.bodySmall, color = relayColor)
            }
            Text(
                "Dashboard works from anywhere when enabled.",
                style = MaterialTheme.typography.bodySmall,
                color = TvTextDim,
            )
        } else {
            Text(
                "Dashboard only works on same WiFi.",
                style = MaterialTheme.typography.bodySmall,
                color = TvTextDim,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- Debug ---
        Text("Debug", style = MaterialTheme.typography.titleMedium, color = TvWarning)
        Spacer(modifier = Modifier.height(8.dp))

        Text("Offline sim: ${OfflineSimulator.isOffline}", style = MaterialTheme.typography.bodySmall, color = TvTextDim)

        Spacer(modifier = Modifier.height(12.dp))

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsBtn("Reset PIN") {
                val newPin = ServiceLocator.pinManager.resetPin()
                ServiceLocator.sessionManager.invalidateAll()
                // Rotate tv-secret on PIN reset (invalidates all remote access)
                try {
                    ServiceLocator.relayConfig.rotateTvSecret()
                    if (relayEnabled) {
                        ServiceLocator.relayConnector.reconnectNow()
                    }
                } catch (_: Exception) {}
                displayPin = newPin
                sessionCount = 0
            }
            SettingsBtn("Clear Sessions") {
                ServiceLocator.sessionManager.invalidateAll()
                sessionCount = 0
            }
            SettingsBtn("Clear Events") { PlayEventRecorder.clearAll() }
            SettingsBtn(if (OfflineSimulator.isOffline) "Go Online" else "Simulate Offline") {
                OfflineSimulator.toggle()
            }
            SettingsBtn(if (showLog) "Hide Log" else "Show Log") { showLog = !showLog }
            SettingsBtn("Clear Log") { AppLogger.clear() }
        }

        if (showLog) {
            Spacer(modifier = Modifier.height(12.dp))
            LogPanel()
        }
    }
}

@Composable
private fun SettingsBtn(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = TvPrimary),
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(text, color = TvText, style = MaterialTheme.typography.bodySmall)
    }
}
