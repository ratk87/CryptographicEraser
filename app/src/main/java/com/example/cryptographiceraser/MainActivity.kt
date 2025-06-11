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
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import java.io.File

class MainActivity : AppCompatActivity(), FileExplorer.OnFileSelectedListener {

    companion object {
        private const val STORAGE_PERMISSION_REQUEST_CODE = 1001
    }

    private lateinit var controller: CryptoEraseController
    private var statusDialog: Dialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1) Laufzeit‐Berechtigungen sicherstellen
        ensureStoragePermissions()

        // 2) Controller initialisieren
        controller = CryptoEraseController(
            context = this,
            fragmentManager = supportFragmentManager,
            lifecycleScope = lifecycleScope
        )

        // 3) UI‐Elemente referenzieren
        val textInternal = findViewById<TextView>(R.id.textInternalStorage)
        val btnShred     = findViewById<Button>(R.id.btnShredFile)
        val btnWipe      = findViewById<Button>(R.id.btnWipe)

        // 4) Speicher‐Statistiken anzeigen
        val (total, free) = WipeUtils.getStorageStats(filesDir)
        textInternal.text = "Internal (Total / Free): ${formatGB(total)} / ${formatGB(free)}"

        // 5) „Shred File“ → eigenem FileExplorer starten
        btnShred.setOnClickListener {
            if (hasStoragePermission()) {
                openFileExplorer()
            } else {
                ensureStoragePermissions()
                showToast("Bitte Speicherzugriff erlauben.")
            }
        }

        // 6) „Wipe Free Space“ → direkt Freispeicher‐Wipe
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
    //  Permissions
    // -------------------------

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            // beide Permissions müssen da sein
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)  == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }


    private fun ensureStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+
            if (!Environment.isExternalStorageManager()) {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        .apply { data = Uri.parse("package:$packageName") }
                )
            }
        } else {
            // Android 6–10: sowohl READ als auch WRITE
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
    //  FileExplorer
    // -------------------------

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

    override fun onFilesSelected(selectedFiles: List<File>) {
        if (selectedFiles.isEmpty()) {
            showToast("Keine Datei gewählt.")
            return
        }
        controller.shredFiles(selectedFiles) { success, failed ->
            showToast("$success Dateien gelöscht, $failed Fehler.")
            // Nachfrage: Freien Speicher bereinigen?
            AlertDialog.Builder(this)
                .setTitle("Freien Speicher bereinigen?")
                .setMessage("Möchten Sie jetzt den freien Speicher mit Zufallsdaten überschreiben?")
                .setPositiveButton("Ja") { _, _ -> startWipeFreeSpace() }
                .setNegativeButton("Nein", null)
                .show()
            // Explorer aktualisieren
            (supportFragmentManager.findFragmentByTag("FileExplorer") as? FileExplorer)
                ?.refreshCurrentDir()
        }
    }

    // -------------------------
    //  Freien Speicher bereinigen
    // -------------------------

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
    //  Options‐Menu
    // -------------------------

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_faq -> {
            AlertDialog.Builder(this)
                .setTitle("FAQ – Cryptographic Eraser")
                .setMessage(
                    "1. Wie funktioniert das?\n" +
                            "- Verschlüsseln & Löschen.\n\n" +
                            "2. Algorithmus?\n" +
                            "- AES-GCM 256 + PBKDF2.\n\n" +
                            "3. Rechte?\n" +
                            "- Voller Dateizugriff.\n\n" +
                            "4. Sicher?\n" +
                            "- Sehr wahrscheinlich, NAND-Flash kann Spuren behalten.\n\n" +
                            "5. Vertrauenswürdig?\n" +
                            "- Bachelor-Arbeit, Code auf GitHub."
                )
                .setPositiveButton("OK", null)
                .show()
            true
        }
        R.id.action_exit -> {
            finishAffinity()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    // -------------------------
    //  Fortschritts‐Dialog & Toast
    // -------------------------

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

    private fun hideStatusDialog() {
        statusDialog?.dismissAllowingStateLoss()
        statusDialog = null
    }

    private fun showToast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    // -------------------------
    //  Hilfsfunktion
    // -------------------------

    private fun formatGB(bytes: Long): String {
        val gb = bytes / 1_073_741_824.0
        return String.format("%.2f GB", gb)
    }
}
