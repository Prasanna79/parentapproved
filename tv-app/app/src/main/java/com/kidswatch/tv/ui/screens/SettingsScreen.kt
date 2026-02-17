package com.kidswatch.tv.ui.screens

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kidswatch.tv.BuildConfig
import com.kidswatch.tv.ServiceLocator
import com.kidswatch.tv.data.events.PlayEventRecorder
import com.kidswatch.tv.ui.components.LogPanel
import com.kidswatch.tv.ui.theme.TvBackground
import com.kidswatch.tv.ui.theme.TvPrimary
import com.kidswatch.tv.ui.theme.TvText
import com.kidswatch.tv.ui.theme.TvTextDim
import com.kidswatch.tv.ui.theme.TvWarning
import com.kidswatch.tv.util.AppLogger
import com.kidswatch.tv.util.OfflineSimulator

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    var showLog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TvBackground)
            .padding(24.dp)
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
        Text("PIN: ${ServiceLocator.pinManager.getCurrentPin()}", style = MaterialTheme.typography.bodySmall, color = TvTextDim)
        Text("Active sessions: ${ServiceLocator.sessionManager.getActiveSessionCount()}", style = MaterialTheme.typography.bodySmall, color = TvTextDim)

        Spacer(modifier = Modifier.height(24.dp))

        // --- Debug ---
        Text("Debug", style = MaterialTheme.typography.titleMedium, color = TvWarning)
        Spacer(modifier = Modifier.height(8.dp))

        Text("Offline sim: ${OfflineSimulator.isOffline}", style = MaterialTheme.typography.bodySmall, color = TvTextDim)

        Spacer(modifier = Modifier.height(12.dp))

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsBtn("Reset PIN") { ServiceLocator.pinManager.resetPin() }
            SettingsBtn("Clear Sessions") { ServiceLocator.sessionManager.invalidateAll() }
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
