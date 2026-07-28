package com.badukai.next

import android.app.Application
import android.util.Log

class BadukNextApplication : Application() {
    companion object {
        private const val TAG = "BadukNextApplication"
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "BadukNext application created")
    }
}
