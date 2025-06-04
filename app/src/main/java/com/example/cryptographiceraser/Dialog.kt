package com.example.cryptographiceraser

import android.app.Dialog
import android.os.Bundle
import android.text.InputType
import android.view.*
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment

class Dialog : DialogFragment() {

    // Dialog-Typ
    enum class Type { STATUS, PASSWORD }

    var dialogType: Type = Type.STATUS
    var statusMessage: String = ""
    var progress: Int = 0
    var passwordCallback: ((CharArray?) -> Unit)? = null

    // UI-Elemente
    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var passwordInput: EditText

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return when (dialogType) {
            Type.STATUS -> {
                val view = requireActivity().layoutInflater.inflate(R.layout.dialog_status, null)
                statusText = view.findViewById(R.id.statusText)
                progressBar = view.findViewById(R.id.progressBar)
                statusText.text = statusMessage
                progressBar.progress = progress
                AlertDialog.Builder(requireContext())
                    .setView(view)
                    .setCancelable(false)
                    .create()
            }
            Type.PASSWORD -> {
                passwordInput = EditText(requireContext()).apply {
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                    hint = "Passwort"
                }
                AlertDialog.Builder(requireContext())
                    .setTitle("Passwort eingeben")
                    .setView(passwordInput)
                    .setPositiveButton("OK") { _, _ ->
                        passwordCallback?.invoke(passwordInput.text.toString().toCharArray())
                        dismiss()
                    }
                    .setNegativeButton("Abbrechen") { _, _ ->
                        passwordCallback?.invoke(null)
                        dismiss()
                    }
                    .setOnCancelListener { passwordCallback?.invoke(null) }
                    .create()
            }
        }
    }

    // Status aktualisieren
    fun updateStatus(message: String, progress: Int) {
        if (this::statusText.isInitialized && this::progressBar.isInitialized) {
            statusText.text = message
            progressBar.progress = progress
        }
    }
}
