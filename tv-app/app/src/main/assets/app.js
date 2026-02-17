(function() {
    'use strict';

    let sessionToken = null;
    const API_BASE = '';

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
    const nowPlayingId = document.getElementById('now-playing-id');
    const statVideos = document.getElementById('stat-videos');
    const statTime = document.getElementById('stat-time');

    function authHeaders() {
        return { 'Authorization': 'Bearer ' + sessionToken, 'Content-Type': 'application/json' };
    }

    async function apiCall(method, path, body) {
        const opts = { method: method, headers: authHeaders() };
        if (body) opts.body = JSON.stringify(body);
        const resp = await fetch(API_BASE + path, opts);
        const data = await resp.json();
        return { status: resp.status, data: data };
    }

    // Auth
    pinForm.addEventListener('submit', async function(e) {
        e.preventDefault();
        authError.classList.add('hidden');
        const pin = pinInput.value.trim();
        if (!pin) return;

        try {
            const resp = await fetch(API_BASE + '/auth', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ pin: pin })
            });
            const data = await resp.json();

            if (resp.ok && data.token) {
                sessionToken = data.token;
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
        const url = playlistUrl.value.trim();
        if (!url) return;

        try {
            const result = await apiCall('POST', '/playlists', { url: url });
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
            const result = await apiCall('GET', '/playlists');
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
            const result = await apiCall('GET', '/stats');
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
            const result = await apiCall('GET', '/stats/recent');
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
            const result = await apiCall('GET', '/status');
            if (result.status === 200 && result.data.currentlyPlaying) {
                nowPlaying.classList.remove('hidden');
                nowPlayingId.textContent = result.data.currentlyPlaying.videoId;
            } else {
                nowPlaying.classList.add('hidden');
            }
        } catch (err) {
            console.error('Load status failed:', err);
        }
    }

    function loadDashboard() {
        loadPlaylists();
        loadStats();
        loadRecent();
        loadStatus();

        // Auto-refresh every 30s
        setInterval(function() {
            loadStats();
            loadStatus();
        }, 30000);
    }

    function escapeHtml(str) {
        var div = document.createElement('div');
        div.textContent = str;
        return div.innerHTML;
    }
})();
