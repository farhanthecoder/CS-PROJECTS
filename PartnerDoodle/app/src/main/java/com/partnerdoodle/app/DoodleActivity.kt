package com.partnerdoodle.app

import android.app.KeyguardManager
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.partnerdoodle.app.databinding.ActivityDoodleBinding
import kotlinx.coroutines.launch

/**
 * Full-screen drawing canvas that is allowed to show over the lock screen.
 * After drawing, the user taps Send and the doodle is uploaded to Firebase
 * so the partner's wallpaper updates automatically.
 */
class DoodleActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDoodleBinding
    private var selectedColor = Color.BLACK

    // Palette: 12 carefully chosen colours + white eraser hint
    private val palette = listOf(
        "#000000", "#FFFFFF", "#FF3B30", "#FF9500",
        "#FFCC00", "#34C759", "#30B0C7", "#007AFF",
        "#5856D6", "#FF2D55", "#A2845E", "#636366"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show over lock screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        binding = ActivityDoodleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupColorPalette()
        setupBrushControls()
        setupActionButtons()
    }

    // ── Toolbar ──────────────────────────────────────────────────────────────

    private fun setupToolbar() {
        binding.btnClose.setOnClickListener { finish() }
        binding.btnUndo.setOnClickListener { binding.doodleView.undo() }
        binding.btnRedo.setOnClickListener { binding.doodleView.redo() }
        binding.btnClear.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Clear canvas?")
                .setMessage("This will erase your entire doodle.")
                .setPositiveButton("Clear") { _, _ -> binding.doodleView.clear() }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    // ── Color palette ────────────────────────────────────────────────────────

    private fun setupColorPalette() {
        val container = binding.colorPaletteContainer
        container.removeAllViews()

        palette.forEach { hex ->
            val color = Color.parseColor(hex)
            val dot = layoutInflater.inflate(R.layout.item_color_dot, container, false)
            val dotView = dot.findViewById<View>(R.id.colorDot)
            dotView.setBackgroundColor(color)
            dotView.setOnClickListener {
                selectedColor = color
                binding.doodleView.isEraser = false
                binding.doodleView.brushColor = color
                updateSelectedColorIndicator(color)
            }
            container.addView(dot)
        }
    }

    private fun updateSelectedColorIndicator(color: Int) {
        binding.selectedColorIndicator.setBackgroundColor(color)
    }

    // ── Brush size ───────────────────────────────────────────────────────────

    private fun setupBrushControls() {
        binding.seekBarBrush.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                binding.doodleView.brushSize = (progress + 4).toFloat()
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
        binding.seekBarBrush.progress = 8  // default ~12px

        binding.btnEraser.setOnClickListener {
            binding.doodleView.isEraser = !binding.doodleView.isEraser
            binding.btnEraser.alpha = if (binding.doodleView.isEraser) 1f else 0.5f
        }
    }

    // ── Send / Background ────────────────────────────────────────────────────

    private fun setupActionButtons() {
        binding.btnSend.setOnClickListener { sendDoodle() }

        binding.btnBackground.setOnClickListener {
            val backgroundColors = listOf(
                Color.WHITE, Color.BLACK,
                Color.parseColor("#1C1C1E"), Color.parseColor("#F2F2F7"),
                Color.parseColor("#FFE5E5"), Color.parseColor("#E5FFE9")
            )
            val names = arrayOf("White", "Black", "Dark", "Light Gray", "Rose", "Mint")
            MaterialAlertDialogBuilder(this)
                .setTitle("Background colour")
                .setItems(names) { _, idx ->
                    binding.doodleView.canvasColor = backgroundColors[idx]
                }
                .show()
        }
    }

    private fun sendDoodle() {
        if (binding.doodleView.isEmpty()) {
            Toast.makeText(this, "Draw something first!", Toast.LENGTH_SHORT).show()
            return
        }

        val (userId, partnerId) = FirebaseManager.loadUserFromPrefs(this)
        if (userId == null) {
            Toast.makeText(this, "Please sign in first.", Toast.LENGTH_SHORT).show()
            return
        }
        if (partnerId == null) {
            Toast.makeText(this, "Pair with your partner first.", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnSend.isEnabled = false
        binding.sendProgressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            val bitmap = binding.doodleView.getBitmap()
            val result = FirebaseManager.uploadDoodle(userId, bitmap)

            binding.btnSend.isEnabled = true
            binding.sendProgressBar.visibility = View.GONE

            result.fold(
                onSuccess = {
                    Toast.makeText(
                        this@DoodleActivity,
                        "Doodle sent to your partner! 💌",
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                },
                onFailure = { e ->
                    Toast.makeText(
                        this@DoodleActivity,
                        "Failed to send: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            )
        }
    }
}
