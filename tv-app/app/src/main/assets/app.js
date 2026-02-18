(function() {
    'use strict';

    let sessionToken = localStorage.getItem('kw_token');
    const API_BASE = '';
    let statusInterval = null;
    let isCurrentlyPlaying = false;

    // Extract PIN from query param: ?pin=123456
    function extractPin() {
        var params = new URLSearchParams(window.location.search);
        return params.get('pin');
    }

    // DOM refs
    const authScreen = document.getElementById('auth-screen');
    const dashboard = document.getElementById('dashboard');
    const pinForm = document.getElementById('pin-form');
    const pinInput = document.getElementById('pin-input');
    const authError = document.getElementById('auth-error');
    const playlistForm = document.getElementById('playlist-form');
    const playlistUrl = document.getElementById('playlist-url');
    const playlistError = document.getElementById('playlist-error');
    const playlistList = document.getElementById('playlist-list');
    const recentList = document.getElementById('recent-list');
    const nowPlaying = document.getElementById('now-playing');
    const npTitle = document.getElementById('np-title');
    const npPlaylistTitle = document.getElementById('np-playlist-title');
    const npElapsed = document.getElementById('np-elapsed');
    const npDuration = document.getElementById('np-duration');
    const npProgressFill = document.getElementById('np-progress-fill');
    const npStopBtn = document.getElementById('np-stop-btn');
    const npPauseBtn = document.getElementById('np-pause-btn');
    const npNextBtn = document.getElementById('np-next-btn');
    const statVideos = document.getElementById('stat-videos');
    const statTime = document.getElementById('stat-time');

    function authHeaders() {
        return { 'Authorization': 'Bearer ' + sessionToken, 'Content-Type': 'application/json' };
    }

    async function apiCall(method, path, body) {
        var opts = { method: method, headers: authHeaders() };
        if (body) opts.body = JSON.stringify(body);
        var resp = await fetch(API_BASE + path, opts);
        if (resp.status === 401) {
            logout();
            return { status: 401, data: { error: 'Session expired' } };
        }
        var data = await resp.json();
        return { status: resp.status, data: data };
    }

    function formatTime(totalSec) {
        var mins = Math.floor(totalSec / 60);
        var secs = totalSec % 60;
        return mins + ':' + (secs < 10 ? '0' : '') + secs;
    }

    // Auth
    async function submitPin(pin) {
        authError.classList.add('hidden');
        try {
            var resp = await fetch(API_BASE + '/auth', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ pin: pin })
            });
            var data = await resp.json();

            if (resp.ok && data.token) {
                sessionToken = data.token;
                localStorage.setItem('kw_token', sessionToken);
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

    pinForm.addEventListener('submit', async function(e) {
        e.preventDefault();
        var pin = pinInput.value.trim();
        if (!pin) return;
        submitPin(pin);
    });

    // Add playlist
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

                // Switch to fast polling when playing
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
        }, ms);
    }

    function loadDashboard() {
        loadPlaylists();
        loadStats();
        loadRecent();
        loadStatus();
        setPollingRate(120000);
    }

    function escapeHtml(str) {
        var div = document.createElement('div');
        div.textContent = str;
        return div.innerHTML;
    }

    function logout() {
        sessionToken = null;
        localStorage.removeItem('kw_token');
        dashboard.classList.add('hidden');
        authScreen.classList.remove('hidden');
        if (statusInterval) clearInterval(statusInterval);
    }

    // Add to Home Screen banner
    (function() {
        var DISMISS_KEY = 'kw_homescreen_dismissed';
        var banner = document.getElementById('homescreen-banner');
        var addBtn = document.getElementById('homescreen-add-btn');
        var dismissBtn = document.getElementById('homescreen-dismiss-btn');
        var bannerText = document.getElementById('homescreen-text');
        var deferredPrompt = null;

        // Don't show on desktop or if already standalone
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

    // Auto-PIN from URL query param (QR code scan)
    var autoPin = extractPin();
    if (autoPin && !sessionToken) {
        pinInput.value = autoPin;
        submitPin(autoPin);
    } else if (sessionToken) {
        // Auto-login if token exists from previous session
        fetch(API_BASE + '/status', { headers: authHeaders() })
            .then(function(resp) {
                if (resp.ok) {
                    authScreen.classList.add('hidden');
                    dashboard.classList.remove('hidden');
                    loadDashboard();
                } else {
                    logout();
                }
            })
            .catch(function() { logout(); });
    }
})();
