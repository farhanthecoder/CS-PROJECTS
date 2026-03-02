package com.partnerdoodle.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Restarts the wallpaper's Firebase listener after device reboot.
 * The live wallpaper service handles this automatically when it is re-created
 * by the system, so this receiver just ensures the prefs are accessible.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // The WallpaperService is re-created by Android automatically.
            // Any additional startup logic (e.g., refreshing cached URL) goes here.
        }
    }
}
