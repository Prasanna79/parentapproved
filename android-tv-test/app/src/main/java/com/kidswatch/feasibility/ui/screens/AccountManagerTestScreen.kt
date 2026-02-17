package com.kidswatch.feasibility.ui.screens

import android.Manifest
import android.accounts.Account
import android.accounts.AccountManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.kidswatch.feasibility.ui.components.LogEntry
import com.kidswatch.feasibility.ui.components.LogLevel
import com.kidswatch.feasibility.ui.components.ResultLogPanel
import com.kidswatch.feasibility.ui.theme.TvAccent
import com.kidswatch.feasibility.ui.theme.TvBackground
import com.kidswatch.feasibility.ui.theme.TvPrimary
import com.kidswatch.feasibility.ui.theme.TvText
import com.kidswatch.feasibility.util.CookieConverter
import com.kidswatch.feasibility.util.YouTubeApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AccountManagerTestScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val logs = remember { mutableStateListOf<LogEntry>() }
    var selectedAccount by remember { mutableStateOf<Account?>(null) }
    var currentToken by remember { mutableStateOf<String?>(null) }

    fun log(message: String, level: LogLevel = LogLevel.INFO) {
        logs.add(LogEntry(message, level))
    }

    val accountManager = remember { AccountManager.get(context) }
    val youTubeClient = remember { YouTubeApiClient() }
    val cookieConverter = remember { CookieConverter() }

    // Account picker launcher (API 26+)
    val accountPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val accountName = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
        val accountType = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE)
        if (accountName != null && accountType != null) {
            selectedAccount = Account(accountName, accountType)
            log("Account selected: $accountName", LogLevel.SUCCESS)
        } else {
            log("Account picker cancelled or failed", LogLevel.WARNING)
        }
    }

    // Permission launcher for GET_ACCOUNTS (API 24-25)
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            log("GET_ACCOUNTS permission granted", LogLevel.SUCCESS)
            @Suppress("DEPRECATION")
            val accounts = accountManager.getAccountsByType("com.google")
            log("Found ${accounts.size} Google account(s)")
            accounts.forEach { log("  - ${it.name}") }
            if (accounts.isNotEmpty()) {
                selectedAccount = accounts[0]
                log("Auto-selected: ${accounts[0].name}", LogLevel.SUCCESS)
            }
        } else {
            log("GET_ACCOUNTS permission denied", LogLevel.ERROR)
        }
    }

    fun discoverAccounts() {
        log("--- Account Discovery ---")
        log("Android API level: ${Build.VERSION.SDK_INT}")

        if (Build.VERSION.SDK_INT >= 26) {
            log("Using newChooseAccountIntent() (API 26+)")
            val intent = AccountManager.newChooseAccountIntent(
                null, null, arrayOf("com.google"), null, null, null, null
            )
            accountPickerLauncher.launch(intent)
        } else {
            log("Using getAccountsByType() with permission (API <26)")
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.GET_ACCOUNTS)
                == PackageManager.PERMISSION_GRANTED
            ) {
                @Suppress("DEPRECATION")
                val accounts = accountManager.getAccountsByType("com.google")
                log("Found ${accounts.size} Google account(s)")
                if (accounts.isNotEmpty()) {
                    selectedAccount = accounts[0]
                    log("Selected: ${accounts[0].name}", LogLevel.SUCCESS)
                }
            } else {
                log("Requesting GET_ACCOUNTS permission...")
                permissionLauncher.launch(Manifest.permission.GET_ACCOUNTS)
            }
        }
    }

    fun requestToken() {
        val account = selectedAccount
        if (account == null) {
            log("No account selected. Discover accounts first.", LogLevel.ERROR)
            return
        }

        log("--- Token Request ---")
        log("Requesting YouTube OAuth token for ${account.name}...")
        log("Scope: oauth2:https://www.googleapis.com/auth/youtube.readonly")

        accountManager.getAuthToken(
            account,
            "oauth2:https://www.googleapis.com/auth/youtube.readonly",
            Bundle(),
            context as android.app.Activity,
            { future ->
                try {
                    val result = future.result
                    val token = result.getString(AccountManager.KEY_AUTHTOKEN)
                    if (token != null) {
                        currentToken = token
                        log("Token obtained! (${token.take(20)}...)", LogLevel.SUCCESS)
                        log("Token length: ${token.length}")
                    } else {
                        val intent = result.getParcelable<android.content.Intent>(AccountManager.KEY_INTENT)
                        if (intent != null) {
                            log("User consent required — launching consent activity", LogLevel.WARNING)
                            context.startActivity(intent)
                        } else {
                            log("No token and no consent intent returned", LogLevel.ERROR)
                        }
                    }
                } catch (e: Exception) {
                    log("Token request failed: ${e.message}", LogLevel.ERROR)
                    log("Exception type: ${e.javaClass.simpleName}", LogLevel.ERROR)
                }
            },
            null,
        )
    }

    fun callYouTubeApi() {
        val token = currentToken
        if (token == null) {
            log("No token available. Request token first.", LogLevel.ERROR)
            return
        }

        log("--- YouTube API Call ---")
        log("Calling /playlists?part=snippet&mine=true ...")

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                youTubeClient.fetchMyPlaylists(token)
            }
            if (result.success) {
                log("API call successful!", LogLevel.SUCCESS)
                if (result.playlistNames.isEmpty()) {
                    log("No playlists found (account may have none)")
                } else {
                    log("Found ${result.playlistNames.size} playlist(s):")
                    result.playlistNames.forEach { name -> log("  - $name", LogLevel.SUCCESS) }
                }
            } else {
                log("API call failed: ${result.error}", LogLevel.ERROR)
            }
        }
    }

    fun tryMergeSession() {
        val token = currentToken
        if (token == null) {
            log("No token available for MergeSession experiment.", LogLevel.ERROR)
            return
        }

        log("--- MergeSession Experiment (Bonus) ---")
        log("This uses undocumented Google endpoints. Expect failure.")

        scope.launch {
            val uberResult = withContext(Dispatchers.IO) {
                cookieConverter.getUberToken(token)
            }
            if (uberResult.success) {
                log("UberToken obtained: ${uberResult.rawResponse?.take(30)}...", LogLevel.SUCCESS)
                val mergeResult = withContext(Dispatchers.IO) {
                    cookieConverter.mergeSession(uberResult.rawResponse!!)
                }
                if (mergeResult.success) {
                    log("MergeSession succeeded!", LogLevel.SUCCESS)
                    mergeResult.cookies.forEach { (k, v) ->
                        log("  Cookie: $k = ${v.take(20)}...", LogLevel.SUCCESS)
                    }
                } else {
                    log("MergeSession failed: ${mergeResult.error}", LogLevel.WARNING)
                }
            } else {
                log("OAuthLogin failed: ${uberResult.error}", LogLevel.WARNING)
                log("This is expected — endpoint may be restricted", LogLevel.INFO)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TvBackground)
            .padding(32.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = "Test 1: AccountManager Token",
            style = MaterialTheme.typography.headlineMedium,
            color = TvText,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Validate OS-level Google account → YouTube OAuth token flow",
            style = MaterialTheme.typography.bodyMedium,
            color = TvText.copy(alpha = 0.7f),
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row {
            Button(
                onClick = { discoverAccounts() },
                colors = ButtonDefaults.buttonColors(containerColor = TvPrimary),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("1. Discover Accounts", color = TvText)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Button(
                onClick = { requestToken() },
                colors = ButtonDefaults.buttonColors(containerColor = TvPrimary),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("2. Request Token", color = TvText)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Button(
                onClick = { callYouTubeApi() },
                colors = ButtonDefaults.buttonColors(containerColor = TvPrimary),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("3. Call YouTube API", color = TvText)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Button(
                onClick = { tryMergeSession() },
                colors = ButtonDefaults.buttonColors(containerColor = TvAccent),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("4. MergeSession (Bonus)", color = TvText)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Status summary
        selectedAccount?.let {
            Text("Account: ${it.name}", color = TvText, style = MaterialTheme.typography.bodyLarge)
        }
        currentToken?.let {
            Text(
                "Token: ${it.take(20)}... (${it.length} chars)",
                color = TvText,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        ResultLogPanel(logs = logs, modifier = Modifier.fillMaxWidth())

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onBack,
            colors = ButtonDefaults.buttonColors(containerColor = TvPrimary.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text("Back", color = TvText)
        }
    }
}
