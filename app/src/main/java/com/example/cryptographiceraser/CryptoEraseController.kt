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
 * Controller-Klasse für den CryptoShred-Workflow.
 * Vermittelt zwischen View (Activity, Dialog, FileExplorer)
 * und Model (CryptoUtils, WipeUtils, DialogUtils).
 * Stellt sicher, dass alle UI-Updates auf dem Main-Thread stattfinden,
 * und verschiebt Dateioperationen in den IO-Thread.
 */
class CryptoEraseController(
    private val context: Context,
    private val fragmentManager: FragmentManager,
    private val lifecycleScope: LifecycleCoroutineScope
) {
    /** Dialog für Statusanzeigen: Fortschrittsbalken + Text */
    private var statusDialog: Dialog? = null

    /**
     * Startet den CryptoShred-Workflow für eine oder mehrere Dateien:
     * 1) Passwort-Dialog anzeigen und auf Eingabe warten
     * 2) Dateien verschlüsseln (CryptoUtils.encryptFileInPlace) im IO-Thread
     * 3) Dateien löschen (File.delete) im IO-Thread
     * 4) UI-Updates (showStatus) immer im Main-Thread
     * 5) Am Ende onFinish(successCount, failCount) aufrufen
     *
     * @param files     Liste der zu shreddernden Dateien
     * @param onFinish  Callback, dem Anzahl erfolgreicher bzw. fehlgeschlagener Löschvorgänge übergeben wird
     */
    fun shredFiles(files: List<File>, onFinish: (Int, Int) -> Unit) {
        // Initiale Statusmeldung: Warten auf Passwort
        showStatus("Warte auf Passwort...", 0)

        // Passwort-Dialog öffnen
        showPasswordDialog { password ->
            // (1) Korrekte Null- und Leerprüfung für CharArray?
            if (password == null || password.isEmpty()) {
                hideDialog()
                DialogUtils.showToast(context, "Kein Passwort eingegeben, abgebrochen.")
                return@showPasswordDialog
            }
            // Passwort nicht-null und nicht-leer → sichere lokale Referenz
            val pwd: CharArray = password

            // Coroutine im Main-Thread starten für UI-Updates
            lifecycleScope.launch(Dispatchers.Main) {
                // 2) Verschlüsselungsphase starten
                showStatus("Verschlüssele Datei(en)...", 15)
                var encSuccess = 0
                var encFailed  = 0

                // Schleife über alle Dateien
                for ((i, file) in files.withIndex()) {
                    // Verschlüsselung im IO-Dispatcher
                    val ok = withContext(Dispatchers.IO) {
                        CryptoUtils.encryptFileInPlace(file, pwd)
                    }
                    if (ok) encSuccess++ else encFailed++

                    // Fortschritts-Berechnung (zwischen 15% und 65%)
                    val progEnc = 15 + (50 * (i + 1) / files.size)
                    showStatus("Verschlüssele ${file.name} (${i + 1}/${files.size})...", progEnc)
                }

                // 3) Löschphase starten
                showStatus("Lösche Dateien...", 70)
                var delSuccess = 0
                var delFailed  = 0

                // Schleife über alle Dateien zum Löschen
                for ((i, file) in files.withIndex()) {
                    // Löschen im IO-Dispatcher
                    val deleted = withContext(Dispatchers.IO) {
                        file.delete()
                    }
                    if (deleted) {
                        delSuccess++
                    } else {
                        delFailed++
                        Log.w(CryptoUtils.TAG, "Konnte Datei nicht löschen: ${file.name}")
                    }
                    // Fortschritts-Berechnung (zwischen 70% und 95%)
                    val progDel = 70 + (25 * (i + 1) / files.size)
                    showStatus("Lösche ${file.name} (${i + 1}/${files.size})...", progDel)
                }

                // 4) Workflow abschließen
                showStatus("Fertig!", 100)
                hideDialog()

                // Callback mit den Lösch-Ergebnissen aufrufen
                onFinish(delSuccess, delFailed)
            }
        }
    }

    /**
     * Zeigt oder aktualisiert einen Status-Dialog mit Text und Prozent.
     *
     * @param msg      Status-Text, der angezeigt wird
     * @param progress Fortschrittswert (0–100)
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
     * Öffnet einen Passwort-Dialog. Das eingegebene Passwort (oder null
     * bei Abbruch) wird über den Callback zurückgeliefert.
     *
     * @param callback Funktion, die das CharArray? Passwort erhält
     */
    private fun showPasswordDialog(callback: (CharArray?) -> Unit) {
        val passDialog = Dialog().apply {
            dialogType       = Dialog.Type.PASSWORD
            passwordCallback = callback
        }
        passDialog.show(fragmentManager, "PasswordDialog")
    }

    /**
     * Führt eine nachträgliche Freispeicher-Bereinigung durch.
     * Überschreibt freien Speicherplatz mit Zufallsdaten.
     *
     * @param dir        Verzeichnis, in dem freier Speicher überschrieben wird
     * @param onProgress Callback für Status-Text und Prozent (Main-Thread)
     * @param onDone     Callback, wenn 100% erreicht sind
     */
    fun startWipeFreeSpace(
        dir: File,
        onProgress: (String, Int) -> Unit,
        onDone: () -> Unit
    ) {
        // Initiale Statusmeldung
        onProgress("Bereinige freien Speicher...", 0)
        // WipeUtils führt im Hintergrund aus und liefert Prozent
        WipeUtils.wipeFreeSpaceWithFeedback(context, dir) { percent ->
            // UI-Update (läuft bereits auf Main-Thread)
            onProgress("Bereinige freien Speicher... $percent%", percent)
            if (percent >= 100) {
                onDone()
            }
        }
    }
}
