(function() {
    'use strict';

    let sessionToken = localStorage.getItem('kw_token');
    const API_BASE = '';
    let statusInterval = null;
    let isCurrentlyPlaying = false;

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
    pinForm.addEventListener('submit', async function(e) {
        e.preventDefault();
        authError.classList.add('hidden');
        var pin = pinInput.value.trim();
        if (!pin) return;

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
                    li.innerHTML = '<span>' + escapeHtml(pl.displayName) + '</span>';
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
                    li.innerHTML = '<span>' + escapeHtml(evt.videoId) + '</span><span>' + mins + 'm</span>';
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
                    setPollingRate(5000);
                }
            } else {
                nowPlaying.classList.add('hidden');
                if (isCurrentlyPlaying) {
                    isCurrentlyPlaying = false;
                    setPollingRate(30000);
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
        setPollingRate(30000);
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

    // Auto-login if token exists from previous session
    if (sessionToken) {
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
