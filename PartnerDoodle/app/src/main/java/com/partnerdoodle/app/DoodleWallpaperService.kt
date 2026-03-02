package com.partnerdoodle.app

import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.SurfaceHolder
import com.google.firebase.database.*

/**
 * Live wallpaper that renders the partner's latest doodle.
 *
 * On start it reads the user's own RTDB record to discover the current
 * partnerId, then attaches a doodle listener on that partner.  It also
 * watches the partnerId node for changes, so if the user re-pairs (or the
 * partner runs a new PC session) the wallpaper automatically switches to
 * the new partner's doodles without needing a restart.
 */
class DoodleWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = DoodleEngine()

    inner class DoodleEngine : Engine() {

        private val handler = Handler(Looper.getMainLooper())
        private var doodleBitmap: Bitmap? = null

        private var myUserId: String? = null
        private var currentPartnerId: String? = null

        private var partnerIdListener: ValueEventListener? = null
        private var doodleListener: ValueEventListener? = null

        private val db get() = FirebaseDatabase.getInstance().reference

        // ── Paints ───────────────────────────────────────────────────────────

        private val bgPaint = Paint().apply { color = Color.parseColor("#1C1C2E") }

        private val emojiPaint = Paint().apply {
            color = Color.WHITE; textSize = 96f
            textAlign = Paint.Align.CENTER; isAntiAlias = true
        }
        private val labelPaint = Paint().apply {
            color = Color.WHITE; textSize = 42f
            textAlign = Paint.Align.CENTER; isAntiAlias = true
        }
        private val subPaint = Paint().apply {
            color = Color.parseColor("#AEAEB2"); textSize = 32f
            textAlign = Paint.Align.CENTER; isAntiAlias = true
        }

        // ── Engine lifecycle ─────────────────────────────────────────────────

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)

            val (uid, _) = FirebaseManager.loadUserFromPrefs(applicationContext)
            myUserId = uid ?: return

            // Show cached doodle immediately while we wait for the listener
            FirebaseManager.loadPartnerDoodleData(applicationContext)?.let { b64 ->
                doodleBitmap = FirebaseManager.base64ToBitmap(b64)
            }

            // Watch partnerId in RTDB so we react to re-pairing automatically
            watchPartnerId(uid)
        }

        override fun onDestroy() {
            super.onDestroy()
            detachListeners()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            if (visible) drawFrame()
        }

        override fun onSurfaceChanged(
            holder: SurfaceHolder, format: Int, width: Int, height: Int
        ) {
            super.onSurfaceChanged(holder, format, width, height)
            drawFrame()
        }

        // ── RTDB listeners ───────────────────────────────────────────────────

        /**
         * Watches `users/{uid}/partnerId`.  Whenever it changes (initial load,
         * or after a new pairing), we switch the doodle listener to the new partner.
         */
        private fun watchPartnerId(uid: String) {
            val ref = db.child("users").child(uid).child("partnerId")
            partnerIdListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val newPartnerId = snapshot.getValue(String::class.java) ?: return
                    if (newPartnerId == currentPartnerId) return  // no change

                    // Detach old doodle listener
                    currentPartnerId?.let { old ->
                        doodleListener?.let { db.child("doodles").child(old).removeEventListener(it) }
                    }

                    currentPartnerId = newPartnerId
                    FirebaseManager.saveUserToPrefs(applicationContext, uid, newPartnerId)
                    attachDoodleListener(newPartnerId)
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e("DoodleWallpaper", "partnerId listener cancelled: ${error.message}")
                }
            }
            ref.addValueEventListener(partnerIdListener!!)
        }

        private fun attachDoodleListener(partnerId: String) {
            val ref = db.child("doodles").child(partnerId)
            doodleListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val b64 = snapshot.child("imageData").getValue(String::class.java) ?: return
                    val bmp = FirebaseManager.base64ToBitmap(b64) ?: return
                    FirebaseManager.savePartnerDoodleData(applicationContext, b64)
                    doodleBitmap = bmp
                    drawFrame()
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e("DoodleWallpaper", "doodle listener cancelled: ${error.message}")
                }
            }
            ref.addValueEventListener(doodleListener!!)
        }

        private fun detachListeners() {
            val uid = myUserId ?: return
            partnerIdListener?.let {
                db.child("users").child(uid).child("partnerId").removeEventListener(it)
            }
            currentPartnerId?.let { pId ->
                doodleListener?.let { db.child("doodles").child(pId).removeEventListener(it) }
            }
        }

        // ── Drawing ──────────────────────────────────────────────────────────

        private fun drawFrame() {
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) render(canvas)
            } finally {
                canvas?.let {
                    try { holder.unlockCanvasAndPost(it) } catch (_: Exception) {}
                }
            }
        }

        private fun render(canvas: Canvas) {
            val w = canvas.width.toFloat()
            val h = canvas.height.toFloat()
            canvas.drawRect(0f, 0f, w, h, bgPaint)

            val bmp = doodleBitmap
            if (bmp != null && !bmp.isRecycled) {
                val scale = minOf(w / bmp.width, h / bmp.height)
                val sw = bmp.width * scale
                val sh = bmp.height * scale
                val matrix = Matrix().apply {
                    postScale(scale, scale)
                    postTranslate((w - sw) / 2f, (h - sh) / 2f)
                }
                canvas.drawBitmap(bmp, matrix, null)
            } else {
                canvas.drawText("💌", w / 2f, h / 2f - 60f, emojiPaint)
                canvas.drawText("Waiting for your", w / 2f, h / 2f + 30f, labelPaint)
                canvas.drawText("partner's doodle…", w / 2f, h / 2f + 82f, subPaint)
            }
        }
    }
}
