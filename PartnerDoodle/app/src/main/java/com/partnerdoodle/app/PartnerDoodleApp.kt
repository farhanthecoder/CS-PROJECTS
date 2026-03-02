package com.partnerdoodle.app

import android.app.Application
import com.google.firebase.FirebaseApp

class PartnerDoodleApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
    }
}
