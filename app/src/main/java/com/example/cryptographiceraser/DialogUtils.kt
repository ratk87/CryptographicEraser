package com.example.cryptographiceraser

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.widget.EditText
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Shows a password dialog and returns the entered password as a CharArray.
 * Input: Context (required for Dialog), dialog title (String, optional)
 * Output: CharArray with the entered password, or null if canceled.
 * Usage: Must be called from a coroutine (suspend).
 */
suspend fun requestPassword(
    context: Context,
    title: String = "Enter password for shredding"
): CharArray? = suspendCancellableCoroutine { continuation ->
    // Create password EditText
    val input = EditText(context).apply {
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        hint = "Password"
    }
    // Build the dialog
    val dialog = AlertDialog.Builder(context)
        .setTitle(title)
        .setView(input)
        .setCancelable(true)
        .setPositiveButton("OK") { _, _ ->
            continuation.resume(input.text.toString().toCharArray())
        }
        .setNegativeButton("Cancel") { _, _ ->
            continuation.resume(null)
        }
        .setOnCancelListener {
            continuation.resume(null)
        }
        .create()
    dialog.show()
}

/**
 * Shows an information dialog explaining Android storage and deletion restrictions.
 * Call this from your activity, for example on FAQ or Info menu selection.
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
