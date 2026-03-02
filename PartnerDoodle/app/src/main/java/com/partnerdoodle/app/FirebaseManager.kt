package com.partnerdoodle.app

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.util.UUID

object FirebaseManager {

    private const val TAG = "FirebaseManager"

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val database: FirebaseDatabase by lazy { FirebaseDatabase.getInstance() }
    private val storage: FirebaseStorage by lazy { FirebaseStorage.getInstance() }

    val currentUser: FirebaseUser? get() = auth.currentUser
    val currentUserId: String? get() = auth.currentUser?.uid

    // ── Authentication ──────────────────────────────────────────────────────

    suspend fun signInAnonymously(): Result<FirebaseUser> {
        return try {
            val result = auth.signInAnonymously().await()
            Result.success(result.user!!)
        } catch (e: Exception) {
            Log.e(TAG, "Anonymous sign-in failed", e)
            Result.failure(e)
        }
    }

    // ── Pairing ─────────────────────────────────────────────────────────────

    /** Creates a unique pairing code tied to this user's UID and stores it in the DB. */
    suspend fun createPairingCode(userId: String): Result<String> {
        return try {
            val code = userId.takeLast(6).uppercase()
            val ref = database.reference.child("pairingCodes").child(code)
            ref.setValue(userId).await()
            Result.success(code)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Looks up the UID behind a pairing code and links the two users as partners. */
    suspend fun pairWithCode(myUserId: String, code: String): Result<String> {
        return try {
            val snapshot = database.reference
                .child("pairingCodes")
                .child(code.uppercase())
                .get().await()

            val partnerId = snapshot.getValue(String::class.java)
                ?: return Result.failure(Exception("Invalid code"))

            if (partnerId == myUserId) {
                return Result.failure(Exception("You cannot pair with yourself"))
            }

            // Write partner relationship for both users
            database.reference.child("users").child(myUserId).child("partnerId")
                .setValue(partnerId).await()
            database.reference.child("users").child(partnerId).child("partnerId")
                .setValue(myUserId).await()

            Result.success(partnerId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Returns the stored partner UID, if any. */
    suspend fun getPartnerId(userId: String): String? {
        return try {
            val snap = database.reference
                .child("users").child(userId).child("partnerId")
                .get().await()
            snap.getValue(String::class.java)
        } catch (e: Exception) {
            null
        }
    }

    // ── Doodle upload / download ─────────────────────────────────────────────

    /**
     * Compresses [bitmap] to JPEG, uploads to Storage, then writes the download URL
     * into the Realtime Database so the partner's device is notified immediately.
     */
    suspend fun uploadDoodle(userId: String, bitmap: Bitmap): Result<String> {
        return try {
            val bytes = ByteArrayOutputStream().apply {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, this)
            }.toByteArray()

            val ref = storage.reference.child("doodles/$userId/latest.jpg")
            val uploadTask = ref.putBytes(bytes).await()
            val downloadUrl = uploadTask.storage.downloadUrl.await().toString()

            // Store URL and timestamp so partner is notified via the listener
            val doodleData = mapOf(
                "url" to downloadUrl,
                "timestamp" to ServerValue.TIMESTAMP,
                "fromUserId" to userId
            )
            database.reference.child("doodles").child(userId).setValue(doodleData).await()

            Result.success(downloadUrl)
        } catch (e: Exception) {
            Log.e(TAG, "Doodle upload failed", e)
            Result.failure(e)
        }
    }

    /**
     * Attaches a real-time listener for the partner's latest doodle URL.
     * [onDoodleChanged] is called with the download URL every time the partner
     * posts a new doodle.
     */
    fun listenForPartnerDoodle(
        partnerId: String,
        onDoodleChanged: (url: String, timestamp: Long) -> Unit
    ): ValueEventListener {
        val ref = database.reference.child("doodles").child(partnerId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val url = snapshot.child("url").getValue(String::class.java) ?: return
                val ts = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L
                onDoodleChanged(url, ts)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Partner doodle listener cancelled: ${error.message}")
            }
        }
        ref.addValueEventListener(listener)
        return listener
    }

    fun removeListener(partnerId: String, listener: ValueEventListener) {
        database.reference.child("doodles").child(partnerId).removeEventListener(listener)
    }

    // ── Preferences helpers ──────────────────────────────────────────────────

    fun saveUserToPrefs(context: Context, userId: String, partnerId: String?) {
        context.getSharedPreferences("partner_doodle_prefs", Context.MODE_PRIVATE).edit()
            .putString("userId", userId)
            .putString("partnerId", partnerId)
            .apply()
    }

    fun loadUserFromPrefs(context: Context): Pair<String?, String?> {
        val prefs = context.getSharedPreferences("partner_doodle_prefs", Context.MODE_PRIVATE)
        return Pair(prefs.getString("userId", null), prefs.getString("partnerId", null))
    }

    fun savePartnerDoodleUrl(context: Context, url: String) {
        context.getSharedPreferences("partner_doodle_prefs", Context.MODE_PRIVATE).edit()
            .putString("partnerDoodleUrl", url)
            .apply()
    }

    fun loadPartnerDoodleUrl(context: Context): String? {
        return context.getSharedPreferences("partner_doodle_prefs", Context.MODE_PRIVATE)
            .getString("partnerDoodleUrl", null)
    }
}
