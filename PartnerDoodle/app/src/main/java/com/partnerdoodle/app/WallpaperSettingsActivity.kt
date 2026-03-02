package com.partnerdoodle.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.partnerdoodle.app.databinding.ActivityWallpaperSettingsBinding

/** Stub settings activity required by the WallpaperService meta-data. */
class WallpaperSettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityWallpaperSettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWallpaperSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnClose.setOnClickListener { finish() }
    }
}
