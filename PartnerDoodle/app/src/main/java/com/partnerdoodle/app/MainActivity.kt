package com.partnerdoodle.app

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.partnerdoodle.app.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initSession()
        setupButtons()
    }

    override fun onResume() {
        super.onResume()
        refreshUI()
    }

    // ── Session init ─────────────────────────────────────────────────────────

    private fun initSession() {
        val (savedUserId, _) = FirebaseManager.loadUserFromPrefs(this)

        if (savedUserId != null && FirebaseManager.currentUser != null) return

        binding.loadingOverlay.visibility = View.VISIBLE

        lifecycleScope.launch {
            val result = FirebaseManager.signInAnonymously()
            binding.loadingOverlay.visibility = View.GONE

            result.fold(
                onSuccess = { user ->
                    val (_, savedPartnerId) = FirebaseManager.loadUserFromPrefs(this@MainActivity)
                    FirebaseManager.saveUserToPrefs(this@MainActivity, user.uid, savedPartnerId)

                    // If no pairing code stored yet, create one now
                    FirebaseManager.createPairingCode(user.uid)
                    refreshUI()
                },
                onFailure = {
                    Toast.makeText(this@MainActivity, "Sign-in failed. Check your internet connection.", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    // ── UI refresh ───────────────────────────────────────────────────────────

    private fun refreshUI() {
        val (userId, partnerId) = FirebaseManager.loadUserFromPrefs(this)

        if (userId != null) {
            val code = userId.takeLast(6).uppercase()
            binding.tvYourCode.text = code
        }

        val paired = partnerId != null
        binding.partnerStatusIcon.setImageResource(
            if (paired) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
        )
        binding.tvPartnerStatus.text = if (paired) "Connected with partner" else "Not yet paired"
        binding.btnDoodle.isEnabled = paired
        binding.btnDoodle.alpha = if (paired) 1f else 0.4f
    }

    // ── Buttons ──────────────────────────────────────────────────────────────

    private fun setupButtons() {
        binding.btnPair.setOnClickListener {
            startActivity(Intent(this, PairingActivity::class.java))
        }

        binding.btnDoodle.setOnClickListener {
            startActivity(Intent(this, DoodleActivity::class.java))
        }

        binding.btnSetWallpaper.setOnClickListener {
            val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                putExtra(
                    WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    ComponentName(this@MainActivity, DoodleWallpaperService::class.java)
                )
            }
            startActivity(intent)
        }

        binding.btnCopyCode.setOnClickListener {
            val (userId, _) = FirebaseManager.loadUserFromPrefs(this)
            if (userId != null) {
                val code = userId.takeLast(6).uppercase()
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Pairing Code", code))
                Toast.makeText(this, "Code copied!", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
