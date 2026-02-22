package tv.parentapproved.app.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import tv.parentapproved.app.BuildConfig
import tv.parentapproved.app.ServiceLocator
import tv.parentapproved.app.relay.RelayConnectionState
import tv.parentapproved.app.ui.theme.KidAccent
import tv.parentapproved.app.ui.theme.KidBackground
import tv.parentapproved.app.ui.theme.KidSurface
import tv.parentapproved.app.ui.theme.KidText
import tv.parentapproved.app.ui.theme.KidTextDim
import tv.parentapproved.app.ui.theme.NunitoSans
import tv.parentapproved.app.ui.theme.OverscanPadding
import tv.parentapproved.app.ui.theme.StatusSuccess
import tv.parentapproved.app.ui.theme.StatusWarning
import tv.parentapproved.app.util.NetworkUtils
import tv.parentapproved.app.util.QrCodeGenerator
import kotlinx.coroutines.delay

@Composable
fun ConnectScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    var ip by remember { mutableStateOf<String?>(null) }
    var localQrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var relayQrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    val pin = remember { ServiceLocator.pinManager.getCurrentPin() }
    var relayEnabled by remember { mutableStateOf(ServiceLocator.isRelayEnabled()) }

    val relayConfig = remember {
        if (ServiceLocator.isInitialized()) ServiceLocator.relayConfig else null
    }
    val relayConnector = remember {
        if (ServiceLocator.isInitialized()) try { ServiceLocator.relayConnector } catch (e: Exception) { null } else null
    }

    LaunchedEffect(Unit) {
        ip = NetworkUtils.getDeviceIp(context)

        ip?.let { address ->
            val localUrl = NetworkUtils.buildConnectUrl(address) + "?pin=$pin"
            localQrBitmap = QrCodeGenerator.generate(localUrl)
        }

        if (relayEnabled) {
            relayConfig?.let { config ->
                val relayUrl = "${config.relayUrl}/tv/${config.tvId}/?pin=$pin"
                relayQrBitmap = QrCodeGenerator.generate(relayUrl)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(KidBackground)
    ) {
        if (showSettings) {
            ConnectSettingsPanel(
                ip = ip,
                pin = pin,
                relayEnabled = relayEnabled,
                localQrBitmap = localQrBitmap,
                relayConfig = relayConfig,
                relayConnector = relayConnector,
                onClose = {
                    // Re-read relay state so QR code reflects any toggle change
                    relayEnabled = ServiceLocator.isRelayEnabled()
                    // Regenerate relay QR if newly enabled
                    if (relayEnabled && relayQrBitmap == null) {
                        relayConfig?.let { config ->
                            relayQrBitmap = QrCodeGenerator.generate("${config.relayUrl}/tv/${config.tvId}/?pin=$pin")
                        }
                    }
                    showSettings = false
                },
                onToggleRelay = { enabled ->
                    ServiceLocator.setRelayEnabled(enabled)
                    relayEnabled = enabled
                },
            )
        } else {
            // Two-column layout: QR left, info right
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(OverscanPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Left column: QR code
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (relayEnabled && relayQrBitmap != null) {
                        Image(
                            bitmap = relayQrBitmap!!.asImageBitmap(),
                            contentDescription = "QR code to connect via relay",
                            modifier = Modifier.size(240.dp),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Scan with your phone\u2019s camera",
                            style = MaterialTheme.typography.bodyMedium,
                            color = KidText,
                        )
                    } else if (localQrBitmap != null) {
                        Image(
                            bitmap = localQrBitmap!!.asImageBitmap(),
                            contentDescription = "QR code to connect on same WiFi",
                            modifier = Modifier.size(240.dp),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Scan with your phone\u2019s camera",
                            style = MaterialTheme.typography.bodyMedium,
                            color = KidText,
                        )
                    } else {
                        Text(
                            text = "Looking for network...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = KidTextDim,
                        )
                    }
                }

                Spacer(modifier = Modifier.width(32.dp))

                // Right column: branding, PIN, info
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    // Wordmark
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "ParentApproved",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            fontFamily = NunitoSans,
                            color = KidText,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = KidAccent,
                            modifier = Modifier.size(28.dp),
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "One-time setup",
                        style = MaterialTheme.typography.bodySmall,
                        color = KidTextDim,
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // PIN box
                    Box(
                        modifier = Modifier
                            .border(2.dp, KidAccent, RoundedCornerShape(12.dp))
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Or enter:",
                                style = MaterialTheme.typography.bodySmall,
                                color = KidTextDim,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = pin,
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = KidAccent,
                                letterSpacing = 8.sp,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Secondary info
                    if (relayEnabled) {
                        ip?.let { address ->
                            Text(
                                text = "Local: ${NetworkUtils.buildConnectUrl(address)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = KidTextDim,
                            )
                        }
                    } else {
                        Text(
                            text = "Enable Remote Access in Settings for anywhere access",
                            style = MaterialTheme.typography.bodySmall,
                            color = KidTextDim,
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Charityware
                    Text(
                        text = "ParentApproved.tv is free, forever.",
                        style = MaterialTheme.typography.bodySmall,
                        color = KidTextDim,
                    )
                    Text(
                        text = "If it\u2019s been useful to your family,",
                        style = MaterialTheme.typography.bodySmall,
                        color = KidTextDim,
                    )
                    Text(
                        text = "consider supporting loving-kindness meditation.",
                        style = MaterialTheme.typography.bodySmall,
                        color = KidTextDim,
                    )
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

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = onBack,
                        colors = ButtonDefaults.buttonColors(containerColor = KidSurface),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text("Back", color = KidText, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Update-available banner
                    if (ServiceLocator.isInitialized() && ServiceLocator.updateChecker.isUpdateAvailable) {
                        val latest = ServiceLocator.updateChecker.latestVersion
                        Text(
                            text = "Update available: v${latest?.latest} — visit parentapproved.tv",
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusWarning,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // Version (always shown)
                    val versionSuffix = if (BuildConfig.IS_DEBUG) "-debug" else ""
                    Text(
                        text = "v${BuildConfig.VERSION_NAME}$versionSuffix",
                        style = MaterialTheme.typography.bodySmall,
                        color = KidTextDim,
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
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = "Settings",
                    tint = KidTextDim,
                    modifier = Modifier.size(24.dp),
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
    relayConfig: tv.parentapproved.app.relay.RelayConfig?,
    relayConnector: tv.parentapproved.app.relay.RelayConnector?,
    onClose: () -> Unit,
    onToggleRelay: (Boolean) -> Unit,
) {
    var debugQrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var localRelayEnabled by remember { mutableStateOf(relayEnabled) }

    // Poll relay status while panel is open
    var relayState by remember { mutableStateOf(
        try { relayConnector?.state } catch (_: Exception) { null }
    ) }
    LaunchedEffect(localRelayEnabled) {
        if (localRelayEnabled) {
            while (true) {
                delay(2000)
                relayState = try { relayConnector?.state } catch (_: Exception) { null }
            }
        }
    }

    LaunchedEffect(ip) {
        ip?.let { address ->
            val debugUrl = "http://$address:8080/debug/"
            debugQrBitmap = QrCodeGenerator.generate(debugUrl)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KidBackground)
            .padding(OverscanPadding)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            color = KidText,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text("TV Info", style = MaterialTheme.typography.titleMedium, color = KidText)
        Spacer(modifier = Modifier.height(4.dp))
        relayConfig?.let {
            Text("TV ID: ${it.tvId}", style = MaterialTheme.typography.bodySmall, color = KidTextDim)
        }
        Text(
            "Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            style = MaterialTheme.typography.bodySmall,
            color = KidTextDim,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text("Remote Access", style = MaterialTheme.typography.titleMedium, color = KidText)
        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                localRelayEnabled = !localRelayEnabled
                onToggleRelay(localRelayEnabled)
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (localRelayEnabled) KidSurface else KidAccent,
            ),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(
                if (localRelayEnabled) "Disable Remote Access" else "Enable Remote Access",
                color = if (localRelayEnabled) KidText else KidBackground,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (localRelayEnabled) {
            relayConfig?.let {
                Text("Relay: ${it.relayUrl}", style = MaterialTheme.typography.bodySmall, color = KidTextDim)
            }
            relayState?.let { state ->
                val statusText = when (state) {
                    RelayConnectionState.CONNECTED -> "Connected"
                    RelayConnectionState.CONNECTING -> "Connecting..."
                    RelayConnectionState.DISCONNECTED -> "Disconnected"
                }
                val statusColor = when (state) {
                    RelayConnectionState.CONNECTED -> StatusSuccess
                    RelayConnectionState.CONNECTING -> StatusWarning
                    RelayConnectionState.DISCONNECTED -> KidTextDim
                }
                Text("Status: $statusText", style = MaterialTheme.typography.bodySmall, color = statusColor)
            }
            Text(
                "Dashboard works from anywhere when enabled.",
                style = MaterialTheme.typography.bodySmall,
                color = KidTextDim,
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text("Local connection (same WiFi):", style = MaterialTheme.typography.bodySmall, color = KidTextDim)
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
                    color = KidTextDim,
                )
            }
        } else {
            Text(
                "Dashboard only works on same WiFi.",
                style = MaterialTheme.typography.bodySmall,
                color = KidTextDim,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Current PIN", style = MaterialTheme.typography.titleMedium, color = KidText)
        Spacer(modifier = Modifier.height(4.dp))
        Text(pin, style = MaterialTheme.typography.bodyLarge, color = KidAccent, fontFamily = FontFamily.Monospace)

        Spacer(modifier = Modifier.height(16.dp))

        if (debugQrBitmap != null) {
            Text("Debug (local only)", style = MaterialTheme.typography.titleMedium, color = StatusWarning)
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
                    color = KidTextDim,
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onClose,
            colors = ButtonDefaults.buttonColors(containerColor = KidSurface),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text("Close", color = KidText, fontWeight = FontWeight.SemiBold)
        }
    }
}
