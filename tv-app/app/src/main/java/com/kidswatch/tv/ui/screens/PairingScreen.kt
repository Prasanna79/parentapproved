package com.kidswatch.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.ListenerRegistration
import com.kidswatch.tv.BuildConfig
import com.kidswatch.tv.data.FirebaseManager
import com.kidswatch.tv.data.PairingState
import com.kidswatch.tv.ui.theme.TvAccent
import com.kidswatch.tv.ui.theme.TvBackground
import com.kidswatch.tv.ui.theme.TvSuccess
import com.kidswatch.tv.ui.theme.TvText
import com.kidswatch.tv.ui.theme.TvTextDim
import com.kidswatch.tv.util.AppLogger
import kotlinx.coroutines.delay

@Composable
fun PairingScreen(onPaired: (String) -> Unit) {
    var state by remember { mutableStateOf<PairingState>(PairingState.Loading) }
    var listener by remember { mutableStateOf<ListenerRegistration?>(null) }

    fun generateCode(): String {
        val letters = ('A'..'Z').shuffled().take(4).joinToString("")
        val digits = (1..4).map { (0..9).random() }.joinToString("")
        return "$letters-$digits"
    }

    fun setupPairing() {
        val code = generateCode()
        AppLogger.log("Generated pairing code: $code")

        // Check collision then create
        FirebaseManager.checkPairingCodeExists(code) { exists ->
            if (exists) {
                AppLogger.warn("Code collision, regenerating")
                setupPairing()
                return@checkPairingCodeExists
            }

            FirebaseManager.getFcmToken { token ->
                FirebaseManager.createDeviceDoc(code, token) { success ->
                    if (success) {
                        state = PairingState.Unpaired(code)
                        // Listen for pairing
                        listener = FirebaseManager.listenDeviceDoc { data ->
                            val familyId = data?.get("family_id") as? String
                            if (familyId != null) {
                                AppLogger.success("Paired! familyId=$familyId")
                                state = PairingState.Paired(familyId)
                                onPaired(familyId)
                            }
                        }
                    } else {
                        AppLogger.error("Failed to create device doc")
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        // Check if already paired
        val existingListener = FirebaseManager.listenDeviceDoc { data ->
            if (data != null) {
                val familyId = data["family_id"] as? String
                if (familyId != null) {
                    state = PairingState.Paired(familyId)
                    onPaired(familyId)
                } else {
                    val existingCode = data["pairing_code"] as? String ?: ""
                    state = PairingState.Unpaired(existingCode)
                }
            } else {
                setupPairing()
            }
        }
        listener = existingListener
    }

    // Auto-regenerate code after 1 hour
    LaunchedEffect(state) {
        if (state is PairingState.Unpaired) {
            delay(60 * 60 * 1000L) // 1 hour
            AppLogger.log("Code expired, regenerating")
            listener?.remove()
            setupPairing()
        }
    }

    DisposableEffect(Unit) {
        onDispose { listener?.remove() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TvBackground),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (val s = state) {
            is PairingState.Loading -> {
                Text("Setting up...", style = MaterialTheme.typography.headlineMedium, color = TvText)
            }
            is PairingState.Unpaired -> {
                Text(
                    text = "Go to kidswatch.app",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TvText,
                )
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = "Enter code:",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TvTextDim,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = s.code,
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = TvAccent,
                    letterSpacing = 8.sp,
                )
                Spacer(modifier = Modifier.height(48.dp))
                Text(
                    text = "This code expires in 1 hour",
                    style = MaterialTheme.typography.bodySmall,
                    color = TvTextDim,
                )
            }
            is PairingState.Paired -> {
                Text(
                    text = "Paired!",
                    style = MaterialTheme.typography.headlineLarge,
                    color = TvSuccess,
                )
            }
        }

        if (BuildConfig.IS_DEBUG) {
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "v${BuildConfig.VERSION_NAME}-debug",
                style = MaterialTheme.typography.bodySmall,
                color = TvTextDim,
            )
        }
    }
}
