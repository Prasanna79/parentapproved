package com.kidswatch.tv.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import com.kidswatch.tv.ui.theme.OverscanPadding
import com.kidswatch.tv.ui.theme.TvAccent
import com.kidswatch.tv.ui.theme.TvPrimary
import com.kidswatch.tv.ui.theme.TvBackground
import com.kidswatch.tv.ui.theme.TvText
import com.kidswatch.tv.ui.theme.TvTextDim
import com.kidswatch.tv.util.NetworkUtils
import com.kidswatch.tv.util.QrCodeGenerator

@Composable
fun ConnectScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    var ip by remember { mutableStateOf<String?>(null) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val pin = remember { ServiceLocator.pinManager.getCurrentPin() }

    LaunchedEffect(Unit) {
        ip = NetworkUtils.getDeviceIp(context)
        ip?.let { address ->
            val url = NetworkUtils.buildConnectUrl(address)
            qrBitmap = QrCodeGenerator.generate(url)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TvBackground)
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

        if (qrBitmap != null) {
            Image(
                bitmap = qrBitmap!!.asImageBitmap(),
                contentDescription = "QR code to connect",
                modifier = Modifier.size(160.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = ip?.let { NetworkUtils.buildConnectUrl(it) } ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = TvTextDim,
            )
        } else {
            Text(
                text = "Connect to WiFi to get started",
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

        Text(
            text = "Scan the QR code or visit the URL above",
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
}
