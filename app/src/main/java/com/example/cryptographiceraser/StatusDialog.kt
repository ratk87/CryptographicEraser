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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.dialog_status, container, false)
        statusText = view.findViewById(R.id.statusText)
        progressBar = view.findViewById(R.id.progressBar)
        progressBar.max = 100
        progressBar.progress = 0
        return view
    }

    fun updateStatus(text: String, progress: Int) {
        statusText.text = text
        progressBar.progress = progress
    }
}
