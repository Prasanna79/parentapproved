(function() {
    'use strict';

    // --- Environment detection ---
    // On relay: URL is /tv/{tvId}/... → tvId is set, API_BASE is /tv/{tvId}/api
    // On local: URL is / → tvId is null, API_BASE is ''
    function extractTvId() {
        var parts = window.location.pathname.split('/');
        if (parts.length >= 3 && parts[1] === 'tv') {
            return parts[2];
        }
        return null;
    }

    function extractApiBase() {
        var tvId = extractTvId();
        if (!tvId) return '';
        return '/tv/' + tvId + '/api';
    }

    function extractPin() {
        var params = new URLSearchParams(window.location.search);
        return params.get('pin');
    }

    var tvId = extractTvId();
    var isRelay = !!tvId;
    var API_BASE = extractApiBase();
    var STORAGE_KEY = tvId ? 'kw_token_' + tvId : 'kw_token';
    var sessionToken = localStorage.getItem(STORAGE_KEY);
    var statusInterval = null;
    var isCurrentlyPlaying = false;
    var offlineRetryInterval = null;
    var tvIsOffline = false;

    // Export functions for testing
    if (typeof window !== 'undefined') {
        window._kw = {
            extractTvId: extractTvId,
            extractApiBase: extractApiBase,
            extractPin: extractPin
        };
    }

    // DOM refs
    var authScreen = document.getElementById('auth-screen');
    var dashboard = document.getElementById('dashboard');
    var pinForm = document.getElementById('pin-form');
    var pinInput = document.getElementById('pin-input');
    var authError = document.getElementById('auth-error');
    var playlistForm = document.getElementById('playlist-form');
    var playlistUrl = document.getElementById('playlist-url');
    var playlistError = document.getElementById('playlist-error');
    var playlistList = document.getElementById('playlist-list');
    var recentList = document.getElementById('recent-list');
    var nowPlaying = document.getElementById('now-playing');
    var npTitle = document.getElementById('np-title');
    var npPlaylistTitle = document.getElementById('np-playlist-title');
    var npElapsed = document.getElementById('np-elapsed');
    var npDuration = document.getElementById('np-duration');
    var npProgressFill = document.getElementById('np-progress-fill');
    var npStopBtn = document.getElementById('np-stop-btn');
    var npPauseBtn = document.getElementById('np-pause-btn');
    var npNextBtn = document.getElementById('np-next-btn');
    var statVideos = document.getElementById('stat-videos');
    var statTime = document.getElementById('stat-time');
    var offlineBanner = document.getElementById('offline-banner');
    var versionBanner = document.getElementById('version-banner');
    var localNotice = document.getElementById('local-notice');

    // Hide local-notice on relay
    if (isRelay && localNotice) {
        localNotice.classList.add('hidden');
    }

    function authHeaders() {
        return { 'Authorization': 'Bearer ' + sessionToken, 'Content-Type': 'application/json' };
    }

    // --- Offline handling (relay only, but harmless on local) ---
    function showOffline() {
        if (offlineBanner) offlineBanner.classList.remove('hidden');
        tvIsOffline = true;
        if (!offlineRetryInterval) {
            offlineRetryInterval = setInterval(function() {
                checkOnline();
            }, 30000);
        }
    }

    function hideOffline() {
        if (offlineBanner) offlineBanner.classList.add('hidden');
        tvIsOffline = false;
        if (offlineRetryInterval) {
            clearInterval(offlineRetryInterval);
            offlineRetryInterval = null;
        }
    }

    function showVersionMismatch() {
        if (versionBanner) versionBanner.classList.remove('hidden');
    }

    async function checkOnline() {
        try {
            var resp = await fetch(API_BASE + '/status', { headers: authHeaders() });
            if (resp.status !== 503) {
                hideOffline();
                loadDashboard();
            }
        } catch (err) {
            // Still offline
        }
    }

    // --- API call with offline + auth handling ---
    async function apiCall(method, path, body) {
        var opts = { method: method, headers: authHeaders() };
        if (body) opts.body = JSON.stringify(body);
        try {
            var resp = await fetch(API_BASE + path, opts);
            if (resp.status === 503) {
                showOffline();
                return { status: 503, data: { error: 'TV is offline' } };
            }
            if (tvIsOffline) hideOffline();
            if (resp.status === 401) {
                logout();
                return { status: 401, data: { error: 'Session expired' } };
            }
            var data = await resp.json();
            return { status: resp.status, data: data };
        } catch (err) {
            if (isRelay) {
                showOffline();
                return { status: 503, data: { error: 'Connection failed' } };
            }
            return { status: 0, data: { error: 'Connection failed' } };
        }
    }

    function formatTime(totalSec) {
        var mins = Math.floor(totalSec / 60);
        var secs = totalSec % 60;
        return mins + ':' + (secs < 10 ? '0' : '') + secs;
    }

    // --- Token refresh ---
    async function refreshToken() {
        if (!sessionToken) return false;
        try {
            var resp = await fetch(API_BASE + '/auth/refresh', {
                method: 'POST',
                headers: authHeaders()
            });
            if (resp.status === 503) {
                showOffline();
                return true; // Token might still be valid, TV just offline
            }
            if (resp.ok) {
                var data = await resp.json();
                if (data.token) {
                    sessionToken = data.token;
                    localStorage.setItem(STORAGE_KEY, sessionToken);
                    return true;
                }
            }
            return false;
        } catch (err) {
            if (isRelay) {
                showOffline();
                return true; // Network error, token might still be valid
            }
            return false;
        }
    }

    // --- Version check ---
    async function checkVersion() {
        try {
            var result = await apiCall('GET', '/status');
            if (result.status === 200 && result.data.protocolVersion) {
                if (result.data.protocolVersion !== 1) {
                    showVersionMismatch();
                }
            }
        } catch (err) {
            // Version check is best-effort
        }
    }

    // --- Auth ---
    async function submitPin(pin) {
        if (!pin) return;
        authError.classList.add('hidden');

        try {
            var resp = await fetch(API_BASE + '/auth', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ pin: pin })
            });

            if (resp.status === 503) {
                showOffline();
                authError.textContent = 'TV is offline';
                authError.classList.remove('hidden');
                return;
            }

            var data = await resp.json();

            if (resp.ok && data.token) {
                sessionToken = data.token;
                localStorage.setItem(STORAGE_KEY, sessionToken);
                // Strip secret and pin from URL for security
                if (window.history && window.history.replaceState) {
                    var cleanUrl = window.location.pathname;
                    window.history.replaceState({}, document.title, cleanUrl);
                }
                authScreen.classList.add('hidden');
                dashboard.classList.remove('hidden');
                loadDashboard();
            } else {
                authError.textContent = data.error || 'Invalid PIN';
                authError.classList.remove('hidden');
            }
        } catch (err) {
            authError.textContent = 'Connection failed';
            authError.classList.remove('hidden');
        }
    }

    pinForm.addEventListener('submit', function(e) {
        e.preventDefault();
        submitPin(pinInput.value.trim());
    });

    // --- Playlists ---
    playlistForm.addEventListener('submit', async function(e) {
        e.preventDefault();
        playlistError.classList.add('hidden');
        var url = playlistUrl.value.trim();
        if (!url) return;

        try {
            var result = await apiCall('POST', '/playlists', { url: url });
            if (result.status === 201) {
                playlistUrl.value = '';
                loadPlaylists();
            } else {
                playlistError.textContent = result.data.error || 'Failed to add playlist';
                playlistError.classList.remove('hidden');
            }
        } catch (err) {
            playlistError.textContent = 'Connection failed';
            playlistError.classList.remove('hidden');
        }
    });

    async function deletePlaylist(id) {
        if (!confirm('Remove this playlist?')) return;
        await apiCall('DELETE', '/playlists/' + id);
        loadPlaylists();
    }

    async function loadPlaylists() {
        try {
            var result = await apiCall('GET', '/playlists');
            if (result.status === 200) {
                playlistList.innerHTML = '';
                result.data.forEach(function(pl) {
                    var li = document.createElement('li');
                    var label = escapeHtml(pl.displayName);
                    if (pl.videoCount > 0) label += ' \u2014 ' + pl.videoCount + ' videos';
                    li.innerHTML = '<span>' + label + '</span>';
                    var btn = document.createElement('button');
                    btn.className = 'delete-btn';
                    btn.textContent = 'Remove';
                    btn.onclick = function() { deletePlaylist(pl.id); };
                    li.appendChild(btn);
                    playlistList.appendChild(li);
                });
            }
        } catch (err) {
            console.error('Load playlists failed:', err);
        }
    }

    // --- Stats ---
    async function loadStats() {
        try {
            var result = await apiCall('GET', '/stats');
            if (result.status === 200) {
                statVideos.textContent = result.data.totalEventsToday;
                var mins = Math.round(result.data.totalWatchTimeToday / 60);
                statTime.textContent = mins + 'm';
            }
        } catch (err) {
            console.error('Load stats failed:', err);
        }
    }

    async function loadRecent() {
        try {
            var result = await apiCall('GET', '/stats/recent');
            if (result.status === 200) {
                recentList.innerHTML = '';
                result.data.slice(0, 10).forEach(function(evt) {
                    var li = document.createElement('li');
                    var mins = Math.round(evt.durationSec / 60);
                    var label = evt.title ? escapeHtml(evt.title) : escapeHtml(evt.videoId);
                    li.innerHTML = '<span>' + label + '</span><span>' + mins + 'm</span>';
                    recentList.appendChild(li);
                });
            }
        } catch (err) {
            console.error('Load recent failed:', err);
        }
    }

    // --- Now Playing ---
    async function loadStatus() {
        try {
            var result = await apiCall('GET', '/status');
            if (result.status === 200 && result.data.currentlyPlaying) {
                var np = result.data.currentlyPlaying;
                nowPlaying.classList.remove('hidden');
                npTitle.textContent = np.title || np.videoId;
                npPlaylistTitle.textContent = np.playlistTitle || '';
                var npThumbnail = document.getElementById('np-thumbnail');
                npThumbnail.src = 'https://img.youtube.com/vi/' + np.videoId + '/mqdefault.jpg';
                npThumbnail.alt = np.title || np.videoId;
                npElapsed.textContent = formatTime(np.elapsedSec || 0);
                npDuration.textContent = formatTime(np.durationSec || 0);
                npPauseBtn.textContent = np.playing ? 'Pause' : 'Play';

                var pct = 0;
                if (np.durationSec > 0) {
                    pct = Math.min(100, Math.round((np.elapsedSec / np.durationSec) * 100));
                }
                npProgressFill.style.width = pct + '%';

                if (!isCurrentlyPlaying) {
                    isCurrentlyPlaying = true;
                    setPollingRate(30000);
                }
            } else {
                nowPlaying.classList.add('hidden');
                var npThumbnailHide = document.getElementById('np-thumbnail');
                npThumbnailHide.src = '';
                npThumbnailHide.alt = '';
                if (isCurrentlyPlaying) {
                    isCurrentlyPlaying = false;
                    setPollingRate(120000);
                    loadStats();
                    loadRecent();
                }
            }
        } catch (err) {
            console.error('Load status failed:', err);
        }
    }

    // Playback controls
    npStopBtn.addEventListener('click', function() {
        apiCall('POST', '/playback/stop');
    });

    npPauseBtn.addEventListener('click', function() {
        apiCall('POST', '/playback/pause');
    });

    npNextBtn.addEventListener('click', function() {
        apiCall('POST', '/playback/skip');
    });

    function setPollingRate(ms) {
        if (statusInterval) clearInterval(statusInterval);
        statusInterval = setInterval(function() {
            loadStatus();
            loadTimeLimits();
        }, ms);
    }

    // --- Screen Time ---

    var currentlyLocked = false;

    async function loadTimeLimits() {
        try {
            var result = await apiCall('GET', '/time-limits');
            if (result.status !== 200) return;
            var d = result.data;

            // Status badge
            var badge = document.getElementById('st-status-badge');
            badge.className = 'badge st-badge-' + d.currentStatus;
            badge.textContent = d.currentStatus === 'allowed' ? 'Allowed'
                : d.currentStatus === 'warning' ? 'Warning'
                : 'Blocked';

            // Lock reason
            var reasonEl = document.getElementById('st-lock-reason');
            if (d.lockReason) {
                var reasonText = d.lockReason === 'daily_limit' ? 'Daily limit reached'
                    : d.lockReason === 'bedtime' ? 'Bedtime'
                    : 'Manually locked';
                reasonEl.textContent = reasonText;
                reasonEl.classList.remove('hidden');
            } else {
                reasonEl.classList.add('hidden');
            }

            // Usage bar
            var bar = document.getElementById('st-usage-bar');
            var label = document.getElementById('st-usage-label');
            var usedMin = d.todayUsedMin || 0;

            if (d.todayLimitMin != null) {
                var effectiveLimit = d.todayLimitMin + (d.todayBonusMin || 0);
                var pct = Math.min(100, Math.round((usedMin / effectiveLimit) * 100));
                bar.style.width = pct + '%';
                bar.className = 'st-usage-bar' + (d.currentStatus === 'blocked' ? ' st-bar-blocked' : d.currentStatus === 'warning' ? ' st-bar-warning' : '');
                label.textContent = usedMin + 'm / ' + effectiveLimit + 'm' + (d.todayBonusMin > 0 ? ' (+' + d.todayBonusMin + 'm bonus)' : '');
            } else {
                bar.style.width = '0%';
                label.textContent = usedMin + 'm / No limit';
            }

            // Lock button
            var lockBtn = document.getElementById('st-lock-btn');
            currentlyLocked = d.manuallyLocked;
            if (d.manuallyLocked) {
                lockBtn.textContent = 'Unlock TV';
                lockBtn.className = 'st-unlock';
                lockBtn.id = 'st-lock-btn';
            } else {
                lockBtn.textContent = 'Lock TV';
                lockBtn.className = '';
                lockBtn.id = 'st-lock-btn';
            }

            // Time request banner
            var requestEl = document.getElementById('st-time-request');
            if (d.hasTimeRequest) {
                requestEl.classList.remove('hidden');
            } else {
                requestEl.classList.add('hidden');
            }
        } catch (err) {
            console.error('Load time limits failed:', err);
        }
    }

    window.toggleLock = async function() {
        var locked = !currentlyLocked;
        await apiCall('POST', '/time-limits/lock', { locked: locked });
        loadTimeLimits();
    };

    window.grantBonusTime = async function(minutes) {
        await apiCall('POST', '/time-limits/bonus', { minutes: minutes });
        loadTimeLimits();
    };

    // Edit limits modal
    window.openEditLimits = async function() {
        var result = await apiCall('GET', '/time-limits');
        if (result.status !== 200) return;
        var d = result.data;

        var limitEnabled = document.getElementById('edit-limit-enabled');
        var limitMinutes = document.getElementById('edit-limit-minutes');
        var limitRow = document.getElementById('edit-limit-input-row');
        var bedtimeEnabled = document.getElementById('edit-bedtime-enabled');
        var bedtimeStart = document.getElementById('edit-bedtime-start');
        var bedtimeEnd = document.getElementById('edit-bedtime-end');
        var bedtimeRow = document.getElementById('edit-bedtime-input-row');

        var hasLimit = d.todayLimitMin != null;
        limitEnabled.checked = hasLimit;
        limitRow.classList.toggle('hidden', !hasLimit);
        if (hasLimit) limitMinutes.value = d.todayLimitMin;

        var hasBedtime = d.bedtime != null;
        bedtimeEnabled.checked = hasBedtime;
        bedtimeRow.classList.toggle('hidden', !hasBedtime);
        if (hasBedtime) {
            bedtimeStart.value = d.bedtime.start;
            bedtimeEnd.value = d.bedtime.end;
        }

        limitEnabled.onchange = function() { limitRow.classList.toggle('hidden', !limitEnabled.checked); };
        bedtimeEnabled.onchange = function() { bedtimeRow.classList.toggle('hidden', !bedtimeEnabled.checked); };

        document.getElementById('edit-limits-modal').classList.remove('hidden');
    };

    window.saveLimits = async function() {
        var limitEnabled = document.getElementById('edit-limit-enabled').checked;
        var bedtimeEnabled = document.getElementById('edit-bedtime-enabled').checked;
        var body = {};

        if (limitEnabled) {
            var mins = parseInt(document.getElementById('edit-limit-minutes').value) || 120;
            body.dailyLimits = {
                monday: mins, tuesday: mins, wednesday: mins,
                thursday: mins, friday: mins, saturday: mins, sunday: mins
            };
        } else {
            body.dailyLimits = {};
        }

        if (bedtimeEnabled) {
            var start = document.getElementById('edit-bedtime-start').value;
            var end = document.getElementById('edit-bedtime-end').value;
            var startParts = start.split(':');
            var endParts = end.split(':');
            body.bedtimeStartMin = parseInt(startParts[0]) * 60 + parseInt(startParts[1]);
            body.bedtimeEndMin = parseInt(endParts[0]) * 60 + parseInt(endParts[1]);
        } else {
            body.bedtimeStartMin = -1;
            body.bedtimeEndMin = -1;
        }

        await apiCall('PUT', '/time-limits', body);
        closeEditLimits();
        loadTimeLimits();
    };

    window.closeEditLimits = function() {
        document.getElementById('edit-limits-modal').classList.add('hidden');
    };

    // --- Version footer + feedback ---
    var footerVersion = document.getElementById('footer-version');
    var updateBadge = document.getElementById('update-badge');
    var feedbackLink = document.getElementById('feedback-link');
    var crashSection = document.getElementById('crash-section');
    var crashLog = document.getElementById('crash-log');
    var copyCrashBtn = document.getElementById('copy-crash');

    function updateVersionFooter(version) {
        if (footerVersion) footerVersion.textContent = 'v' + version;
        if (feedbackLink) {
            feedbackLink.href = 'mailto:hello@parentapproved.tv?subject='
                + encodeURIComponent('[ParentApproved v' + version + '] Feedback')
                + '&body=' + encodeURIComponent('Device: \nIssue: \n\n');
        }
    }

    async function checkUpdateAvailable(version) {
        try {
            var resp = await fetch('https://parentapproved.tv/version.json');
            if (resp.ok) {
                var data = await resp.json();
                if (data.latestCode && data.latest) {
                    var current = parseInt(version.replace(/[^0-9]/g, ''));
                    if (data.latestCode > current && updateBadge) {
                        updateBadge.textContent = 'Update: v' + data.latest;
                        updateBadge.classList.remove('hidden');
                    }
                }
            }
        } catch (err) {
            // Best-effort
        }
    }

    async function loadCrashLog() {
        try {
            var result = await apiCall('GET', '/crash-log');
            if (result.status === 200 && result.data.hasCrash) {
                if (crashSection) crashSection.classList.remove('hidden');
                if (crashLog) crashLog.textContent = result.data.log;
            }
        } catch (err) {
            // Best-effort
        }
    }

    if (copyCrashBtn) {
        copyCrashBtn.addEventListener('click', function() {
            var text = crashLog ? crashLog.textContent : '';
            navigator.clipboard.writeText(text).then(function() {
                copyCrashBtn.textContent = 'Copied!';
                setTimeout(function() { copyCrashBtn.textContent = 'Copy to clipboard'; }, 2000);
            });
        });
    }

    // --- Dashboard lifecycle ---
    async function loadDashboard() {
        await refreshToken();
        loadPlaylists();
        loadStats();
        loadRecent();
        loadStatus();
        loadTimeLimits();
        checkVersion();
        loadCrashLog();

        // Set version footer from status
        try {
            var statusResult = await apiCall('GET', '/status');
            if (statusResult.status === 200 && statusResult.data.version) {
                updateVersionFooter(statusResult.data.version);
                checkUpdateAvailable(statusResult.data.version);
            }
        } catch (err) {}

        setPollingRate(120000);
    }

    function escapeHtml(str) {
        var div = document.createElement('div');
        div.textContent = str;
        return div.innerHTML;
    }

    function logout() {
        sessionToken = null;
        localStorage.removeItem(STORAGE_KEY);
        dashboard.classList.add('hidden');
        authScreen.classList.remove('hidden');
        if (statusInterval) clearInterval(statusInterval);
    }

    // --- Add to Home Screen banner ---
    (function() {
        var DISMISS_KEY = tvId ? 'kw_homescreen_dismissed_' + tvId : 'kw_homescreen_dismissed';
        var banner = document.getElementById('homescreen-banner');
        var addBtn = document.getElementById('homescreen-add-btn');
        var dismissBtn = document.getElementById('homescreen-dismiss-btn');
        var bannerText = document.getElementById('homescreen-text');
        var deferredPrompt = null;

        var isStandalone = window.matchMedia('(display-mode: standalone)').matches || window.navigator.standalone;
        var isMobile = 'ontouchstart' in window || window.innerWidth <= 768;
        if (isStandalone || !isMobile || localStorage.getItem(DISMISS_KEY)) return;

        var isIOS = /iPhone|iPad/.test(navigator.userAgent);

        if (isIOS) {
            bannerText.textContent = "Tap Share then 'Add to Home Screen' for quick access.";
            addBtn.style.display = 'none';
            banner.classList.remove('hidden');
        }

        window.addEventListener('beforeinstallprompt', function(e) {
            e.preventDefault();
            deferredPrompt = e;
            banner.classList.remove('hidden');
        });

        addBtn.addEventListener('click', function() {
            if (deferredPrompt) {
                deferredPrompt.prompt();
                deferredPrompt.userChoice.then(function() {
                    deferredPrompt = null;
                    banner.classList.add('hidden');
                });
            }
        });

        dismissBtn.addEventListener('click', function() {
            banner.classList.add('hidden');
            localStorage.setItem(DISMISS_KEY, '1');
        });
    })();

    // --- Startup ---
    var autoPin = extractPin();
    if (autoPin && !sessionToken) {
        pinInput.value = autoPin;
        submitPin(autoPin);
    } else if (sessionToken) {
        refreshToken().then(function(valid) {
            if (valid) {
                authScreen.classList.add('hidden');
                dashboard.classList.remove('hidden');
                loadDashboard();
            } else {
                logout();
            }
        });
    }
})();
