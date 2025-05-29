package com.example.cryptographiceraser

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
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
import android.provider.Settings

class MainActivity : AppCompatActivity(), FileExplorer.OnFileSelectedListener {

    @Volatile
    private var wipeCancelled = false

    private val STORAGE_PERMISSION_REQUEST_CODE = 1001
    private val SAF_PICK_FILE_REQUEST_CODE = 10234
    private var fileExplorerFragment: FileExplorer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val textInternalStorage = findViewById<TextView>(R.id.textInternalStorage)
        val externalStorageRow = findViewById<LinearLayout>(R.id.externalStorageRow)
        val textExternalStorage = findViewById<TextView>(R.id.textExternalStorage)
        val btnDebugDecrypt = findViewById<Button>(R.id.btnDebugDecrypt)

        // Speicher-Infos anzeigen
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

        // Button: Wipe all free space
        findViewById<Button>(R.id.btnWipe).setOnClickListener {
            val targetDir = filesDir // oder getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            WipeUtils.wipeFreeSpaceWithFeedback(this, targetDir)
        }

        // Debug-Button Sichtbarkeit initial setzen
        btnDebugDecrypt.visibility = if (AppConfig.debugModeEnabled) Button.VISIBLE else Button.GONE

        // --- Debug-Button Funktion ---
// --- Debug: Entschlüsseln und Prüfen (Testmode) ---
        btnDebugDecrypt.setOnClickListener {
            // Datei im Explorer wählen lassen
            val fragment = FileExplorer()
            fragment.setOnFileSelectedListener(object : FileExplorer.OnFileSelectedListener {
                override fun onFilesSelected(selectedFiles: List<File>) {
                    if (selectedFiles.isEmpty()) {
                        Toast.makeText(this@MainActivity, "Keine Datei gewählt.", Toast.LENGTH_SHORT).show()
                        return
                    }
                    val file = selectedFiles.first()
                    lifecycleScope.launch {
                        val password = requestPasswordDialog(this@MainActivity, "Passwort zum Verschlüsseln/Entschlüsseln")
                        if (password == null || password.isEmpty()) {
                            Toast.makeText(this@MainActivity, "Kein Passwort eingegeben.", Toast.LENGTH_SHORT).show()
                            return@launch
                        }
// (a) Vorher: Hash und Log
                        val hashOrig = DebugCryptoFunc.sha256(file)
                        DebugCryptoFunc.log(this@MainActivity, "DEBUG-TEST: SHA256 Original: $hashOrig (${file.name})")

// (b) Verschlüsseln
                        val encryptedFile = File(file.parent, file.name + ".enc")
                        val okEnc = withContext(Dispatchers.IO) {
                            CryptoUtils.encryptFileInPlaceCustom(this@MainActivity, file, password, encryptedFile)
                        }
                        if (!okEnc) {
                            Toast.makeText(this@MainActivity, "Fehler bei Verschlüsselung!", Toast.LENGTH_LONG).show()
                            return@launch
                        }
                        val hashEnc = DebugCryptoFunc.sha256(encryptedFile)
                        DebugCryptoFunc.log(this@MainActivity, "DEBUG-TEST: SHA256 nach Verschlüsselung: $hashEnc (${encryptedFile.name})")

// (c) Entschlüsseln
                        val decryptedFile = File(file.parent, "decrypted_" + file.name)
                        val okDec = withContext(Dispatchers.IO) {
                            CryptoUtils.decryptFile(this@MainActivity, encryptedFile, password, decryptedFile)
                        }
                        if (!okDec) {
                            Toast.makeText(this@MainActivity, "Fehler bei Entschlüsselung!", Toast.LENGTH_LONG).show()
                            return@launch
                        }
                        val hashDec = DebugCryptoFunc.sha256(decryptedFile)
                        DebugCryptoFunc.log(this@MainActivity, "DEBUG-TEST: SHA256 nach Entschlüsselung: $hashDec (${decryptedFile.name})")

// (d) Ergebnis/Check
                        val result = if (hashOrig == hashDec) "ERFOLG: Die entschlüsselte Datei ist identisch!" else "FEHLER: Dateien unterscheiden sich!"
                        DebugCryptoFunc.log(this@MainActivity, "DEBUG-TEST: Vergleichsergebnis: $result")
                        Toast.makeText(this@MainActivity, result, Toast.LENGTH_LONG).show()
                    }
                }
            })
            supportFragmentManager.popBackStack("FileExplorer", FragmentManager.POP_BACK_STACK_INCLUSIVE)
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment, "FileExplorer")
                .addToBackStack("FileExplorer")
                .commit()
        }
    }

    override fun onFilesSelected(selectedFiles: List<File>) {
        if (selectedFiles.isNotEmpty()) {
            cryptoShredFiles(selectedFiles)
        } else {
            Toast.makeText(this, "No file selected.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openFileExplorer(singleSelection: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
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
                if (!AppConfig.debugModeEnabled) {
                    statusDialog.updateStatus("Deleting encryption key(s) and file(s)...", 85)
                    for (file in selectedFiles) {
                        withContext(Dispatchers.IO) { file.delete() }
                    }
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

    // Helper-Methoden
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

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_exit -> {
                finishAffinity()
                return true
            }
            R.id.action_faq -> {
                Toast.makeText(this, "FAQ selected (not implemented yet)", Toast.LENGTH_SHORT).show()
                return true
            }
            R.id.action_info -> {
                showAndroidStorageInfoDialog(this)
                return true
            }
            R.id.action_debug_mode -> {
                AppConfig.debugModeEnabled = !AppConfig.debugModeEnabled
                val msg = if (AppConfig.debugModeEnabled) "Debug Mode AKTIV" else "Debug Mode deaktiviert"
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                // Sichtbarkeit des Debug-Buttons aktualisieren!
                findViewById<Button>(R.id.btnDebugDecrypt).visibility =
                    if (AppConfig.debugModeEnabled) Button.VISIBLE else Button.GONE
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun hasAllFilesAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else true
    }

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
            // Optional: Nutzerfeedback
        }
    }
}
