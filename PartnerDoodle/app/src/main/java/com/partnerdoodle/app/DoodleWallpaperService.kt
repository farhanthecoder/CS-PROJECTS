package com.partnerdoodle.app

import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.SurfaceHolder
import com.google.firebase.database.ValueEventListener

/**
 * Live wallpaper that renders the partner's latest doodle.
 * The doodle arrives as a Base64 JPEG string from Firebase Realtime Database
 * (no Firebase Storage required).
 */
class DoodleWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = DoodleEngine()

    inner class DoodleEngine : Engine() {

        private val handler = Handler(Looper.getMainLooper())
        private var doodleBitmap: Bitmap? = null
        private var firebaseListener: ValueEventListener? = null
        private var visible = false
        private var partnerId: String? = null

        private val backgroundPaint = Paint().apply {
            color = Color.parseColor("#1C1C2E")
        }

        private val emojiPaint = Paint().apply {
            color = Color.WHITE
            textSize = 96f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        private val labelPaint = Paint().apply {
            color = Color.WHITE
            textSize = 42f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        private val subLabelPaint = Paint().apply {
            color = Color.parseColor("#AEAEB2")
            textSize = 32f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        // ── Engine lifecycle ─────────────────────────────────────────────────

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            val (_, pId) = FirebaseManager.loadUserFromPrefs(applicationContext)
            partnerId = pId

            // Load cached doodle immediately so the wallpaper isn't blank on restart
            FirebaseManager.loadPartnerDoodleData(applicationContext)?.let { base64 ->
                doodleBitmap = FirebaseManager.base64ToBitmap(base64)
            }

            if (pId != null) attachFirebaseListener(pId)
        }

        override fun onDestroy() {
            super.onDestroy()
            partnerId?.let { pId ->
                firebaseListener?.let { FirebaseManager.removeListener(pId, it) }
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) drawFrame()
        }

        override fun onSurfaceChanged(
            holder: SurfaceHolder, format: Int, width: Int, height: Int
        ) {
            super.onSurfaceChanged(holder, format, width, height)
            drawFrame()
        }

        // ── Firebase listener ────────────────────────────────────────────────

        private fun attachFirebaseListener(pId: String) {
            firebaseListener = FirebaseManager.listenForPartnerDoodle(pId) { bmp, _ ->
                doodleBitmap = bmp
                drawFrame()
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

            canvas.drawRect(0f, 0f, w, h, backgroundPaint)

            val bmp = doodleBitmap
            if (bmp != null && !bmp.isRecycled) {
                val scale = minOf(w / bmp.width, h / bmp.height)
                val scaledW = bmp.width * scale
                val scaledH = bmp.height * scale
                val matrix = Matrix().apply {
                    postScale(scale, scale)
                    postTranslate((w - scaledW) / 2f, (h - scaledH) / 2f)
                }
                canvas.drawBitmap(bmp, matrix, null)
            } else {
                canvas.drawText("💌", w / 2f, h / 2f - 60f, emojiPaint)
                canvas.drawText("Waiting for your", w / 2f, h / 2f + 30f, labelPaint)
                canvas.drawText("partner's doodle…", w / 2f, h / 2f + 82f, subLabelPaint)
            }
        }
    }
}
