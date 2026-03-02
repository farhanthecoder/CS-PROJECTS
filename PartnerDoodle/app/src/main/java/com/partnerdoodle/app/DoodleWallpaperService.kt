package com.partnerdoodle.app

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.SurfaceHolder
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.*

/**
 * Live wallpaper that displays the partner's latest doodle.
 * It listens to Firebase Realtime Database for changes and redraws
 * the surface as soon as a new doodle URL arrives.
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
            color = Color.parseColor("#1C1C1E")
        }

        private val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 48f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }

        private val subTextPaint = Paint().apply {
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
            if (pId != null) attachFirebaseListener(pId)

            // Load cached doodle immediately
            val cachedUrl = FirebaseManager.loadPartnerDoodleUrl(applicationContext)
            if (cachedUrl != null) loadBitmapFromUrl(cachedUrl)
        }

        override fun onDestroy() {
            super.onDestroy()
            val pId = partnerId ?: return
            firebaseListener?.let { FirebaseManager.removeListener(pId, it) }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) drawFrame()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            drawFrame()
        }

        // ── Firebase listener ────────────────────────────────────────────────

        private fun attachFirebaseListener(pId: String) {
            firebaseListener = FirebaseManager.listenForPartnerDoodle(pId) { url, _ ->
                FirebaseManager.savePartnerDoodleUrl(applicationContext, url)
                loadBitmapFromUrl(url)
            }
        }

        // ── Bitmap loading ───────────────────────────────────────────────────

        private fun loadBitmapFromUrl(url: String) {
            Glide.with(applicationContext)
                .asBitmap()
                .load(url)
                .into(object : CustomTarget<Bitmap>() {
                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                        doodleBitmap = resource
                        drawFrame()
                    }

                    override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {
                        doodleBitmap = null
                    }
                })
        }

        // ── Drawing ──────────────────────────────────────────────────────────

        private fun drawFrame() {
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) draw(canvas)
            } finally {
                if (canvas != null) {
                    try { holder.unlockCanvasAndPost(canvas) } catch (_: Exception) {}
                }
            }
        }

        private fun draw(canvas: Canvas) {
            val w = canvas.width.toFloat()
            val h = canvas.height.toFloat()

            canvas.drawRect(0f, 0f, w, h, backgroundPaint)

            val bmp = doodleBitmap
            if (bmp != null && !bmp.isRecycled) {
                // Scale to fit while keeping aspect ratio
                val scale = minOf(w / bmp.width, h / bmp.height)
                val scaledW = bmp.width * scale
                val scaledH = bmp.height * scale
                val left = (w - scaledW) / 2f
                val top = (h - scaledH) / 2f

                val matrix = Matrix()
                matrix.postScale(scale, scale)
                matrix.postTranslate(left, top)

                canvas.drawBitmap(bmp, matrix, null)
            } else {
                // Placeholder when no doodle yet
                canvas.drawText("💌", w / 2f, h / 2f - 60f, textPaint.apply { textSize = 96f })
                canvas.drawText("Waiting for your", w / 2f, h / 2f + 30f, textPaint.apply { textSize = 42f })
                canvas.drawText("partner's doodle…", w / 2f, h / 2f + 85f, subTextPaint)
            }
        }
    }
}
