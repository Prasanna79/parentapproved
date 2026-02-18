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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import tv.parentapproved.app.BuildConfig
import tv.parentapproved.app.ServiceLocator
import tv.parentapproved.app.data.events.PlayEventRecorder
import tv.parentapproved.app.ui.components.LogPanel
import tv.parentapproved.app.ui.theme.KidBackground
import tv.parentapproved.app.ui.theme.KidSurface
import tv.parentapproved.app.ui.theme.KidText
import tv.parentapproved.app.ui.theme.KidTextDim
import tv.parentapproved.app.ui.theme.OverscanPadding
import tv.parentapproved.app.ui.theme.StatusWarning
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

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(KidBackground)
            .padding(OverscanPadding),
    ) {
        // Left column: settings
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Settings", style = MaterialTheme.typography.headlineMedium, color = KidText)
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(containerColor = KidSurface),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("Back", color = KidText, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- General ---
            Text("General", style = MaterialTheme.typography.titleMedium, color = KidText)
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsBtn("Refresh Videos") { onRefresh() }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = KidTextDim)

            Spacer(modifier = Modifier.height(24.dp))

            // --- Connection ---
            Text("Connection", style = MaterialTheme.typography.titleMedium, color = KidText)
            Spacer(modifier = Modifier.height(8.dp))
            Text("PIN: $displayPin", style = MaterialTheme.typography.bodySmall, color = KidTextDim)
            Text("Active sessions: $sessionCount", style = MaterialTheme.typography.bodySmall, color = KidTextDim)

            Spacer(modifier = Modifier.height(24.dp))

            // --- Debug ---
            Text("Debug", style = MaterialTheme.typography.titleMedium, color = StatusWarning)
            Spacer(modifier = Modifier.height(8.dp))

            Text("Offline sim: ${OfflineSimulator.isOffline}", style = MaterialTheme.typography.bodySmall, color = KidTextDim)

            Spacer(modifier = Modifier.height(12.dp))

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsBtn("Reset PIN") {
                    val newPin = ServiceLocator.pinManager.resetPin()
                    ServiceLocator.sessionManager.invalidateAll()
                    try {
                        ServiceLocator.relayConfig.rotateTvSecret()
                        if (ServiceLocator.isRelayEnabled()) {
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

        Spacer(modifier = Modifier.width(32.dp))

        // Right column: charityware
        Column(
            modifier = Modifier
                .weight(0.4f)
                .padding(top = 64.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            Text(
                text = "ParentApproved.tv is free, forever.",
                style = MaterialTheme.typography.bodySmall,
                color = KidTextDim,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "If it\u2019s been useful to your family, consider supporting loving-kindness meditation.",
                style = MaterialTheme.typography.bodySmall,
                color = KidTextDim,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "India: mettavipassana.org/donate",
                style = MaterialTheme.typography.bodySmall,
                color = KidTextDim,
            )
            Text(
                text = "Worldwide: donate to a Buddhist charity near you.",
                style = MaterialTheme.typography.bodySmall,
                color = KidTextDim,
            )
        }
    }
}

@Composable
private fun SettingsBtn(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = KidSurface),
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(text, color = KidText, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}
