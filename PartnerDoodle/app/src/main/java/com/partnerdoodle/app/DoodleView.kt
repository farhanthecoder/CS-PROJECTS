package com.partnerdoodle.app

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/**
 * A touch-driven drawing canvas that supports variable brush sizes,
 * multiple colors, an eraser, and undo/redo.
 */
class DoodleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // ── Drawing state ────────────────────────────────────────────────────────

    private var canvasBitmap: Bitmap? = null
    private var drawingCanvas: Canvas? = null

    private val drawPaint = Paint().apply {
        color = Color.BLACK
        isAntiAlias = true
        isDither = true
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 12f
    }

    private val canvasPaint = Paint(Paint.DITHER_FLAG)

    private var currentPath = Path()
    private val paths = mutableListOf<Pair<Path, Paint>>()
    private val undoStack = mutableListOf<Pair<Path, Paint>>()

    private var lastX = 0f
    private var lastY = 0f

    var brushColor: Int
        get() = drawPaint.color
        set(value) {
            drawPaint.color = value
            drawPaint.xfermode = null  // reset eraser
        }

    var brushSize: Float
        get() = drawPaint.strokeWidth
        set(value) { drawPaint.strokeWidth = value }

    var isEraser: Boolean = false
        set(value) {
            field = value
            if (value) {
                drawPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                drawPaint.strokeWidth = 40f
            } else {
                drawPaint.xfermode = null
                drawPaint.strokeWidth = brushSize
            }
        }

    // ── Background ───────────────────────────────────────────────────────────

    var canvasColor: Int = Color.WHITE
        set(value) {
            field = value
            canvasBitmap?.eraseColor(value)
            invalidate()
        }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        canvasBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmp ->
            bmp.eraseColor(canvasColor)
            drawingCanvas = Canvas(bmp)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvasBitmap?.let { canvas.drawBitmap(it, 0f, 0f, canvasPaint) }
        canvas.drawPath(currentPath, drawPaint)
    }

    // ── Touch handling ───────────────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                currentPath = Path()
                currentPath.moveTo(x, y)
                lastX = x
                lastY = y
                undoStack.clear()
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = abs(x - lastX)
                val dy = abs(y - lastY)
                if (dx >= 4 || dy >= 4) {
                    currentPath.quadTo(lastX, lastY, (x + lastX) / 2, (y + lastY) / 2)
                    lastX = x
                    lastY = y
                }
            }

            MotionEvent.ACTION_UP -> {
                currentPath.lineTo(x, y)
                val savedPaint = Paint(drawPaint)
                drawingCanvas?.drawPath(currentPath, savedPaint)
                paths.add(Pair(currentPath, savedPaint))
                currentPath = Path()
            }
        }

        invalidate()
        return true
    }

    // ── Undo / Redo ──────────────────────────────────────────────────────────

    fun undo() {
        if (paths.isNotEmpty()) {
            undoStack.add(paths.removeLast())
            redrawAll()
        }
    }

    fun redo() {
        if (undoStack.isNotEmpty()) {
            paths.add(undoStack.removeLast())
            redrawAll()
        }
    }

    private fun redrawAll() {
        canvasBitmap?.eraseColor(canvasColor)
        paths.forEach { (path, paint) -> drawingCanvas?.drawPath(path, paint) }
        invalidate()
    }

    // ── Clear ────────────────────────────────────────────────────────────────

    fun clear() {
        paths.clear()
        undoStack.clear()
        currentPath = Path()
        canvasBitmap?.eraseColor(canvasColor)
        invalidate()
    }

    // ── Export ───────────────────────────────────────────────────────────────

    fun getBitmap(): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(canvasColor)
        draw(canvas)
        return bmp
    }

    fun isEmpty(): Boolean = paths.isEmpty()
}
