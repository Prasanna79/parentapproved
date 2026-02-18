package com.kidswatch.tv.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kidswatch.tv.BuildConfig
import com.kidswatch.tv.ServiceLocator
import com.kidswatch.tv.relay.RelayConnectionState
import com.kidswatch.tv.ui.theme.OverscanPadding
import com.kidswatch.tv.ui.theme.TvAccent
import com.kidswatch.tv.ui.theme.TvPrimary
import com.kidswatch.tv.ui.theme.TvBackground
import com.kidswatch.tv.ui.theme.TvText
import com.kidswatch.tv.ui.theme.TvTextDim
import com.kidswatch.tv.ui.theme.TvWarning
import com.kidswatch.tv.util.NetworkUtils
import com.kidswatch.tv.util.QrCodeGenerator

@Composable
fun ConnectScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    var ip by remember { mutableStateOf<String?>(null) }
    var localQrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var relayQrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    val pin = remember { ServiceLocator.pinManager.getCurrentPin() }
    val relayEnabled = remember { ServiceLocator.isRelayEnabled() }

    // Relay info
    val relayConfig = remember {
        if (ServiceLocator.isInitialized()) ServiceLocator.relayConfig else null
    }
    val relayConnector = remember {
        if (ServiceLocator.isInitialized()) try { ServiceLocator.relayConnector } catch (e: Exception) { null } else null
    }

    LaunchedEffect(Unit) {
        ip = NetworkUtils.getDeviceIp(context)

        // Generate local QR code
        ip?.let { address ->
            val localUrl = NetworkUtils.buildConnectUrl(address) + "?pin=$pin"
            localQrBitmap = QrCodeGenerator.generate(localUrl)
        }

        // Generate relay QR code (only if relay is enabled)
        if (relayEnabled) {
            relayConfig?.let { config ->
                val relayUrl = "${config.relayUrl}/tv/${config.tvId}/connect?secret=${config.tvSecret}&pin=$pin"
                relayQrBitmap = QrCodeGenerator.generate(relayUrl)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TvBackground)
    ) {
        if (showSettings) {
            ConnectSettingsPanel(
                ip = ip,
                pin = pin,
                relayEnabled = relayEnabled,
                localQrBitmap = localQrBitmap,
                relayConfig = relayConfig,
                relayConnector = relayConnector,
                onClose = { showSettings = false },
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(OverscanPadding)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "Connect Your Phone",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TvText,
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (relayEnabled && relayQrBitmap != null) {
                    // Relay is enabled — show relay QR as primary
                    Image(
                        bitmap = relayQrBitmap!!.asImageBitmap(),
                        contentDescription = "QR code to connect via relay",
                        modifier = Modifier.size(200.dp),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Scan to connect from anywhere",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TvText,
                    )
                } else if (localQrBitmap != null) {
                    // Local only — show local QR as primary
                    Image(
                        bitmap = localQrBitmap!!.asImageBitmap(),
                        contentDescription = "QR code to connect on same WiFi",
                        modifier = Modifier.size(200.dp),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Scan to connect (same WiFi)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TvText,
                    )
                } else {
                    Text(
                        text = "Looking for network...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TvTextDim,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "PIN:",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TvTextDim,
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = pin,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = TvAccent,
                    letterSpacing = 8.sp,
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Secondary info
                if (relayEnabled) {
                    // Relay mode: show local IP as secondary
                    ip?.let { address ->
                        Text(
                            text = "Local: ${NetworkUtils.buildConnectUrl(address)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TvTextDim,
                        )
                    }
                } else {
                    // Local mode: hint about remote access
                    Text(
                        text = "Enable Remote Access in Settings for anywhere access",
                        style = MaterialTheme.typography.bodySmall,
                        color = TvTextDim,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Charityware note
                Text(
                    text = "KidsWatch is free, forever. If it\u2019s been useful to your family,",
                    style = MaterialTheme.typography.bodySmall,
                    color = TvTextDim,
                )
                Text(
                    text = "consider supporting loving-kindness meditation.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TvTextDim,
                )
                Text(
                    text = "India: mettavipassana.org/donate",
                    style = MaterialTheme.typography.bodySmall,
                    color = TvTextDim,
                )
                Text(
                    text = "Worldwide: donate to a Buddhist charity near you.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TvTextDim,
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(containerColor = TvPrimary),
                ) {
                    Text("Back", color = TvText)
                }

                if (BuildConfig.IS_DEBUG) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "v${BuildConfig.VERSION_NAME}-debug",
                        style = MaterialTheme.typography.bodySmall,
                        color = TvTextDim,
                    )
                }
            }

            // Settings gear icon (bottom-right)
            IconButton(
                onClick = { showSettings = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(OverscanPadding),
            ) {
                Text(
                    text = "\u2699",
                    fontSize = 24.sp,
                    color = TvTextDim,
                )
            }
        }
    }
}

@Composable
private fun ConnectSettingsPanel(
    ip: String?,
    pin: String,
    relayEnabled: Boolean,
    localQrBitmap: Bitmap?,
    relayConfig: com.kidswatch.tv.relay.RelayConfig?,
    relayConnector: com.kidswatch.tv.relay.RelayConnector?,
    onClose: () -> Unit,
) {
    var debugQrBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(ip) {
        ip?.let { address ->
            val debugUrl = "http://$address:8080/debug/"
            debugQrBitmap = QrCodeGenerator.generate(debugUrl)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TvBackground)
            .padding(OverscanPadding)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            color = TvText,
        )

        Spacer(modifier = Modifier.height(16.dp))

        // TV Info
        Text("TV Info", style = MaterialTheme.typography.titleMedium, color = TvText)
        Spacer(modifier = Modifier.height(4.dp))
        relayConfig?.let {
            Text("TV ID: ${it.tvId}", style = MaterialTheme.typography.bodySmall, color = TvTextDim)
        }
        Text(
            "Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            style = MaterialTheme.typography.bodySmall,
            color = TvTextDim,
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Relay status
        Text("Remote Access", style = MaterialTheme.typography.titleMedium, color = TvText)
        Spacer(modifier = Modifier.height(4.dp))
        if (relayEnabled) {
            relayConfig?.let {
                Text("Relay: ${it.relayUrl}", style = MaterialTheme.typography.bodySmall, color = TvTextDim)
            }
            relayConnector?.let {
                val statusText = when (it.state) {
                    RelayConnectionState.CONNECTED -> "Connected"
                    RelayConnectionState.CONNECTING -> "Connecting..."
                    RelayConnectionState.DISCONNECTED -> "Disconnected"
                }
                val statusColor = when (it.state) {
                    RelayConnectionState.CONNECTED -> TvAccent
                    RelayConnectionState.CONNECTING -> TvWarning
                    RelayConnectionState.DISCONNECTED -> TvTextDim
                }
                Text("Status: $statusText", style = MaterialTheme.typography.bodySmall, color = statusColor)
            }

            // When relay is on, show local QR here as secondary
            Spacer(modifier = Modifier.height(8.dp))
            Text("Local connection (same WiFi):", style = MaterialTheme.typography.bodySmall, color = TvTextDim)
            if (localQrBitmap != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Image(
                    bitmap = localQrBitmap.asImageBitmap(),
                    contentDescription = "Local QR code (same WiFi)",
                    modifier = Modifier.size(120.dp),
                )
            }
            ip?.let {
                Text(
                    NetworkUtils.buildConnectUrl(it),
                    style = MaterialTheme.typography.bodySmall,
                    color = TvTextDim,
                )
            }
        } else {
            Text(
                "Remote access is off. Enable it to connect from anywhere.",
                style = MaterialTheme.typography.bodySmall,
                color = TvTextDim,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { ServiceLocator.setRelayEnabled(true) },
                colors = ButtonDefaults.buttonColors(containerColor = TvAccent),
            ) {
                Text("Enable Remote Access", color = TvText)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Current PIN
        Text("Current PIN", style = MaterialTheme.typography.titleMedium, color = TvText)
        Spacer(modifier = Modifier.height(4.dp))
        Text(pin, style = MaterialTheme.typography.bodyLarge, color = TvAccent, fontFamily = FontFamily.Monospace)

        Spacer(modifier = Modifier.height(16.dp))

        // Debug QR code
        if (debugQrBitmap != null) {
            Text("Debug (local only)", style = MaterialTheme.typography.titleMedium, color = TvWarning)
            Spacer(modifier = Modifier.height(8.dp))
            Image(
                bitmap = debugQrBitmap!!.asImageBitmap(),
                contentDescription = "Debug QR code (local network)",
                modifier = Modifier.size(120.dp),
            )
            ip?.let {
                Text(
                    "http://$it:8080/debug/",
                    style = MaterialTheme.typography.bodySmall,
                    color = TvTextDim,
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onClose,
            colors = ButtonDefaults.buttonColors(containerColor = TvPrimary),
        ) {
            Text("Close", color = TvText)
        }
    }
}
