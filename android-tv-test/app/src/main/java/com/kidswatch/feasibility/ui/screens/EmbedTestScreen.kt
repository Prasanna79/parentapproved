package com.kidswatch.feasibility.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.kidswatch.feasibility.ui.components.LogEntry
import com.kidswatch.feasibility.ui.components.LogLevel
import com.kidswatch.feasibility.ui.components.ResultLogPanel
import com.kidswatch.feasibility.ui.theme.TvAccent
import com.kidswatch.feasibility.ui.theme.TvBackground
import com.kidswatch.feasibility.ui.theme.TvPrimary
import com.kidswatch.feasibility.ui.theme.TvText
import com.kidswatch.feasibility.ui.theme.TvSuccess
import com.kidswatch.feasibility.ui.theme.TvWarning

private const val TEST_VIDEO_ID = "dQw4w9WgXcQ"

/**
 * Comprehensive diagnostic HTML page that runs all embed tests inline.
 * Results reported via JS bridge AND visible in the page itself.
 */
private fun diagnosticHtml(): String = """
<!DOCTYPE html>
<html><head>
<meta name="viewport" content="width=device-width,initial-scale=1">
<style>
  * { margin:0; padding:0; box-sizing:border-box; }
  body { background:#111; color:#eee; font:12px/1.4 monospace; padding:8px; }
  h2 { color:#4CAF50; font-size:14px; margin:8px 0 4px; }
  .test { background:#1a1a2e; border:1px solid #333; border-radius:4px; padding:6px; margin:4px 0; }
  .test-title { color:#FF9800; font-weight:bold; }
  .pass { color:#4CAF50; } .fail { color:#E94560; } .info { color:#aaa; }
  iframe { width:100%; height:200px; border:1px solid #444; background:#000; }
  video { width:100%; max-height:150px; background:#000; }
  #log { background:#0a0a0a; border:1px solid #333; padding:4px; margin-top:8px;
         max-height:200px; overflow-y:auto; font-size:11px; white-space:pre-wrap; }
  button { background:#0F3460; color:#eee; border:none; padding:4px 8px; border-radius:3px;
           margin:2px; cursor:pointer; font-size:11px; }
  button:hover { background:#1a4a80; }
</style>
</head><body>

<h2>Embed Diagnostic - ${TEST_VIDEO_ID}</h2>

<!-- Test 1: Environment info -->
<div class="test" id="t1">
  <span class="test-title">1. Environment</span>
  <div id="env-info" class="info">Checking...</div>
</div>

<!-- Test 2: Media capabilities -->
<div class="test" id="t2">
  <span class="test-title">2. Media Capabilities</span>
  <div id="media-info" class="info">Checking...</div>
</div>

<!-- Test 3: Direct MP4 video tag -->
<div class="test" id="t3">
  <span class="test-title">3. Direct MP4 &lt;video&gt; tag</span>
  <div id="mp4-status" class="info">Loading...</div>
  <video id="mp4test" controls playsinline preload="auto"
    src="https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4">
  </video>
</div>

<!-- Test 4: YouTube embed standard domain (direct iframe) -->
<div class="test" id="t4">
  <span class="test-title">4. youtube.com/embed (iframe)</span>
  <div id="yt-std-status" class="info">Loading...</div>
  <iframe id="yt-std" src="https://www.youtube.com/embed/${TEST_VIDEO_ID}"
    allow="autoplay; encrypted-media" allowfullscreen></iframe>
</div>

<!-- Test 5: YouTube embed nocookie domain -->
<div class="test" id="t5">
  <span class="test-title">5. youtube-nocookie.com/embed (iframe)</span>
  <div id="yt-nc-status" class="info">Loading...</div>
  <iframe id="yt-nc" src="https://www.youtube-nocookie.com/embed/${TEST_VIDEO_ID}"
    allow="autoplay; encrypted-media" allowfullscreen></iframe>
</div>

<!-- Test 6: YouTube embed with origin param -->
<div class="test" id="t6">
  <span class="test-title">6. embed + origin param</span>
  <div id="yt-origin-status" class="info">Loading...</div>
  <iframe id="yt-origin"
    src="https://www.youtube.com/embed/${TEST_VIDEO_ID}?origin=https://www.youtube.com&enablejsapi=1"
    allow="autoplay; encrypted-media" allowfullscreen></iframe>
</div>

<!-- Test 7: YouTube IFrame Player API (JS-created) -->
<div class="test" id="t7">
  <span class="test-title">7. YT IFrame Player API (JS)</span>
  <div id="yt-api-status" class="info">Loading API...</div>
  <div id="yt-api-player"></div>
</div>

<!-- Test 8: YouTube embed with html5=1 -->
<div class="test" id="t8">
  <span class="test-title">8. embed + html5=1</span>
  <div id="yt-html5-status" class="info">Loading...</div>
  <iframe id="yt-html5"
    src="https://www.youtube.com/embed/${TEST_VIDEO_ID}?html5=1&rel=0"
    allow="autoplay; encrypted-media" allowfullscreen></iframe>
</div>

<!-- Test 9: YouTube watch page in iframe -->
<div class="test" id="t9">
  <span class="test-title">9. youtube.com/watch in iframe (likely X-Frame blocked)</span>
  <div id="yt-watch-status" class="info">Loading...</div>
  <iframe id="yt-watch"
    src="https://www.youtube.com/watch?v=${TEST_VIDEO_ID}"
    allow="autoplay; encrypted-media" allowfullscreen></iframe>
</div>

<!-- Test 10: Dump full error page DOM from embed -->
<div class="test" id="t10">
  <span class="test-title">10. Error page DOM dump</span>
  <div id="dom-dump" class="info">Click button after iframes load...</div>
  <button onclick="dumpAllIframes()">Dump iframe DOMs</button>
  <button onclick="dumpFullPage()">Dump this page DOM</button>
</div>

<!-- Log area -->
<div id="log"></div>

<script>
var B = window.KidsWatchEmbed;
function log(msg, level) {
  var el = document.getElementById('log');
  el.textContent += '[' + (level||'INFO') + '] ' + msg + '\n';
  el.scrollTop = el.scrollHeight;
  if (B) B.onLog(msg, level||'INFO');
}

// Test 1: Environment
(function() {
  var ua = navigator.userAgent;
  var info = 'UA: ' + ua + '\n';
  info += 'Platform: ' + navigator.platform + '\n';
  info += 'Cookies enabled: ' + navigator.cookieEnabled + '\n';
  info += 'WebGL: ';
  try {
    var c = document.createElement('canvas');
    var gl = c.getContext('webgl') || c.getContext('experimental-webgl');
    info += gl ? gl.getParameter(gl.RENDERER) : 'NOT SUPPORTED';
  } catch(e) { info += 'ERROR: ' + e.message; }
  document.getElementById('env-info').textContent = info;
  log('UA: ' + ua.substring(0,100));
})();

// Test 2: Media capabilities
(function() {
  var el = document.getElementById('media-info');
  var v = document.createElement('video');
  var codecs = [
    ['H.264 Baseline', 'video/mp4; codecs="avc1.42E01E"'],
    ['H.264 High', 'video/mp4; codecs="avc1.640028"'],
    ['VP8', 'video/webm; codecs="vp8"'],
    ['VP9', 'video/webm; codecs="vp9"'],
    ['AV1', 'video/mp4; codecs="av01.0.01M.08"'],
    ['AAC', 'audio/mp4; codecs="mp4a.40.2"'],
    ['Opus', 'audio/webm; codecs="opus"'],
  ];
  var result = '';
  codecs.forEach(function(c) {
    var support = v.canPlayType(c[1]);
    result += c[0] + ': ' + (support || 'NO') + '  ';
    log('Codec ' + c[0] + ': ' + (support || 'NO'));
  });
  el.textContent = result;

  // Also check MediaSource
  if (window.MediaSource) {
    var mseCodecs = [
      ['MSE H.264', 'video/mp4; codecs="avc1.640028"'],
      ['MSE VP9', 'video/webm; codecs="vp9"'],
      ['MSE AV1', 'video/mp4; codecs="av01.0.01M.08"'],
    ];
    mseCodecs.forEach(function(c) {
      var ok = MediaSource.isTypeSupported(c[1]);
      result += '\n' + c[0] + ': ' + ok;
      log('MSE ' + c[0] + ': ' + ok);
    });
    el.textContent = result;
  } else {
    log('MediaSource API: NOT AVAILABLE', 'WARN');
    result += '\nMediaSource: NOT AVAILABLE';
    el.textContent = result;
  }
})();

// Test 3: MP4 video events
(function() {
  var v = document.getElementById('mp4test');
  var el = document.getElementById('mp4-status');
  v.onloadeddata = function() { el.textContent = 'LOADED OK'; el.className='pass'; log('MP4: loaded OK','OK'); };
  v.onplay = function() { el.textContent = 'PLAYING'; el.className='pass'; log('MP4: playing','OK'); };
  v.onerror = function(e) { el.textContent = 'ERROR: ' + (v.error?v.error.message:'unknown'); el.className='fail'; log('MP4: error ' + (v.error?v.error.code:'?'), 'ERR'); };
  v.onstalled = function() { log('MP4: stalled', 'WARN'); };
  v.onwaiting = function() { log('MP4: waiting/buffering'); };
})();

// Test 4-6, 8-9: iframe load monitoring
['yt-std','yt-nc','yt-origin','yt-html5','yt-watch'].forEach(function(id) {
  var iframe = document.getElementById(id);
  var statusEl = document.getElementById(id + '-status');
  if (!iframe || !statusEl) return;
  iframe.onload = function() {
    statusEl.textContent = 'iframe onload fired';
    statusEl.className = 'info';
    log(id + ': iframe onload fired');
    // Try to read content (will fail cross-origin, but worth trying)
    try {
      var doc = iframe.contentDocument || iframe.contentWindow.document;
      var text = doc.body ? doc.body.innerText.substring(0,200) : 'no body';
      statusEl.textContent = 'Content: ' + text;
      if (text.indexOf('Error') >= 0) { statusEl.className = 'fail'; log(id+': '+text,'ERR'); }
      else { statusEl.className = 'pass'; log(id+': '+text,'OK'); }
    } catch(e) {
      statusEl.textContent = 'Cross-origin (expected): ' + e.message;
      log(id + ': cross-origin blocked (normal for working embed)');
    }
  };
  iframe.onerror = function(e) {
    statusEl.textContent = 'LOAD ERROR';
    statusEl.className = 'fail';
    log(id + ': load error', 'ERR');
  };
});

// Test 7: YouTube IFrame Player API
(function() {
  var tag = document.createElement('script');
  tag.src = 'https://www.youtube.com/iframe_api';
  document.head.appendChild(tag);
  window.onYouTubeIframeAPIReady = function() {
    log('YT IFrame API ready', 'OK');
    document.getElementById('yt-api-status').textContent = 'API loaded, creating player...';
    try {
      var player = new YT.Player('yt-api-player', {
        height: '200', width: '100%',
        videoId: '${TEST_VIDEO_ID}',
        playerVars: { rel: 0, modestbranding: 1, playsinline: 1 },
        events: {
          onReady: function(e) {
            document.getElementById('yt-api-status').textContent = 'Player READY';
            document.getElementById('yt-api-status').className = 'pass';
            log('YT API: player ready', 'OK');
          },
          onStateChange: function(e) {
            var states = {'-1':'unstarted','0':'ended','1':'playing','2':'paused','3':'buffering','5':'cued'};
            var name = states[e.data] || ('unknown:'+e.data);
            log('YT API state: ' + name);
          },
          onError: function(e) {
            document.getElementById('yt-api-status').textContent = 'ERROR: ' + e.data;
            document.getElementById('yt-api-status').className = 'fail';
            log('YT API error: ' + e.data, 'ERR');
          }
        }
      });
    } catch(ex) {
      document.getElementById('yt-api-status').textContent = 'EXCEPTION: ' + ex.message;
      document.getElementById('yt-api-status').className = 'fail';
      log('YT API exception: ' + ex.message, 'ERR');
    }
  };
  // Timeout fallback
  setTimeout(function() {
    if (!window.YT) {
      document.getElementById('yt-api-status').textContent = 'API FAILED TO LOAD (timeout)';
      document.getElementById('yt-api-status').className = 'fail';
      log('YT IFrame API: failed to load after 10s', 'ERR');
    }
  }, 10000);
})();

function dumpAllIframes() {
  var el = document.getElementById('dom-dump');
  el.textContent = '';
  ['yt-std','yt-nc','yt-origin','yt-html5','yt-watch'].forEach(function(id) {
    var iframe = document.getElementById(id);
    try {
      var doc = iframe.contentDocument || iframe.contentWindow.document;
      var html = doc.documentElement.outerHTML.substring(0,500);
      el.textContent += id + ':\n' + html + '\n---\n';
      log(id + ' DOM: ' + html.substring(0,200));
    } catch(e) {
      el.textContent += id + ': CROSS-ORIGIN (' + e.message + ')\n---\n';
      log(id + ': cross-origin, cannot read DOM');
    }
  });
}

function dumpFullPage() {
  var el = document.getElementById('dom-dump');
  var html = document.documentElement.outerHTML;
  el.textContent = 'Full page length: ' + html.length + '\n';
  // Send chunks to native
  if (B) B.onLog('Full DOM length: ' + html.length, 'INFO');
}

// Intercept all console messages
var origLog = console.log, origErr = console.error, origWarn = console.warn;
console.log = function() { origLog.apply(console, arguments); log('console.log: ' + Array.from(arguments).join(' ')); };
console.error = function() { origErr.apply(console, arguments); log('console.error: ' + Array.from(arguments).join(' '), 'ERR'); };
console.warn = function() { origWarn.apply(console, arguments); log('console.warn: ' + Array.from(arguments).join(' '), 'WARN'); };

log('Diagnostic page loaded');
</script>
</body></html>
""".trimIndent()

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmbedTestScreen(onBack: () -> Unit) {
    val logs = remember { mutableStateListOf<LogEntry>() }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var showLogs by remember { mutableStateOf(true) }

    fun log(message: String, level: LogLevel = LogLevel.INFO) {
        logs.add(LogEntry(message, level))
    }

    val jsBridge = remember {
        object {
            @JavascriptInterface
            fun onLog(message: String, level: String) {
                val logLevel = when (level) {
                    "OK" -> LogLevel.SUCCESS
                    "ERR" -> LogLevel.ERROR
                    "WARN" -> LogLevel.WARNING
                    else -> LogLevel.INFO
                }
                logs.add(LogEntry(message, logLevel))
            }

            @JavascriptInterface
            fun onPlayerState(state: String) {
                logs.add(LogEntry("Player state: $state", LogLevel.SUCCESS))
            }

            @JavascriptInterface
            fun onPlayerError(code: String) {
                logs.add(LogEntry("Player error: $code", LogLevel.ERROR))
            }
        }
    }

    fun loadDiagnostics() {
        val wv = webViewRef ?: run { log("WebView not ready", LogLevel.ERROR); return }
        log("--- Running all diagnostics ---")
        wv.loadDataWithBaseURL(
            "https://www.youtube.com",
            diagnosticHtml(),
            "text/html",
            "UTF-8",
            null,
        )
    }

    fun loadDirectEmbed(variant: String) {
        val wv = webViewRef ?: run { log("WebView not ready", LogLevel.ERROR); return }
        val url = when (variant) {
            "std" -> "https://www.youtube.com/embed/$TEST_VIDEO_ID"
            "nocookie" -> "https://www.youtube-nocookie.com/embed/$TEST_VIDEO_ID"
            "html5" -> "https://www.youtube.com/embed/$TEST_VIDEO_ID?html5=1"
            "origin" -> "https://www.youtube.com/embed/$TEST_VIDEO_ID?origin=https://www.youtube.com"
            "noua" -> {
                // Reset to default WebView UA before loading
                wv.settings.userAgentString = null
                "https://www.youtube.com/embed/$TEST_VIDEO_ID"
            }
            "watch" -> "https://m.youtube.com/watch?v=$TEST_VIDEO_ID"
            else -> return
        }
        log("Direct load [$variant]: $url")
        wv.loadUrl(url)
    }

    fun dumpDom() {
        val wv = webViewRef ?: return
        wv.evaluateJavascript(
            "(function(){ return JSON.stringify({title:document.title, url:location.href, bodyLen:document.body.innerHTML.length, text:document.body.innerText.substring(0,500)}); })()"
        ) { result ->
            log("DOM dump: $result")
        }
    }

    DisposableEffect(Unit) { onDispose { webViewRef?.destroy() } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TvBackground)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        // Header
        Text(
            text = "Test 3: Embed Diagnostics",
            style = MaterialTheme.typography.bodyLarge,
            color = TvText,
        )

        // Button bar
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            EmbedBtn("ALL TESTS", TvSuccess) { loadDiagnostics() }
            EmbedBtn("embed/std", TvPrimary) { loadDirectEmbed("std") }
            EmbedBtn("embed/nc", TvPrimary) { loadDirectEmbed("nocookie") }
            EmbedBtn("embed/html5", TvPrimary) { loadDirectEmbed("html5") }
            EmbedBtn("embed/origin", TvPrimary) { loadDirectEmbed("origin") }
            EmbedBtn("embed/noUA", TvWarning) { loadDirectEmbed("noua") }
            EmbedBtn("m.yt/watch", TvAccent) { loadDirectEmbed("watch") }
            EmbedBtn("DOM", TvPrimary) { dumpDom() }
            EmbedBtn(if (showLogs) "HideLog" else "Log", TvPrimary.copy(alpha = 0.6f)) { showLogs = !showLogs }
            EmbedBtn("Back", TvPrimary.copy(alpha = 0.4f)) { onBack() }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // WebView
        @SuppressLint("SetJavaScriptEnabled")
        AndroidView(
            factory = { ctx ->
                WebView.setWebContentsDebuggingEnabled(true)
                WebView(ctx).apply {
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        allowFileAccess = true
                        javaScriptCanOpenWindowsAutomatically = true
                        setSupportMultipleWindows(false)
                    }

                    addJavascriptInterface(jsBridge, "KidsWatchEmbed")

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            logs.add(LogEntry(">> ${url?.take(80)}", LogLevel.INFO))
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            logs.add(LogEntry("<< ${url?.take(80)}", LogLevel.SUCCESS))
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?,
                        ): Boolean {
                            val url = request?.url?.toString() ?: return true
                            // Allow YouTube embed/watch navigations within the diagnostic page
                            val host = Uri.parse(url).host ?: ""
                            if (host.contains("youtube.com") || host.contains("youtube-nocookie.com") ||
                                host.contains("google.com") || host.contains("googleapis.com") ||
                                host.contains("googlevideo.com") || host.contains("ytimg.com")
                            ) {
                                logs.add(LogEntry("ALLOW nav: ${url.take(80)}", LogLevel.INFO))
                                return false
                            }
                            logs.add(LogEntry("BLOCK nav: ${url.take(80)}", LogLevel.WARNING))
                            return true
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(msg: ConsoleMessage?): Boolean {
                            msg?.let {
                                val level = when (it.messageLevel()) {
                                    ConsoleMessage.MessageLevel.ERROR -> LogLevel.ERROR
                                    ConsoleMessage.MessageLevel.WARNING -> LogLevel.WARNING
                                    else -> LogLevel.INFO
                                }
                                logs.add(LogEntry("JS: ${it.message()?.take(120)}", level))
                            }
                            return true
                        }
                    }

                    webViewRef = this

                    loadData(
                        "<html><body style='background:#111;color:#666;display:flex;align-items:center;" +
                            "justify-content:center;height:100vh;font-family:monospace'>" +
                            "<p>Press ALL TESTS to begin</p></body></html>",
                        "text/html",
                        "UTF-8",
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )

        // Log panel
        if (showLogs) {
            ResultLogPanel(
                logs = logs,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
            )
        }
    }
}

@Composable
private fun EmbedBtn(
    text: String,
    color: Color,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = color),
        shape = RoundedCornerShape(6.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
        modifier = Modifier.height(28.dp),
    ) {
        Text(text, color = TvText, fontSize = 10.sp)
    }
}
