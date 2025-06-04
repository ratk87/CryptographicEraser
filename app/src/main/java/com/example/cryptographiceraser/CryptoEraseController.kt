package com.example.cryptographiceraser

import android.content.Context
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.io.File

/**
 * Vermittelt zwischen View (Activity/Dialog/FileExplorer) und Model (CryptoUtils, WipeUtils, DialogUtils)
 */
class CryptoEraseController(
    private val context: Context,
    private val fragmentManager: FragmentManager,
    private val lifecycleScope: LifecycleCoroutineScope
) {
    private var statusDialog: Dialog? = null

    // Startet den CryptoShred-Workflow für eine oder mehrere Dateien
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
                        CryptoUtils.encryptFileInPlace(context, file, password)
                    }
                    if (ok) success++ else failed++
                    showStatus("Bearbeite ${file.name} (${i+1}/${files.size})...", 20 + (70*(i+1)/files.size))
                }
                showStatus("Lösche Dateien...", 85)
                withContext(Dispatchers.IO) { files.forEach { it.delete() } }
                showStatus("Bereinige freien Speicher...", 92)
                withContext(Dispatchers.IO) {
                    val dir = context.getExternalFilesDir(null) ?: context.filesDir
                    WipeUtils.doubleWipeFreeSpace(context, dir)
                }
                showStatus("Fertig!", 100)
                hideDialog()
                onFinish(success, failed)
            }
        }
    }

    // Statusdialog anzeigen oder aktualisieren
    private fun showStatus(msg: String, progress: Int) {
        if (statusDialog == null) {
            statusDialog = Dialog().apply {
                dialogType = Dialog.Type.STATUS
                statusMessage = msg
                this.progress = progress
            }
            statusDialog!!.show(fragmentManager, "StatusDialog")
        } else {
            statusDialog!!.updateStatus(msg, progress)
        }
    }

    private fun hideDialog() {
        statusDialog?.dismissAllowingStateLoss()
        statusDialog = null
    }

    // Passwortdialog anzeigen
    private fun showPasswordDialog(callback: (CharArray?) -> Unit) {
        val passDialog = Dialog().apply {
            dialogType = Dialog.Type.PASSWORD
            passwordCallback = callback
        }
        passDialog.show(fragmentManager, "PasswordDialog")
    }
}
