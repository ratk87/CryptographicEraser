package com.example.cryptographiceraser

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import java.io.File
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AlertDialog

class MainActivity : AppCompatActivity(), FileExplorer.OnFileSelectedListener {

    private lateinit var controller: CryptoEraseController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Controller
        controller = CryptoEraseController(
            context = this,
            fragmentManager = supportFragmentManager,
            lifecycleScope = lifecycleScope
        )

        // UI-Elements
        val textInternalStorage = findViewById<TextView>(R.id.textInternalStorage)
        val externalStorageRow = findViewById<LinearLayout>(R.id.externalStorageRow)
        val textExternalStorage = findViewById<TextView>(R.id.textExternalStorage)

        // Show Storage Info
        val (intTotal, intFree) = WipeUtils.getStorageStats(filesDir)
        textInternalStorage.text = "Internal Storage (Total / Free): ${formatGB(intTotal)} / ${formatGB(intFree)}"
        val extDir = getExternalFilesDir(null)
        if (extDir != null && android.os.Environment.getExternalStorageState(extDir) == android.os.Environment.MEDIA_MOUNTED) {
            val (extTotal, extFree) = WipeUtils.getStorageStats(extDir)
            externalStorageRow.visibility = LinearLayout.VISIBLE
            textExternalStorage.text = "SD Card (Total / Free): ${formatGB(extTotal)} / ${formatGB(extFree)}"
        } else {
            externalStorageRow.visibility = LinearLayout.GONE
        }

        // Button: Shred File
        findViewById<Button>(R.id.btnShredFile).setOnClickListener {
            openFileExplorer(singleSelection = true)
        }

        // Button: Wipe Free Space
        findViewById<Button>(R.id.btnWipe).setOnClickListener {
            controller.startWipeFreeSpace(
                dir = filesDir,
                onProgress = { status, progress -> showStatusDialog(status, progress) },
                onDone = { hideStatusDialog(); showToast("Wipe abgeschlossen!") }
            )
        }

    }

    // Open the File Explorer
    private fun openFileExplorer(singleSelection: Boolean) {
        supportFragmentManager.popBackStack("FileExplorer", FragmentManager.POP_BACK_STACK_INCLUSIVE)
        val fragment = FileExplorer()
        fragment.setOnFileSelectedListener(this)
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment, "FileExplorer")
            .addToBackStack("FileExplorer")
            .commit()
    }

    // Callback from FileExplorer – initiates Controller Workflow
    override fun onFilesSelected(selectedFiles: List<File>) {
        if (selectedFiles.isNotEmpty()) {
            controller.shredFiles(selectedFiles) { ok, fail ->
                Toast.makeText(this, "$ok Files deleted, $fail Error.", Toast.LENGTH_LONG).show()
                // After Deletion, FileExplorer gets refreshed
                val fragment = supportFragmentManager.findFragmentByTag("FileExplorer")
                if (fragment is FileExplorer) {
                    fragment.refreshCurrentDir()
                }
            }
        } else {
            Toast.makeText(this, "No files selected.", Toast.LENGTH_SHORT).show()
        }
    }

    // Options-Menu
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_faq -> {
                // FAQ anzeigen
                AlertDialog.Builder(this)
                    .setTitle("FAQ – Cryptographic Eraser")
                    .setMessage(
                        "1. Wie funktioniert kryptografisches Löschen?\n" +
                                "- Die App verschlüsselt und löscht Dateien sicher.\n\n" +
                                "2. Welche Algorithmen werden genutzt?\n" +
                                "- AES-GCM 256 mit PBKDF2-Schlüsselableitung.\n\n" +
                                "3. Brauche ich spezielle Rechte?\n" +
                                "- Ja: Voller Dateizugriff.\n\n" +
                                "4. Sind meine Daten nach dem sicheren Löschen unwiderruflich gelöscht?\n" +
                                "- Die Daten sind stark überschrieben, aber auf Flash-Media kann man nie 100 % ausschließen, dass Spezial-Attacks noch Reste finden.\n\n" +
                                "5. Warum ist die App vertrauenswürdig?\n" +
                                "- Ergebnis einer Bachelor-Thesis der FernUniversität Hagen, Quellcode auf GitHub einsehbar."
                    )
                    .setPositiveButton("OK", null)
                    .show()
                true
            }
            R.id.action_exit -> {
                // App beenden
                finishAffinity()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // Helper for showing the storage size
    private fun formatGB(bytes: Long): String {
        val gb = bytes / 1_073_741_824.0
        return String.format("%.2f GB", gb)
    }

    // Further Helper functions
    private var statusDialog: Dialog? = null

    private fun showStatusDialog(status: String, progress: Int) {
        if (statusDialog == null) {
            statusDialog = Dialog().apply {
                dialogType = Dialog.Type.STATUS
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

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }
}