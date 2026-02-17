package com.kidswatch.tv.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.messaging.FirebaseMessaging
import com.kidswatch.tv.util.AppLogger

object FirebaseManager {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    val uid: String? get() = auth.currentUser?.uid

    fun signInAnonymously(onResult: (Boolean) -> Unit) {
        val existing = auth.currentUser
        if (existing != null) {
            AppLogger.log("Already signed in: ${existing.uid}")
            onResult(true)
            return
        }

        auth.signInAnonymously()
            .addOnSuccessListener {
                AppLogger.success("Anonymous auth OK: ${it.user?.uid}")
                onResult(true)
            }
            .addOnFailureListener {
                AppLogger.error("Anonymous auth FAILED: ${it.message}")
                onResult(false)
            }
    }

    fun getFcmToken(onToken: (String?) -> Unit) {
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { onToken(it) }
            .addOnFailureListener {
                AppLogger.warn("FCM token failed: ${it.message}")
                onToken(null)
            }
    }

    // --- TV Device Operations ---

    fun createDeviceDoc(pairingCode: String, fcmToken: String?, onResult: (Boolean) -> Unit) {
        val uid = uid ?: return onResult(false)
        db.collection("tv_devices").document(uid).set(
            mapOf(
                "device_uid" to uid,
                "pairing_code" to pairingCode,
                "fcm_token" to (fcmToken ?: ""),
                "family_id" to null,
                "created_at" to FieldValue.serverTimestamp(),
                "paired_at" to null,
            )
        )
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener {
                AppLogger.error("Create device doc failed: ${it.message}")
                onResult(false)
            }
    }

    fun listenDeviceDoc(onChange: (Map<String, Any>?) -> Unit): ListenerRegistration? {
        val uid = uid ?: return null
        return db.collection("tv_devices").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    AppLogger.error("Device listener error: ${error.message}")
                    return@addSnapshotListener
                }
                @Suppress("UNCHECKED_CAST")
                onChange(snapshot?.data as? Map<String, Any>)
            }
    }

    fun checkPairingCodeExists(code: String, onResult: (Boolean) -> Unit) {
        db.collection("tv_devices")
            .whereEqualTo("pairing_code", code)
            .get()
            .addOnSuccessListener { onResult(!it.isEmpty) }
            .addOnFailureListener { onResult(false) }
    }

    fun updateDeviceDoc(fields: Map<String, Any?>, onResult: (Boolean) -> Unit = {}) {
        val uid = uid ?: return onResult(false)
        db.collection("tv_devices").document(uid).update(fields)
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener {
                AppLogger.error("Update device doc failed: ${it.message}")
                onResult(false)
            }
    }

    fun deleteDeviceDoc(onResult: (Boolean) -> Unit = {}) {
        val uid = uid ?: return onResult(false)
        db.collection("tv_devices").document(uid).delete()
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { onResult(false) }
    }

    fun signOut() {
        auth.signOut()
    }

    // --- Playlist Operations ---

    fun listenPlaylists(familyId: String, onChange: (List<PlaylistMeta>) -> Unit): ListenerRegistration {
        return db.collection("families/$familyId/playlists")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    AppLogger.error("Playlists listener error: ${error.message}")
                    return@addSnapshotListener
                }
                val playlists = snapshot?.documents?.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    PlaylistMeta(
                        firestoreId = doc.id,
                        youtubePlaylistId = data["youtube_pl_id"] as? String ?: return@mapNotNull null,
                        displayName = data["display_name"] as? String ?: "Untitled",
                    )
                } ?: emptyList()
                onChange(playlists)
            }
    }

    // --- Play Events ---

    fun writePlayEvents(events: List<Map<String, Any>>, onResult: (Boolean) -> Unit) {
        if (events.isEmpty()) return onResult(true)
        val batch = db.batch()
        events.forEach { event ->
            val ref = db.collection("play_events").document()
            batch.set(ref, event)
        }
        batch.commit()
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener {
                AppLogger.error("Write play events failed: ${it.message}")
                onResult(false)
            }
    }
}

data class PlaylistMeta(
    val firestoreId: String,
    val youtubePlaylistId: String,
    val displayName: String,
)
