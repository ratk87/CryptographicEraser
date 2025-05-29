package com.example.cryptographiceraser

import android.os.Build

object DeviceUtils {
    fun isScopedStorageEnforced(): Boolean {
        // Scoped Storage is being used starting with Android 10 (API 29)
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    }

    fun getAndroidVersionString(): String {
        return "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")"
    }
}
