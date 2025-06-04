package com.example.cryptographiceraser

import android.app.AlertDialog
import android.content.Context
import android.widget.Toast


var lastDialog: AlertDialog? = null

object DialogUtils {
    fun showToast(context: Context, msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }
}
