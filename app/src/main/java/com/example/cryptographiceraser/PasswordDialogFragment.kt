// PasswordDialogFragment.kt
// Für neuere Android Versionen, sonst geht das Passwort Fenster nach einmaliger Anwendungn icht mehr
package com.example.cryptographiceraser

import android.app.Dialog
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment

class PasswordDialogFragment : DialogFragment() {

    interface PasswordCallback {
        fun onPasswordEntered(password: CharArray)
        fun onPasswordCancelled()
    }

    var callback: PasswordCallback? = null
    var title: String = "Enter password for shredding"

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "Password"
        }

        return AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                callback?.onPasswordEntered(input.text.toString().toCharArray())
                dismiss()
            }
            .setNegativeButton("Cancel") { _, _ ->
                callback?.onPasswordCancelled()
                dismiss()
            }
            .setOnCancelListener {
                callback?.onPasswordCancelled()
            }
            .create()
    }
}
