package com.partnerdoodle.app

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.partnerdoodle.app.databinding.ActivityPairingBinding
import kotlinx.coroutines.launch

class PairingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPairingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPairingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val (userId, _) = FirebaseManager.loadUserFromPrefs(this)
        if (userId != null) {
            binding.tvYourCodeValue.text = userId.takeLast(6).uppercase()
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.btnSubmitCode.setOnClickListener { submitCode() }
    }

    private fun submitCode() {
        val code = binding.etPartnerCode.text.toString().trim()
        if (code.length != 6) {
            binding.tilPartnerCode.error = "Code must be 6 characters"
            return
        }
        binding.tilPartnerCode.error = null

        val (userId, _) = FirebaseManager.loadUserFromPrefs(this)
        if (userId == null) {
            Toast.makeText(this, "Not signed in yet. Try again shortly.", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnSubmitCode.isEnabled = false
        binding.pairingProgressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            val result = FirebaseManager.pairWithCode(userId, code)
            binding.btnSubmitCode.isEnabled = true
            binding.pairingProgressBar.visibility = View.GONE

            result.fold(
                onSuccess = { partnerId ->
                    FirebaseManager.saveUserToPrefs(this@PairingActivity, userId, partnerId)
                    Toast.makeText(
                        this@PairingActivity,
                        "Paired successfully! 💕",
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                },
                onFailure = { e ->
                    binding.tilPartnerCode.error = e.message ?: "Pairing failed"
                }
            )
        }
    }
}
