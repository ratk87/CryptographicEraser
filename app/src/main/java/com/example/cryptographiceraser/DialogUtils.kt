package com.example.cryptographiceraser

import android.content.Context
import android.widget.Toast


object DialogUtils {
    fun showToast(context: Context, msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }
}
