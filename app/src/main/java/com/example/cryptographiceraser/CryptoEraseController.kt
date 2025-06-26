package com.example.cryptographiceraser

import android.content.Context
import android.util.Log
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.io.File

/**
 * Controller: Vermittelt zwischen View (Activity/Dialog/FileExplorer)
 * und Model (CryptoUtils, WipeUtils, DialogUtils).
 */
class CryptoEraseController(
    private val context: Context,
    private val fragmentManager: FragmentManager,
    private val lifecycleScope: LifecycleCoroutineScope
) {
    private var statusDialog: Dialog? = null

    /**
     * Startet den CryptoShred-Workflow für eine oder mehrere Dateien:
     * 1) Passwort holen
     * 2) Dateien verschlüsseln
     * 3) Dateien löschen
     * Danach Callback onFinish(successCount, failCount)
     */
    fun shredFiles(files: List<File>, onFinish: (Int, Int) -> Unit) {
        showStatus("Warte auf Passwort...", 0)
        showPasswordDialog { password ->
            if (password == null || password.isEmpty()) {
                hideDialog()
                DialogUtils.showToast(context, "Kein Passwort eingegeben, abgebrochen.")
                return@showPasswordDialog
            }
            lifecycleScope.launch(Dispatchers.Main) {
                showStatus("Verschlüssele Datei(en)...", 15)
                var success = 0
                var failed = 0
                for ((i, file) in files.withIndex()) {
                    val ok = withContext(Dispatchers.IO) {
                        CryptoUtils.encryptFileInPlace(file, password)
                    }
                    if (ok) success++ else failed++
                    showStatus(
                        "Bearbeite ${file.name} (${i + 1}/${files.size})...",
                        20 + (70 * (i + 1) / files.size)
                    )
                }
                showStatus("Lösche Dateien...", 85)
                withContext(Dispatchers.IO) {
                    for ((i, file) in files.withIndex()) {
                        val deleted = file.delete()
                        if (deleted) {
                            success++
                        } else {
                            failed++
                            Log.w(CryptoUtils.TAG, "Konnte Datei nicht löschen: ${file.name}")
                        }
                        // Optional: Status-Update pro Datei
                        val progress = 85 + (10 * (i + 1) / files.size)
                        showStatus("Lösche ${file.name} (${i + 1}/${files.size})...", progress)
                    }
                }

                showStatus("Fertig!", 100)
                hideDialog()
                onFinish(success, failed)
            }
        }
    }

    private fun showStatus(msg: String, progress: Int) {
        if (statusDialog == null) {
            statusDialog = Dialog().apply {
                dialogType    = Dialog.Type.STATUS
                statusMessage = msg
                this.progress = progress
            }
            statusDialog?.show(fragmentManager, "StatusDialog")
        } else {
            statusDialog?.updateStatus(msg, progress)
        }
    }

    private fun hideDialog() {
        statusDialog?.dismissAllowingStateLoss()
        statusDialog = null
    }

    private fun showPasswordDialog(callback: (CharArray?) -> Unit) {
        val passDialog = Dialog().apply {
            dialogType       = Dialog.Type.PASSWORD
            passwordCallback = callback
        }
        passDialog.show(fragmentManager, "PasswordDialog")
    }

    /**
     * Nachträgliche Speicherbereinigung auf Knopfdruck.
     * onProgress liefert (Status-Text, Prozent).
     */
    fun startWipeFreeSpace(
        dir: File,
        onProgress: (String, Int) -> Unit,
        onDone: () -> Unit
    ) {
        onProgress("Bereinige freien Speicher...", 0)
        WipeUtils.wipeFreeSpaceWithFeedback(context, dir) { percent ->
            onProgress("Bereinige freien Speicher... $percent%", percent)
            if (percent >= 100) onDone()
        }
    }
}
