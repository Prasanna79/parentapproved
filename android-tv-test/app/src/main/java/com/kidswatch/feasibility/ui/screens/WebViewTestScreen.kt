package com.kidswatch.feasibility.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.kidswatch.feasibility.ui.components.LogEntry
import com.kidswatch.feasibility.ui.components.LogLevel
import com.kidswatch.feasibility.ui.components.ResultLogPanel
import com.kidswatch.feasibility.ui.theme.TvAccent
import com.kidswatch.feasibility.ui.theme.TvBackground
import com.kidswatch.feasibility.ui.theme.TvPrimary
import com.kidswatch.feasibility.ui.theme.TvText
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.kidswatch.feasibility.util.WebViewConfigurator
import java.io.File

private const val YOUTUBE_HOME = "https://www.youtube.com"
private const val SIGN_IN_URL = "https://accounts.google.com/ServiceLogin?continue=https://www.youtube.com"
private const val TEST_VIDEO_URL = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
private const val TEST_PLAYLIST_URL = "https://www.youtube.com/playlist?list=PLRqwX-V7Uu6ZiZxtDDRCi6uhfTH4FilpH"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WebViewTestScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val logs = remember { mutableStateListOf<LogEntry>() }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var showWebView by remember { mutableStateOf(false) }
    var signedIn by remember { mutableStateOf(false) }
    var injected by remember { mutableStateOf(false) }
    var showLogs by remember { mutableStateOf(false) }

    fun log(message: String, level: LogLevel = LogLevel.INFO) {
        logs.add(LogEntry(message, level))
    }

    val jsBridge = remember {
        object {
            @JavascriptInterface
            fun onInjectionComplete(status: String) {
                logs.add(LogEntry("JS Bridge: $status", LogLevel.SUCCESS))
                injected = true
            }

            @JavascriptInterface
            fun onPremiumCheck(isPremium: Boolean, details: String) {
                val level = if (isPremium) LogLevel.SUCCESS else LogLevel.WARNING
                logs.add(LogEntry("Premium: $isPremium — $details", level))
            }

            @JavascriptInterface
            fun onPageInfo(title: String, url: String, cookieStatus: String) {
                logs.add(LogEntry("Page: $title", LogLevel.INFO))
                logs.add(LogEntry("URL: $url", LogLevel.INFO))
                logs.add(LogEntry("Cookies: $cookieStatus", LogLevel.INFO))
            }
        }
    }

    fun loadCookiesFromFile() {
        log("--- Loading cookies from file ---")
        val cookieFile = File("/data/local/tmp/yt_cookies.json")
        if (!cookieFile.exists()) {
            log("No cookie file at /data/local/tmp/yt_cookies.json", LogLevel.ERROR)
            log("Run push-cookies.sh on your Mac first!", LogLevel.ERROR)
            return
        }
        try {
            val json = cookieFile.readText()
            val type = object : TypeToken<List<Map<String, Any>>>() {}.type
            val cookies: List<Map<String, Any>> = Gson().fromJson(json, type)
            val cm = CookieManager.getInstance()
            cm.setAcceptCookie(true)
            var count = 0
            for (cookie in cookies) {
                val url = cookie["url"] as? String ?: continue
                val name = cookie["name"] as? String ?: continue
                val value = cookie["value"] as? String ?: continue
                val domain = cookie["domain"] as? String ?: ""
                val path = cookie["path"] as? String ?: "/"
                val secure = cookie["secure"] as? Boolean ?: false

                var cookieStr = "$name=$value; Domain=$domain; Path=$path"
                if (secure) cookieStr += "; Secure"

                cm.setCookie(url, cookieStr)
                count++
            }
            cm.flush()
            log("Loaded $count cookies into CookieManager", LogLevel.SUCCESS)
        } catch (e: Exception) {
            log("Failed to load cookies: ${e.message}", LogLevel.ERROR)
        }
    }

    fun initWebView() {
        log("--- Loading YouTube ---")
        showWebView = true
        log("Opening YouTube (cookies should be pre-loaded via adb)...")
    }

    fun startSignIn() {
        val wv = webViewRef
        if (wv == null) {
            log("Load YouTube first", LogLevel.ERROR)
            return
        }
        log("--- WebView Sign-in Flow ---")
        log("Navigating to Google sign-in...")
        wv.loadUrl(SIGN_IN_URL)
    }

    fun navigateToVideo() {
        val wv = webViewRef
        if (wv == null) { log("Load YouTube first", LogLevel.ERROR); return }
        log("--- Video ---")
        wv.loadUrl(TEST_VIDEO_URL)
    }

    fun loadPlaylist() {
        val wv = webViewRef
        if (wv == null) { log("Load YouTube first", LogLevel.ERROR); return }
        log("--- Playlist ---")
        wv.loadUrl(TEST_PLAYLIST_URL)
    }

    fun injectUiHiding() {
        val wv = webViewRef
        if (wv == null) { log("Load YouTube first", LogLevel.ERROR); return }
        log("--- Injecting CSS/JS ---")
        wv.evaluateJavascript(WebViewConfigurator.buildInjectionScript(), null)
    }

    fun checkCookies() {
        log("--- Cookie Check ---")
        val ytCookies = CookieManager.getInstance().getCookie("https://www.youtube.com")
        if (ytCookies != null) {
            log("YouTube cookies: ${ytCookies.length} chars", LogLevel.SUCCESS)
            val hasSID = ytCookies.contains("SID=")
            val hasHSID = ytCookies.contains("HSID=")
            val hasLOGIN = ytCookies.contains("LOGIN_INFO=")
            log("SID=${if (hasSID) "Y" else "N"} HSID=${if (hasHSID) "Y" else "N"} LOGIN=${if (hasLOGIN) "Y" else "N"}")
            if (hasSID && hasHSID) {
                log("Signed in!", LogLevel.SUCCESS)
                signedIn = true
            } else {
                log("Auth cookies incomplete", LogLevel.WARNING)
            }
        } else {
            log("No YouTube cookies", LogLevel.WARNING)
        }
    }

    fun detectWebViewVersion() {
        val pkg = WebViewCompat.getCurrentWebViewPackage(context)
        if (pkg != null) {
            log("WebView: ${pkg.versionName} (${pkg.packageName})", LogLevel.SUCCESS)
        } else {
            log("WebView version unknown", LogLevel.WARNING)
        }
    }

    DisposableEffect(Unit) { onDispose { webViewRef?.destroy() } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TvBackground)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        // Compact header
        Text(
            text = "Test 2: WebView + YouTube",
            style = MaterialTheme.typography.bodyLarge,
            color = TvText,
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Compact button bar using FlowRow
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SmallButton("Import", TvAccent) { loadCookiesFromFile() }
            SmallButton("YouTube", TvPrimary) { initWebView() }
            SmallButton("Sign In", TvPrimary) { startSignIn() }
            SmallButton("Cookies", TvPrimary) { checkCookies() }
            SmallButton("Video", TvPrimary) { navigateToVideo() }
            SmallButton("Playlist", TvPrimary) { loadPlaylist() }
            SmallButton("Inject", TvAccent) { injectUiHiding() }
            SmallButton("WebView?", TvPrimary) { detectWebViewVersion() }
            SmallButton(if (showLogs) "Hide Log" else "Show Log", TvPrimary.copy(alpha = 0.6f)) {
                showLogs = !showLogs
            }
            SmallButton("Back", TvPrimary.copy(alpha = 0.4f)) { onBack() }
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (showWebView) {
            @SuppressLint("SetJavaScriptEnabled")
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        WebViewConfigurator.configureWebView(this)

                        if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
                            WebSettingsCompat.setRequestedWithHeaderOriginAllowList(settings, emptySet())
                        }

                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                        addJavascriptInterface(jsBridge, "KidsWatchBridge")

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                logs.add(LogEntry("Loading: ${url?.take(60)}", LogLevel.INFO))
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                logs.add(LogEntry("Loaded: ${url?.take(60)}", LogLevel.SUCCESS))
                                if (url != null && url.contains("youtube.com") && !url.contains("accounts.google.com")) {
                                    if (!signedIn) {
                                        signedIn = true
                                    }
                                }
                            }

                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val url = request?.url?.toString() ?: return false
                                if (!WebViewConfigurator.isAllowedUrl(url)) {
                                    logs.add(LogEntry("Blocked: ${url.take(50)}", LogLevel.WARNING))
                                    return true
                                }
                                return false
                            }
                        }

                        webChromeClient = WebChromeClient()
                        webViewRef = this
                        loadUrl(YOUTUBE_HOME)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }

        // Log panel — collapsible, only takes space when shown
        if (showLogs) {
            ResultLogPanel(
                logs = logs,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (showWebView) 100.dp else 300.dp),
            )
        }

        if (!showWebView && !showLogs) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun SmallButton(
    text: String,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = color),
        shape = RoundedCornerShape(6.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        modifier = androidx.compose.ui.Modifier.height(32.dp),
    ) {
        Text(text, color = TvText, fontSize = 11.sp)
    }
}
