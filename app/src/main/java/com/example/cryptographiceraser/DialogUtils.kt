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
