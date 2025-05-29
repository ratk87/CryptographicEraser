package com.example.cryptographiceraser

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.widget.EditText
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import android.util.Log
import android.app.Activity
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

var lastDialog: AlertDialog? = null

suspend fun requestPasswordDialog(
    activity: AppCompatActivity,
    title: String = "Enter password for shredding"
): CharArray? = suspendCancellableCoroutine { continuation ->
    val fragmentManager = activity.supportFragmentManager

    val passwordDialog = PasswordDialogFragment()
    passwordDialog.title = title
    passwordDialog.callback = object : PasswordDialogFragment.PasswordCallback {
        override fun onPasswordEntered(password: CharArray) {
            if (continuation.isActive) continuation.resume(password)
        }
        override fun onPasswordCancelled() {
            if (continuation.isActive) continuation.resume(null)
        }
    }
    passwordDialog.show(fragmentManager, "PasswordDialogFragment")
}

/**
 * Shows an information dialog explaining Android storage and deletion restrictions.
 *
 * @param context The context to use for showing the dialog.
 */
fun showAndroidStorageInfoDialog(context: Context) {
    AlertDialog.Builder(context)
        .setTitle("Why can't files always be deleted?")
        .setMessage(
            "Due to Android's security and privacy rules, apps are not allowed to freely delete or modify files outside their own app folder. " +
                    "Even with full storage permissions, many files in folders like Downloads, Pictures, or other apps can only be accessed using the file picker (Storage Access Framework). " +
                    "Sometimes, the Android system or the app that created the file prevents deletion to protect your data. " +
                    "This is why, even after selecting a file, secure deletion is not always possible. " +
                    "\n\nIf a file cannot be deleted, you may have to remove it manually using a file manager app."
        )
        .setPositiveButton("OK", null)
        .show()
}
