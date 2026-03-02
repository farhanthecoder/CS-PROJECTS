package com.partnerdoodle.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.*
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream

object FirebaseManager {

    private const val TAG = "FirebaseManager"

    // Doodles are stored as Base64 JPEG directly in the Realtime Database —
    // no Firebase Storage (paid plan) required.
    private const val MAX_DOODLE_SIDE = 720   // max width or height before scaling

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val database: FirebaseDatabase by lazy { FirebaseDatabase.getInstance() }

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

    suspend fun createPairingCode(userId: String): Result<String> {
        return try {
            val code = userId.takeLast(6).uppercase()
            database.reference.child("pairingCodes").child(code).setValue(userId).await()
            Result.success(code)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pairWithCode(myUserId: String, code: String): Result<String> {
        return try {
            val snapshot = database.reference
                .child("pairingCodes").child(code.uppercase()).get().await()

            val partnerId = snapshot.getValue(String::class.java)
                ?: return Result.failure(Exception("Invalid code"))

            if (partnerId == myUserId)
                return Result.failure(Exception("You cannot pair with yourself"))

            database.reference.child("users").child(myUserId).child("partnerId")
                .setValue(partnerId).await()
            database.reference.child("users").child(partnerId).child("partnerId")
                .setValue(myUserId).await()

            Result.success(partnerId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPartnerId(userId: String): String? {
        return try {
            database.reference.child("users").child(userId).child("partnerId")
                .get().await().getValue(String::class.java)
        } catch (e: Exception) { null }
    }

    // ── Doodle send / receive (Base64 in RTDB, no Storage needed) ───────────

    /**
     * Scales the bitmap down so its longest side is at most [MAX_DOODLE_SIDE],
     * compresses to JPEG, Base64-encodes it, and writes it directly into the
     * Realtime Database under `doodles/{userId}`.
     * The partner's [listenForPartnerDoodle] fires as soon as the write lands.
     */
    suspend fun uploadDoodle(userId: String, bitmap: Bitmap): Result<String> {
        return try {
            val scaled = scaleBitmap(bitmap, MAX_DOODLE_SIDE)
            val bytes = ByteArrayOutputStream().apply {
                scaled.compress(Bitmap.CompressFormat.JPEG, 72, this)
            }.toByteArray()
            val base64 = Base64.encodeToString(bytes, Base64.DEFAULT)

            val doodleData = mapOf(
                "imageData"  to base64,
                "timestamp"  to ServerValue.TIMESTAMP,
                "fromUserId" to userId
            )
            database.reference.child("doodles").child(userId).setValue(doodleData).await()
            Result.success(base64)
        } catch (e: Exception) {
            Log.e(TAG, "Doodle upload failed", e)
            Result.failure(e)
        }
    }

    /**
     * Listens for changes to the partner's doodle node and decodes the
     * Base64 image into a [Bitmap] on arrival.
     */
    fun listenForPartnerDoodle(
        partnerId: String,
        onDoodleChanged: (bitmap: Bitmap, timestamp: Long) -> Unit
    ): ValueEventListener {
        val ref = database.reference.child("doodles").child(partnerId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val base64 = snapshot.child("imageData").getValue(String::class.java) ?: return
                val ts = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L
                val bmp = base64ToBitmap(base64) ?: return
                onDoodleChanged(bmp, ts)
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

    // ── Bitmap helpers ───────────────────────────────────────────────────────

    private fun scaleBitmap(src: Bitmap, maxSide: Int): Bitmap {
        val w = src.width; val h = src.height
        if (w <= maxSide && h <= maxSide) return src
        val scale = maxSide.toFloat() / maxOf(w, h)
        return Bitmap.createScaledBitmap(src, (w * scale).toInt(), (h * scale).toInt(), true)
    }

    fun base64ToBitmap(base64: String): Bitmap? {
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) { null }
    }

    // ── SharedPreferences helpers ────────────────────────────────────────────

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

    fun savePartnerDoodleData(context: Context, base64: String) {
        context.getSharedPreferences("partner_doodle_prefs", Context.MODE_PRIVATE).edit()
            .putString("partnerDoodleData", base64)
            .apply()
    }

    fun loadPartnerDoodleData(context: Context): String? =
        context.getSharedPreferences("partner_doodle_prefs", Context.MODE_PRIVATE)
            .getString("partnerDoodleData", null)
}
