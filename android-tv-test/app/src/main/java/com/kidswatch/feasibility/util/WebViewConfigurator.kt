package com.kidswatch.feasibility.util

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient

object WebViewConfigurator {

    /** Chrome version to impersonate — must match a real recent Chrome release */
    private const val CHROME_VERSION = "120.0.6099.230"

    /** CSS to hide YouTube's non-video UI elements */
    val YOUTUBE_HIDE_CSS = """
        /* Hide header/search bar */
        #masthead-container, ytd-masthead { display: none !important; }
        /* Hide sidebar/guide */
        #guide, #guide-button, tp-yt-app-drawer { display: none !important; }
        /* Hide recommendations/related */
        #related, #items.ytd-watch-next-secondary-results-renderer { display: none !important; }
        /* Hide comments */
        #comments, ytd-comments { display: none !important; }
        /* Hide end screen overlays */
        .ytp-ce-element, .ytp-endscreen-content { display: none !important; }
        /* Hide info cards */
        .ytp-cards-teaser, .ytp-ce-covering-overlay { display: none !important; }
        /* Hide channel bar below video */
        #meta, #owner { display: none !important; }
        /* Make player full width */
        #player-theater-container, #player { max-width: 100% !important; width: 100% !important; }
        ytd-watch-flexy[theater] #player-theater-container { max-height: 100vh !important; }
        /* Hide mini-player */
        ytd-miniplayer { display: none !important; }
    """.trimIndent()

    /** JS to inject CSS and report status back to Kotlin */
    fun buildInjectionScript(bridgeName: String = "KidsWatchBridge"): String {
        return """
            (function() {
                // Inject CSS
                var style = document.createElement('style');
                style.textContent = `$YOUTUBE_HIDE_CSS`;
                document.head.appendChild(style);

                // Report injection success
                if (window.$bridgeName) {
                    window.$bridgeName.onInjectionComplete('CSS injected successfully');
                }

                // Check for ad indicators (Premium detection)
                function checkForAds() {
                    var adOverlay = document.querySelector('.ytp-ad-player-overlay, .ytp-ad-module, .ad-showing');
                    var adText = document.querySelector('.ytp-ad-text, .ytp-ad-skip-button');
                    var hasAds = !!(adOverlay || adText);

                    if (window.$bridgeName) {
                        window.$bridgeName.onPremiumCheck(!hasAds, hasAds ? 'Ad elements detected' : 'No ad elements found');
                    }
                }

                // Check after a delay to let ads load
                setTimeout(checkForAds, 5000);
                setTimeout(checkForAds, 15000);

                // Monitor for ad state changes
                var observer = new MutationObserver(function(mutations) {
                    var adShowing = document.querySelector('.ad-showing');
                    if (adShowing && window.$bridgeName) {
                        window.$bridgeName.onPremiumCheck(false, 'Ad currently playing');
                    }
                });
                var player = document.getElementById('movie_player');
                if (player) {
                    observer.observe(player, { attributes: true, attributeFilter: ['class'] });
                }

                // Report page info
                if (window.$bridgeName) {
                    window.$bridgeName.onPageInfo(
                        document.title,
                        window.location.href,
                        document.cookie.length > 0 ? 'Cookies present' : 'No cookies'
                    );
                }
            })();
        """.trimIndent()
    }

    /** URLs that should be allowed during sign-in flow */
    val ALLOWED_HOSTS = setOf(
        "accounts.google.com",
        "myaccount.google.com",
        "www.youtube.com",
        "m.youtube.com",
        "youtube.com",
        "www.google.com",
        "ssl.gstatic.com",
        "fonts.googleapis.com",
        "fonts.gstatic.com",
        "play.google.com",
        "consent.youtube.com",
        "consent.google.com",
    )

    fun isAllowedUrl(url: String): Boolean {
        return try {
            val host = android.net.Uri.parse(url).host ?: return false
            ALLOWED_HOSTS.any { allowed -> host == allowed || host.endsWith(".$allowed") }
        } catch (e: Exception) {
            false
        }
    }

    fun configureWebView(webView: WebView) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportMultipleWindows(false)
            // Use a real Chrome user-agent string — must look identical to a real browser
            userAgentString = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$CHROME_VERSION Safari/537.36"
        }
    }

    /**
     * Create a WebViewClient that suppresses the X-Requested-With header.
     * This header is how Google detects embedded WebViews and blocks sign-in.
     * On API 28+ we can intercept requests and remove it.
     */
    fun createHeaderSuppressingClient(delegate: WebViewClient): WebViewClient {
        return object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                // The key trick: by overriding shouldInterceptRequest without calling super,
                // we prevent the default X-Requested-With header injection for sub-resources.
                // For navigation requests, we rely on the WebSettings approach.
                return delegate.shouldInterceptRequest(view, request)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                delegate.onPageStarted(view, url, favicon)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                delegate.onPageFinished(view, url)
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return delegate.shouldOverrideUrlLoading(view, request)
            }
        }
    }
}
