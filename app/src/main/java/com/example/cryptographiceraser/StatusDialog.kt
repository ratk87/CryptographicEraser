package com.example.cryptographiceraser

import android.app.Dialog
import android.os.Bundle
import android.view.*
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.DialogFragment

/**
 * Modal dialog to show status and progress, and block all user input until dismissed.
 */
class StatusDialog : DialogFragment() {

    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar

    // Buffer for Status-Updates in case that the View has not been initialized yet
    private var pendingStatusText: String? = null
    private var pendingProgress: Int? = null


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.dialog_status, container, false)
        statusText = view.findViewById(R.id.statusText)
        progressBar = view.findViewById(R.id.progressBar)
        progressBar.max = 100
        progressBar.progress = 0

        // Übernehme eventuell vorher gesetzte Werte
        pendingStatusText?.let { statusText.text = it }
        pendingProgress?.let { progressBar.progress = it }
        return view
    }

    fun updateStatus(text: String, progress: Int) {
        if (this::statusText.isInitialized && this::progressBar.isInitialized) {
            statusText.text = text
            progressBar.progress = progress
        } else {
            // Buffer until views are ready
            pendingStatusText = text
            pendingProgress = progress
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)
        return dialog
    }

    override fun onStart() {
        super.onStart()
        // Make dialog full width
        dialog?.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )
    }
}
