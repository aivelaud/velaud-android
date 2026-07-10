package com.velaud.chat

import android.app.Application
import com.google.firebase.FirebaseApp

class VelaudApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
    }
}
