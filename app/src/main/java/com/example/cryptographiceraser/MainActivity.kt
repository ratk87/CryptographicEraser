package com.example.cryptographiceraser

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import android.provider.Settings


/**
 * MainActivity: Hosts the UI and manages file explorer integration for secure erasure.
 * Implements FileExplorer.OnFileSelectedListener to receive callbacks from the file explorer.
 */
class MainActivity : AppCompatActivity(), FileExplorer.OnFileSelectedListener {

    @Volatile
    private var wipeCancelled = false

    private val STORAGE_PERMISSION_REQUEST_CODE = 1001
    private val SAF_PICK_FILE_REQUEST_CODE = 10234 // For SAF file picker
    private var fileExplorerFragment: FileExplorer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // --- Initialize UI views ---
        val textInternalStorage = findViewById<TextView>(R.id.textInternalStorage)
        val externalStorageRow = findViewById<LinearLayout>(R.id.externalStorageRow)
        val textExternalStorage = findViewById<TextView>(R.id.textExternalStorage)

        // --- Show device storage information ---
        val (intTotal, intFree) = getStorageStats(filesDir)
        textInternalStorage.text = "Internal Storage (Total / Free): ${formatGB(intTotal)} / ${formatGB(intFree)}"
        val extDir = getExternalFilesDir(null)
        if (extDir != null && Environment.getExternalStorageState(extDir) == Environment.MEDIA_MOUNTED) {
            val (extTotal, extFree) = getStorageStats(extDir)
            externalStorageRow.visibility = LinearLayout.VISIBLE
            textExternalStorage.text = "SD Card (Total / Free): ${formatGB(extTotal)} / ${formatGB(extFree)}"
        } else {
            externalStorageRow.visibility = LinearLayout.GONE
        }

        // --- Ask for runtime storage permissions if required ---
        requestAllFilesAccessIfNeeded()

        findViewById<Button>(R.id.btnShredSingle).setOnClickListener {
            if (hasAllFilesAccess()) {
                openFileExplorer(singleSelection = true)
            } else {
                Toast.makeText(this, "Bitte 'Alle Dateien'-Berechtigung aktivieren!", Toast.LENGTH_LONG).show()
                requestAllFilesAccessIfNeeded()
            }
        }

        findViewById<Button>(R.id.btnShredMultiple).setOnClickListener {
            if (hasAllFilesAccess()) {
                openFileExplorer(singleSelection = false)
            } else {
                Toast.makeText(this, "Bitte 'Alle Dateien'-Berechtigung aktivieren!", Toast.LENGTH_LONG).show()
                requestAllFilesAccessIfNeeded()
            }
        }

        // --- Button: Wipe all free space (internal/external) ---
        findViewById<Button>(R.id.btnWipe).setOnClickListener {
            val targetDir = filesDir // or getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            WipeUtils.wipeFreeSpaceWithFeedback(this, targetDir)
        }
    }

    /**
     * Required by FileExplorer.OnFileSelectedListener. Called when user selects files for shredding.
     * Only used on Android < 10.
     */
    override fun onFilesSelected(selectedFiles: List<File>) {
        if (selectedFiles.isNotEmpty()) {
            cryptoShredFiles(selectedFiles)
        } else {
            Toast.makeText(this, "No file selected.", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Launches the file explorer fragment for file selection.
     * @param singleSelection True = only one file selectable; False = multi-select allowed.
     * Only used on Android < 10.
     */
    private fun openFileExplorer(singleSelection: Boolean) {
        // Für Android 11+ (API 30) reicht MANAGE_EXTERNAL_STORAGE, alte Berechtigungen sind dann nicht mehr relevant
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Keine zusätzliche Abfrage für WRITE_EXTERNAL_STORAGE!
            supportFragmentManager.popBackStack("FileExplorer", FragmentManager.POP_BACK_STACK_INCLUSIVE)
            val fragment = FileExplorer()
            fragment.setOnFileSelectedListener(this)
            fileExplorerFragment = fragment
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment, "FileExplorer")
                .addToBackStack("FileExplorer")
                .commit()
            return
        }
        // Für Android 6 - 10: Prüfe klassische Storage-Permission!
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                STORAGE_PERMISSION_REQUEST_CODE
            )
            Toast.makeText(this, "Storage permission required!", Toast.LENGTH_SHORT).show()
            return
        }
        supportFragmentManager.popBackStack("FileExplorer", FragmentManager.POP_BACK_STACK_INCLUSIVE)
        val fragment = FileExplorer()
        fragment.setOnFileSelectedListener(this)
        fileExplorerFragment = fragment
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment, "FileExplorer")
            .addToBackStack("FileExplorer")
            .commit()
    }


    /**
     * Handles the CryptoShred workflow for classic File access (Android < 10):
     * (1) Encryption of the file(s)
     * (2) Deletion of key material and file(s)
     * (3) Double-wipe of free storage
     * Shows a modal, blocking status dialog and updates progress.
     */
    private fun cryptoShredFiles(selectedFiles: List<File>) {
        val statusDialog = StatusDialog()
        statusDialog.isCancelable = false
        statusDialog.show(supportFragmentManager, "StatusDialog")

        lifecycleScope.launch(Dispatchers.Main) {
            try {
                statusDialog.updateStatus("Waiting for password...", 0)
                val password = requestPasswordDialog(this@MainActivity, "Enter password for secure erase")

                if (password == null || password.isEmpty()) {
                    statusDialog.dismissAllowingStateLoss()
                    Toast.makeText(this@MainActivity, "No password entered, aborted.", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                statusDialog.updateStatus("Encrypting file(s)...", 15)
                var successCount = 0
                var failedCount = 0
                for ((i, file) in selectedFiles.withIndex()) {
                    val ok = withContext(Dispatchers.IO) {
                        CryptoUtils.encryptFileInPlace(this@MainActivity, file, password)
                    }
                    if (ok) {
                        successCount++
                        statusDialog.updateStatus(
                            "Encrypted ${file.name} (${i + 1}/${selectedFiles.size})...",
                            15 + (70 * (i + 1) / selectedFiles.size)
                        )
                    } else {
                        failedCount++
                        statusDialog.updateStatus(
                            "Failed to encrypt ${file.name}!",
                            15 + (70 * (i + 1) / selectedFiles.size)
                        )
                    }
                }
                statusDialog.updateStatus("Deleting encryption key(s) and file(s)...", 85)
                for (file in selectedFiles) {
                    withContext(Dispatchers.IO) { file.delete() }
                }
                statusDialog.updateStatus("Wiping free space (1/2)...", 92)
                val documentsDir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: filesDir
                withContext(Dispatchers.IO) {
                    WipeUtils.doubleWipeFreeSpace(this@MainActivity, documentsDir)
                }
                statusDialog.updateStatus("Wiping free space (2/2)...", 97)
                withContext(Dispatchers.IO) {
                    WipeUtils.doubleWipeFreeSpace(this@MainActivity, documentsDir)
                }
                statusDialog.updateStatus("Done!", 100)
                delay(700)
                statusDialog.dismissAllowingStateLoss()
                Toast.makeText(
                    this@MainActivity,
                    "$successCount of ${selectedFiles.size} files crypto-shredded. $failedCount failed.",
                    Toast.LENGTH_LONG
                ).show()
                fileExplorerFragment?.refreshCurrentDir()
            } catch (e: Exception) {
                statusDialog.dismissAllowingStateLoss()
                Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // -------------- SAF FilePicker & Secure Erasure for Android 10+ ---------------

    /**
     * Launches the Storage Access Framework file picker.
     */
    private fun openSafFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(intent, SAF_PICK_FILE_REQUEST_CODE)
    }


    // HELPER FUNCTIONS
    // --------------------------------------------------------------------------
    private fun getStorageStats(directory: File): Pair<Long, Long> {
        val stat = StatFs(directory.absolutePath)
        val total = stat.blockCountLong * stat.blockSizeLong
        val free = stat.availableBlocksLong * stat.blockSizeLong
        return Pair(total, free)
    }

    private fun formatGB(bytes: Long): String {
        val gb = bytes / 1_073_741_824.0
        return String.format("%.2f GB", gb)
    }

    private fun formatSize(bytes: Long): String {
        val kb = bytes / 1024
        val mb = kb / 1024
        val gb = mb / 1024
        return when {
            gb > 0 -> "$gb GB"
            mb > 0 -> "$mb MB"
            kb > 0 -> "$kb KB"
            else -> "$bytes B"
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_exit -> {
                finishAffinity()
                true
            }
            R.id.action_faq -> {
                Toast.makeText(this, "FAQ selected (not implemented yet)", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_info -> {
                showAndroidStorageInfoDialog(this)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // Prüft, ob MANAGE_EXTERNAL_STORAGE vorliegt
    private fun hasAllFilesAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else true // Für Android < 11 nicht relevant
    }

    // Fordert Permission an, wenn nicht vorhanden
    private fun requestAllFilesAccessIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !hasAllFilesAccess()) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
            Toast.makeText(this, "Bitte erlaube den Zugriff auf alle Dateien, um sichere Löschung zu ermöglichen!", Toast.LENGTH_LONG).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_REQUEST_CODE) {
            // Optionally provide feedback to the user about granted/denied permissions here
        }
    }
}
