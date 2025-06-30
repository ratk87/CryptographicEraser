package com.example.cryptographiceraser

import android.app.Dialog
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment

/**
 * Universeller Dialog-Fragment zur Anzeige von Status- oder
 * Passwort-Eingabe-Dialogen.
 */
class Dialog : DialogFragment() {

    /** Differenziert zwischen Fortschritts-Dialog und Passwort-Dialog */
    enum class Type { STATUS, PASSWORD }

    /** Aktueller Typ dieses Dialogs (Status oder Passwort) */
    var dialogType: Type = Type.STATUS

    /** Nachricht, die im Status-Dialog angezeigt wird */
    var statusMessage: String = ""

    /** Fortschrittswert (0–100) für den Status-Dialog */
    var progress: Int = 0

    /** Callback-Funktion, die bei Passwort-/Abbruch-Dialog aufgerufen wird */
    var passwordCallback: ((CharArray?) -> Unit)? = null

    // UI-Elemente für Status-Dialog (werden in onCreateDialog initialisiert)
    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar

    // UI-Element für Passwort-Dialog
    private lateinit var passwordInput: EditText

    /**
     * Erstellt das passende Dialog-Objekt basierend auf [dialogType].
     * - STATUS: Zeigt Fortschrittstext und eine ProgressBar (nicht abkoppelbar).
     * - PASSWORD: Zeigt ein EditText-Feld zur Passworteingabe mit OK/Abbrechen.
     */
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return when (dialogType) {
            Type.STATUS -> {
                // Layout für Status-Dialog einbinden
                val view = requireActivity()
                    .layoutInflater
                    .inflate(R.layout.dialog_status, null)

                // UI-Elemente aus Layout referenzieren
                statusText = view.findViewById(R.id.statusText)
                progressBar = view.findViewById(R.id.progressBar)

                // Anfangswerte setzen
                statusText.text = statusMessage
                progressBar.progress = progress

                // Builder-Konfiguration: nicht abbrechbar
                AlertDialog.Builder(requireContext())
                    .setView(view)
                    .setCancelable(false)
                    .create()
            }
            Type.PASSWORD -> {
                // Dynamisch ein EditText für das Passwort erzeugen
                passwordInput = EditText(requireContext()).apply {
                    inputType = InputType.TYPE_CLASS_TEXT or
                            InputType.TYPE_TEXT_VARIATION_PASSWORD
                    hint = "Passwort"
                }
                // Builder mit OK- und Abbrechen-Buttons
                AlertDialog.Builder(requireContext())
                    .setTitle("Passwort eingeben")
                    .setView(passwordInput)
                    .setPositiveButton("OK") { _, _ ->
                        // Callback mit eingegebenem Passwort
                        passwordCallback?.invoke(passwordInput.text
                            .toString()
                            .toCharArray())
                        dismiss()
                    }
                    .setNegativeButton("Abbrechen") { _, _ ->
                        // Callback mit null bei Abbruch
                        passwordCallback?.invoke(null)
                        dismiss()
                    }
                    .setOnCancelListener {
                        // Callback auch bei äußerem Abbruch
                        passwordCallback?.invoke(null)
                    }
                    .create()
            }
        }
    }

    /**
     * Aktualisiert die im STATUS-Dialog sichtbaren UI-Elemente,
     * falls bereits initialisiert.
     *
     * @param message Neuer Statustext
     * @param progress Neuer Fortschrittswert (0–100)
     */
    fun updateStatus(message: String, progress: Int) {
        // Prüfen, ob die Views initialisiert wurden
        if (this::statusText.isInitialized && this::progressBar.isInitialized) {
            statusText.text = message
            progressBar.progress = progress
        }
    }
}
