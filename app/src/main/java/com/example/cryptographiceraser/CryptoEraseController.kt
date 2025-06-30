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
 * Controller-Klasse: Vermittelt zwischen der View-Ebene
 * (Activity, Dialog, FileExplorer) und der Model-Ebene
 * (CryptoUtils, WipeUtils, DialogUtils).
 */
class CryptoEraseController(
    private val context: Context,
    private val fragmentManager: FragmentManager,
    private val lifecycleScope: LifecycleCoroutineScope
) {
    /** Dialog zur Anzeige von Statusmeldungen und Fortschritt */
    private var statusDialog: Dialog? = null

    /**
     * Führt den CryptoShred-Workflow für eine oder mehrere Dateien aus:
     * 1) Zeigt einen Passwort-Dialog und wartet auf Eingabe.
     * 2) Verschlüsselt jede Datei „in place“ (CryptoUtils).
     * 3) Löscht jede verschlüsselte Datei.
     * Am Ende wird der Callback onFinish mit den Zählerwerten
     * (Anzahl erfolgreicher Löschvorgänge, Anzahl Fehler) aufgerufen.
     *
     * @param files Liste der zu bearbeitenden Dateien
     * @param onFinish Callback mit (successCount, failCount)
     */
    fun shredFiles(files: List<File>, onFinish: (Int, Int) -> Unit) {
        // Statusdialog initialisieren
        showStatus("Warte auf Passwort...", 0)

        // Passwort abfragen
        showPasswordDialog { password ->
            // Abbruch, wenn kein Passwort eingegeben wurde
            if (password == null || password.isEmpty()) {
                hideDialog()
                DialogUtils.showToast(context, "Kein Passwort eingegeben, abgebrochen.")
                return@showPasswordDialog
            }

            // Starte Verschlüsselung und Lösch-Workflow im Main-Thread
            lifecycleScope.launch(Dispatchers.Main) {
                // 2) Verschlüsselung: Status auf 15 %
                showStatus("Verschlüssele Datei(en)...", 15)
                var success = 0
                var failed = 0

                // Schleife über alle Dateien zur Verschlüsselung
                for ((i, file) in files.withIndex()) {
                    // Verschlüsselung im IO-Thread
                    val ok = withContext(Dispatchers.IO) {
                        CryptoUtils.encryptFileInPlace(file, password)
                    }
                    // Zähler erhöhen
                    if (ok) success++ else failed++

                    // Statusaktualisierung inkl. Dateiname und Fortschritt
                    showStatus(
                        "Bearbeite ${file.name} (${i + 1}/${files.size})...",
                        20 + (70 * (i + 1) / files.size)
                    )
                }

                // 3) Löschphase: Status auf 85 %
                showStatus("Lösche Dateien...", 85)

                // Dateien im IO-Thread löschen
                withContext(Dispatchers.IO) {
                    for ((i, file) in files.withIndex()) {
                        val deleted = file.delete()
                        if (deleted) {
                            success++
                        } else {
                            failed++
                            Log.w(CryptoUtils.TAG, "Konnte Datei nicht löschen: ${file.name}")
                        }
                        // Fortschritt für jede Datei updaten
                        val progress = 85 + (10 * (i + 1) / files.size)
                        showStatus("Lösche ${file.name} (${i + 1}/${files.size})...", progress)
                    }
                }

                // Fertig: Status auf 100 %
                showStatus("Fertig!", 100)
                hideDialog()

                // Callback mit Lösch-Ergebnissen
                onFinish(success, failed)
            }
        }
    }

    /**
     * Zeigt oder aktualisiert einen nicht modalen Dialog,
     * der den aktuellen Status-Text und Fortschritt in Prozent anzeigt.
     *
     * @param msg      Statusnachricht
     * @param progress Fortschritt als Prozentzahl (0–100)
     */
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

    /**
     * Verbirgt den Status-Dialog und setzt die Referenz zurück.
     */
    private fun hideDialog() {
        statusDialog?.dismissAllowingStateLoss()
        statusDialog = null
    }

    /**
     * Öffnet einen Dialog zur Passworteingabe.
     * Das eingegebene CharArray (oder null bei Abbruch) wird
     * an den Callback zurückgegeben.
     *
     * @param callback Funktion, die das eingegebene Passwort erhält
     */
    private fun showPasswordDialog(callback: (CharArray?) -> Unit) {
        val passDialog = Dialog().apply {
            dialogType       = Dialog.Type.PASSWORD
            passwordCallback = callback
        }
        passDialog.show(fragmentManager, "PasswordDialog")
    }

    /**
     * Führt auf Knopfdruck eine nachträgliche Speicherbereinigung
     * durch, indem der freie Speicherplatz mit Zufallsdaten überschrieben wird.
     *
     * @param dir        Verzeichnis, in dem freie Blöcke bereinigt werden
     * @param onProgress Callback mit (Status-Text, Prozent) für UI-Updates
     * @param onDone     Callback, wenn 100 % erreicht sind
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
