package com.example.cryptographiceraser

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import java.io.File

/**
 * Haupt-Activity der App; zeigt Speicherstatistiken,
 * startet den Datei-Explorer zum „Shredden“ ausgewählter Dateien
 * und initiiert das Überschreiben des freien Speicherplatzes.
 */
class MainActivity : AppCompatActivity(), FileExplorer.OnFileSelectedListener {

    companion object {
        /** Request-Code für Speicher-Berechtigungen (READ & WRITE) */
        private const val STORAGE_PERMISSION_REQUEST_CODE = 1001
    }

    /** Controller für Verschlüsselungs- und Lösch-Workflows */
    private lateinit var controller: CryptoEraseController

    /** Dialog für Fortschrittsanzeigen (Encrypt/Delete/Free-Space) */
    private var statusDialog: Dialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1) Permissions: Stelle sicher, dass App Lese-/Schreibzugriff hat
        ensureStoragePermissions()

        // 2) Controller instanziieren mit Kontext, FragmentManager und Coroutine-Scope
        controller = CryptoEraseController(
            context = this,
            fragmentManager = supportFragmentManager,
            lifecycleScope = lifecycleScope
        )

        // 3) UI-Elemente aus Layout referenzieren
        val textInternal = findViewById<TextView>(R.id.textInternalStorage)
        val btnShred     = findViewById<Button>(R.id.btnShredFile)
        val btnWipe      = findViewById<Button>(R.id.btnWipe)

        // 4) Aktuelle Speicherstatistiken ermitteln und anzeigen
        val (total, free) = WipeUtils.getStorageStats(filesDir)
        textInternal.text = "Internal (Total / Free): ${formatGB(total)} / ${formatGB(free)}"

        // 5) Button „Shred File“: öffnet FileExplorer, wenn Berechtigung vorhanden
        btnShred.setOnClickListener {
            if (hasStoragePermission()) {
                openFileExplorer()
            } else {
                ensureStoragePermissions()
                showToast("Bitte Speicherzugriff erlauben.")
            }
        }

        // 6) Button „Wipe Free Space“: startet Freispeicher-Wipe
        btnWipe.setOnClickListener {
            if (hasStoragePermission()) {
                startWipeFreeSpace()
            } else {
                ensureStoragePermissions()
                showToast("Bitte Speicherzugriff erlauben.")
            }
        }
    }

    // -------------------------
    // Berechtigungs-Logik
    // -------------------------

    /** Prüft, ob die nötigen Speicher-Berechtigungen erteilt wurden */
    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+: „Manage all files“
            Environment.isExternalStorageManager()
        } else {
            // Android 6–10: READ & WRITE nötig
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)  == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    /** Fordert bei Bedarf die Laufzeit-Berechtigungen an */
    private fun ensureStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                // Menü öffnen, damit Nutzer „All Files Access“ erteilen kann
                startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        .apply { data = Uri.parse("package:$packageName") }
                )
            }
        } else {
            // Für ältere Android-Versionen: READ + WRITE gemeinsam anfragen
            val perms = arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            val missing = perms.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            if (missing.isNotEmpty()) {
                ActivityCompat.requestPermissions(
                    this,
                    missing.toTypedArray(),
                    STORAGE_PERMISSION_REQUEST_CODE
                )
            }
        }
    }

    /** Callback für das Ergebnis der Berechtigungsanfrage */
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_REQUEST_CODE) {
            val granted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (granted) {
                showToast("Speicherzugriff erteilt.")
            } else {
                showToast("Ohne Speicherzugriff kann die App nicht arbeiten.")
            }
        }
    }

    // -------------------------
    // File Explorer-Integration
    // -------------------------

    /** Startet den FileExplorer-Fragment, um Dateien auszuwählen */
    private fun openFileExplorer() {
        supportFragmentManager
            .popBackStack("FileExplorer", FragmentManager.POP_BACK_STACK_INCLUSIVE)

        FileExplorer().apply {
            setOnFileSelectedListener(this@MainActivity)
        }.also {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, it, "FileExplorer")
                .addToBackStack("FileExplorer")
                .commit()
        }
    }

    /** Wird aufgerufen, wenn der Nutzer Dateien im Explorer ausgewählt hat */
    override fun onFilesSelected(selectedFiles: List<File>) {
        if (selectedFiles.isEmpty()) {
            showToast("Keine Datei gewählt.")
            return
        }
        // Shred-Workflow: verschlüsseln + löschen → liefert (Erfolg, Fehler)
        controller.shredFiles(selectedFiles) { success, failed ->
            showToast("$success Dateien gelöscht, $failed Fehler.")
            // Frage: Freispeicher bereinigen?
            AlertDialog.Builder(this)
                .setTitle("Freien Speicher bereinigen?")
                .setMessage("Möchten Sie jetzt den freien Speicher mit Zufallsdaten überschreiben?")
                .setPositiveButton("Ja") { _, _ -> startWipeFreeSpace() }
                .setNegativeButton("Nein", null)
                .show()
            // Explorer aktualisieren, um gelöschte Dateien zu entfernen
            (supportFragmentManager.findFragmentByTag("FileExplorer") as? FileExplorer)
                ?.refreshCurrentDir()
        }
    }

    // -------------------------
    // Freien Speicher bereinigen
    // -------------------------

    /** Startet den Freispeicher-Wipe mit Callback für UI-Updates */
    private fun startWipeFreeSpace() {
        controller.startWipeFreeSpace(
            dir = filesDir,
            onProgress = { status, prog -> showStatusDialog(status, prog) },
            onDone     = {
                hideStatusDialog()
                showToast("Speicherbereinigung abgeschlossen!")
            }
        )
    }

    // -------------------------
    // Optionsmenü (FAQ, Exit)
    // -------------------------

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_faq -> {
            // Zeigt eine FAQ-Box mit Erklärungen zur App
            AlertDialog.Builder(this)
                .setTitle("FAQ – Cryptographic Eraser")
                .setMessage(
                    "1. Wie funktioniert das?\n" +
                            "- In-place verschlüsseln der Datei, gefolgt von Löschen der Datei und einer Speicherbereinigung.\n\n" +
                            "2. Algorithmus?\n" +
                            "- AES-GCM bzw AES-CTR mit PBKDF2.\n\n" +
                            "3. Rechte?\n" +
                            "- Voller Dateizugriff wird benötigt.\n\n" +
                            "4. Sicher?\n" +
                            "- Aufgrund der verwendeten Speichertechnologie - NAND-Flashspeicher - verbleibt ein Restrisiko.\n\n" +
                            "5. Vertrauenswürdig?\n" +
                            "- Demonstrator im Rahmen einer Bachelorarbeit, Code auf GitHub."
                )
                .setPositiveButton("OK", null)
                .show()
            true
        }
        R.id.action_exit -> {
            // App vollständig beenden
            finishAffinity()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    // -------------------------
    // Fortschritts-Dialog & Toast
    // -------------------------

    /** Zeigt oder aktualisiert einen Status-Dialog mit Text und Prozent */
    private fun showStatusDialog(status: String, progress: Int) {
        if (statusDialog == null) {
            statusDialog = Dialog().apply {
                dialogType    = Dialog.Type.STATUS
                statusMessage = status
                this.progress = progress
            }
            statusDialog?.show(supportFragmentManager, "StatusDialog")
        } else {
            statusDialog?.updateStatus(status, progress)
        }
    }

    /** Blendet den Fortschritts-Dialog aus */
    private fun hideStatusDialog() {
        statusDialog?.dismissAllowingStateLoss()
        statusDialog = null
    }

    /** Einfache Helper-Funktion für lange Toast-Nachrichten */
    private fun showToast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    // -------------------------
    // Hilfsfunktionen
    // -------------------------

    /**
     * Formatiert Byte-Werte in GB mit zwei Nachkommastellen
     * @param bytes Anzahl Bytes
     * @return Formatierte String-Darstellung, z.B. "1.23 GB"
     */
    private fun formatGB(bytes: Long): String {
        val gb = bytes / 1_073_741_824.0
        return String.format("%.2f GB", gb)
    }
}
