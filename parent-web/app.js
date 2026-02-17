// ==========================================
// KidsWatch Parent Website
// Firebase config — replace after creating project
// ==========================================

const firebaseConfig = {
    apiKey: "YOUR_API_KEY",
    authDomain: "YOUR_PROJECT.firebaseapp.com",
    projectId: "YOUR_PROJECT_ID",
    storageBucket: "YOUR_PROJECT.appspot.com",
    messagingSenderId: "YOUR_SENDER_ID",
    appId: "YOUR_APP_ID"
};

firebase.initializeApp(firebaseConfig);
const auth = firebase.auth();
const db = firebase.firestore();
const googleProvider = new firebase.auth.GoogleAuthProvider();

// ==========================================
// State
// ==========================================
let currentUser = null;
let playlistsUnsubscribe = null;
let devicesUnsubscribe = null;
let pendingPlaylist = null; // { youtube_pl_id, display_name }

// ==========================================
// DOM refs
// ==========================================
const $ = (id) => document.getElementById(id);

const authScreen = $('auth-screen');
const dashboardScreen = $('dashboard-screen');
const btnGoogleSignin = $('btn-google-signin');
const btnSignout = $('btn-signout');
const userPhoto = $('user-photo');
const userName = $('user-name');

const playlistUrlInput = $('playlist-url-input');
const btnAddPlaylist = $('btn-add-playlist');
const playlistPreview = $('playlist-preview');
const previewTitle = $('preview-title');
const btnConfirmPlaylist = $('btn-confirm-playlist');
const btnCancelPlaylist = $('btn-cancel-playlist');
const addError = $('add-error');
const playlistsList = $('playlists-list');

const pairingCodeInput = $('pairing-code-input');
const btnPairTv = $('btn-pair-tv');
const pairError = $('pair-error');
const pairSuccess = $('pair-success');
const pairedTvsList = $('paired-tvs-list');

const debugPanel = $('debug-panel');
const debugFamily = $('debug-family');
const debugPlaylists = $('debug-playlists');
const debugDevices = $('debug-devices');

// ==========================================
// Auth
// ==========================================
btnGoogleSignin.addEventListener('click', async () => {
    try {
        await auth.signInWithPopup(googleProvider);
    } catch (err) {
        console.error('Sign-in failed:', err);
        alert('Sign-in failed: ' + err.message);
    }
});

btnSignout.addEventListener('click', async () => {
    if (playlistsUnsubscribe) playlistsUnsubscribe();
    if (devicesUnsubscribe) devicesUnsubscribe();
    await auth.signOut();
});

auth.onAuthStateChanged(async (user) => {
    currentUser = user;
    if (user) {
        authScreen.classList.add('hidden');
        dashboardScreen.classList.remove('hidden');
        userPhoto.src = user.photoURL || '';
        userName.textContent = user.displayName || user.email;

        // Create/merge family doc on first sign-in
        await db.doc(`families/${user.uid}`).set({
            display_name: user.displayName || '',
            email: user.email || '',
            photo_url: user.photoURL || '',
            created_at: firebase.firestore.FieldValue.serverTimestamp(),
        }, { merge: true });

        // Show debug panel if ?debug=1
        if (new URLSearchParams(window.location.search).get('debug') === '1') {
            debugPanel.classList.remove('hidden');
            setupDebugListeners(user.uid);
        }

        listenPlaylists(user.uid);
        listenDevices(user.uid);
    } else {
        authScreen.classList.remove('hidden');
        dashboardScreen.classList.add('hidden');
    }
});

// ==========================================
// Playlists
// ==========================================

function extractPlaylistId(input) {
    // Handle full URLs and bare IDs
    const urlMatch = input.match(/[?&]list=([a-zA-Z0-9_-]+)/);
    if (urlMatch) return urlMatch[1];
    // Bare playlist ID
    if (/^PL[a-zA-Z0-9_-]+$/.test(input.trim())) return input.trim();
    return null;
}

async function validatePlaylist(playlistId) {
    // Use YouTube oEmbed to validate and get title
    const url = `https://www.youtube.com/oembed?url=https://www.youtube.com/playlist?list=${playlistId}&format=json`;
    const resp = await fetch(url);
    if (!resp.ok) return null;
    const data = await resp.json();
    return data.title || 'Untitled Playlist';
}

btnAddPlaylist.addEventListener('click', async () => {
    const input = playlistUrlInput.value.trim();
    if (!input) return;

    hideError(addError);
    playlistPreview.classList.add('hidden');
    btnAddPlaylist.disabled = true;

    const playlistId = extractPlaylistId(input);
    if (!playlistId) {
        showError(addError, 'Invalid playlist URL. Paste a YouTube playlist URL.');
        btnAddPlaylist.disabled = false;
        return;
    }

    try {
        const title = await validatePlaylist(playlistId);
        if (!title) {
            showError(addError, 'Playlist not found or is private. Only public playlists are supported.');
            btnAddPlaylist.disabled = false;
            return;
        }

        // Check for duplicates
        const existing = await db.collection(`families/${currentUser.uid}/playlists`)
            .where('youtube_pl_id', '==', playlistId).get();
        if (!existing.empty) {
            showError(addError, 'This playlist is already added.');
            btnAddPlaylist.disabled = false;
            return;
        }

        pendingPlaylist = { youtube_pl_id: playlistId, display_name: title };
        previewTitle.textContent = title;
        playlistPreview.classList.remove('hidden');
    } catch (err) {
        showError(addError, 'Failed to validate playlist: ' + err.message);
    }
    btnAddPlaylist.disabled = false;
});

btnConfirmPlaylist.addEventListener('click', async () => {
    if (!pendingPlaylist || !currentUser) return;
    btnConfirmPlaylist.disabled = true;

    try {
        await db.collection(`families/${currentUser.uid}/playlists`).add({
            youtube_pl_id: pendingPlaylist.youtube_pl_id,
            display_name: pendingPlaylist.display_name,
            added_at: firebase.firestore.FieldValue.serverTimestamp(),
        });
        playlistUrlInput.value = '';
        playlistPreview.classList.add('hidden');
        pendingPlaylist = null;
    } catch (err) {
        showError(addError, 'Failed to add playlist: ' + err.message);
    }
    btnConfirmPlaylist.disabled = false;
});

btnCancelPlaylist.addEventListener('click', () => {
    playlistPreview.classList.add('hidden');
    pendingPlaylist = null;
});

function listenPlaylists(uid) {
    if (playlistsUnsubscribe) playlistsUnsubscribe();

    playlistsUnsubscribe = db.collection(`families/${uid}/playlists`)
        .orderBy('added_at', 'desc')
        .onSnapshot((snapshot) => {
            if (snapshot.empty) {
                playlistsList.innerHTML = '<p class="empty-state">No playlists yet. Paste a YouTube playlist URL above.</p>';
                return;
            }

            playlistsList.innerHTML = '';
            snapshot.forEach((doc) => {
                const data = doc.data();
                const item = document.createElement('div');
                item.className = 'playlist-item';
                item.innerHTML = `
                    <span class="playlist-item-name">${escapeHtml(data.display_name)}</span>
                    <button class="btn btn-danger" data-id="${doc.id}">Remove</button>
                `;
                item.querySelector('button').addEventListener('click', () => {
                    db.doc(`families/${uid}/playlists/${doc.id}`).delete();
                });
                playlistsList.appendChild(item);
            });
        });
}

// ==========================================
// TV Pairing
// ==========================================

btnPairTv.addEventListener('click', async () => {
    const code = pairingCodeInput.value.trim().toUpperCase();
    if (!code) return;

    hideError(pairError);
    hideError(pairSuccess);
    btnPairTv.disabled = true;

    try {
        const oneHourAgo = new Date(Date.now() - 60 * 60 * 1000);
        const snapshot = await db.collection('tv_devices')
            .where('pairing_code', '==', code)
            .where('family_id', '==', null)
            .where('created_at', '>', oneHourAgo)
            .get();

        if (snapshot.empty) {
            showError(pairError, 'Code not found or expired. Check the code on your TV screen.');
            btnPairTv.disabled = false;
            return;
        }

        const deviceDoc = snapshot.docs[0];
        await deviceDoc.ref.update({
            family_id: currentUser.uid,
            paired_at: firebase.firestore.FieldValue.serverTimestamp(),
        });

        pairingCodeInput.value = '';
        showSuccess(pairSuccess, 'TV paired successfully!');
    } catch (err) {
        showError(pairError, 'Pairing failed: ' + err.message);
    }
    btnPairTv.disabled = false;
});

function listenDevices(uid) {
    if (devicesUnsubscribe) devicesUnsubscribe();

    devicesUnsubscribe = db.collection('tv_devices')
        .where('family_id', '==', uid)
        .onSnapshot((snapshot) => {
            if (snapshot.empty) {
                pairedTvsList.innerHTML = '<p class="empty-state">No TVs paired yet.</p>';
                return;
            }

            pairedTvsList.innerHTML = '';
            snapshot.forEach((doc) => {
                const data = doc.data();
                const pairedAt = data.paired_at ? data.paired_at.toDate().toLocaleDateString() : 'unknown';
                const item = document.createElement('div');
                item.className = 'paired-tv-item';
                item.innerHTML = `
                    <span class="paired-tv-code">${escapeHtml(data.pairing_code || '???')}</span>
                    <span class="paired-tv-time">Paired ${pairedAt}</span>
                `;
                pairedTvsList.appendChild(item);
            });
        });
}

// ==========================================
// Debug Panel
// ==========================================

function setupDebugListeners(uid) {
    // Family doc
    db.doc(`families/${uid}`).onSnapshot((doc) => {
        debugFamily.textContent = JSON.stringify(doc.data(), null, 2);
    });

    // Playlists
    db.collection(`families/${uid}/playlists`).onSnapshot((snapshot) => {
        const items = [];
        snapshot.forEach((doc) => items.push({ id: doc.id, ...doc.data() }));
        debugPlaylists.textContent = JSON.stringify(items, null, 2);
    });

    // Devices
    db.collection('tv_devices').where('family_id', '==', uid).onSnapshot((snapshot) => {
        const items = [];
        snapshot.forEach((doc) => items.push({ id: doc.id, ...doc.data() }));
        debugDevices.textContent = JSON.stringify(items, null, 2);
    });
}

// ==========================================
// Helpers
// ==========================================

function showError(el, msg) {
    el.textContent = msg;
    el.classList.remove('hidden');
}

function showSuccess(el, msg) {
    el.textContent = msg;
    el.classList.remove('hidden');
}

function hideError(el) {
    el.classList.add('hidden');
}

function escapeHtml(str) {
    const div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
}

// Allow Enter key on inputs
playlistUrlInput.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') btnAddPlaylist.click();
});
pairingCodeInput.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') btnPairTv.click();
});
