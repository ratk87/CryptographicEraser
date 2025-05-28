package com.example.cryptographiceraser

import android.Manifest
import android.content.pm.PackageManager
import android.os.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import android.view.Menu
import android.view.MenuItem
import androidx.fragment.app.FragmentManager

/**
 * MainActivity: Hosts the UI and manages file explorer integration for secure erasure.
 * Implements FileExplorer.OnFileSelectedListener to receive callbacks from the file explorer.
 */
class MainActivity : AppCompatActivity(), FileExplorer.OnFileSelectedListener {

    /** UI components for progress and status display. */
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView

    /** Volatile flag for background wipe cancellation. */
    @Volatile
    private var wipeCancelled = false

    private val STORAGE_PERMISSION_REQUEST_CODE = 1001

    // Keep a reference to the currently displayed FileExplorer fragment for refreshing
    private var fileExplorerFragment: FileExplorer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // --- Initialize UI views ---
        val textInternalStorage = findViewById<TextView>(R.id.textInternalStorage)
        val iconInternal = findViewById<ImageView>(R.id.iconInternal)
        val externalStorageRow = findViewById<LinearLayout>(R.id.externalStorageRow)
        val textExternalStorage = findViewById<TextView>(R.id.textExternalStorage)
        val iconExternal = findViewById<ImageView>(R.id.iconExternal)

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
        requestAllStoragePermissionsIfNeeded()

        // --- Initialize views for progress/status display ---
        progressBar = findViewById(R.id.progressBarWipe)
        statusText = findViewById(R.id.textWipeStatus)

        // --- (Debug) Test: Try writing a dummy file to external storage ---
        CryptoUtils.writeTestDummyFile(this)

        // --- Button: Shred single file using File Explorer ---
        findViewById<Button>(R.id.btnShredSingle).setOnClickListener {
            // Launch our own file explorer fragment in single-selection mode
            openFileExplorer(singleSelection = true)
        }

        // --- Button: Shred multiple files using File Explorer ---
        findViewById<Button>(R.id.btnShredMultiple).setOnClickListener {
            // Launch our own file explorer fragment in multi-selection mode
            openFileExplorer(singleSelection = false)
        }

        // --- Button: Wipe all free space (internal/external) ---
        findViewById<Button>(R.id.btnWipe).setOnClickListener {
            startFreeSpaceWipe()
        }
    }

    /**
     * Required by FileExplorer.OnFileSelectedListener. Called when user selects files for shredding.
     */
    override fun onFilesSelected(selectedFiles: List<File>) {
        // Call CryptoShred workflow with integrated modal status dialog
        if (selectedFiles.isNotEmpty()) {
            cryptoShredFiles(selectedFiles)
        } else {
            Toast.makeText(this, "No file selected.", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Launches the file explorer fragment for file selection.
     * @param singleSelection True = only one file selectable; False = multi-select allowed.
     */
    private fun openFileExplorer(singleSelection: Boolean) {
        // Check permissions for legacy Android
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    STORAGE_PERMISSION_REQUEST_CODE
                )
                Toast.makeText(this, "Storage permission required!", Toast.LENGTH_SHORT).show()
                return
            }
        }
        // Remove any existing FileExplorer fragment first (optional clean-up)
        supportFragmentManager.popBackStack("FileExplorer", FragmentManager.POP_BACK_STACK_INCLUSIVE)
        // Create fragment and set the file selection listener
        val fragment = FileExplorer()
        fragment.setOnFileSelectedListener(this)
        fileExplorerFragment = fragment
        // Show fragment in fragment_container (must be defined in your layout)
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment, "FileExplorer")
            .addToBackStack("FileExplorer")
            .commit()
    }

    /**
     * Handles the CryptoShred workflow:
     * (1) Encryption of the file(s)
     * (2) Deletion of key material and file(s)
     * (3) Double-wipe of free storage
     * Shows a modal, blocking status dialog and updates progress.
     */
    private fun cryptoShredFiles(selectedFiles: List<File>) {
        // Create and show the modal StatusDialog
        val statusDialog = StatusDialog()
        statusDialog.isCancelable = false
        statusDialog.show(supportFragmentManager, "StatusDialog")

        lifecycleScope.launch(Dispatchers.Main) {
            try {
                // (1) Prompt for password, show progress
                statusDialog.updateStatus("Waiting for password...", 0)
                val password = withContext(Dispatchers.Main) {
                    requestPassword(this@MainActivity, "Enter password for secure erase")
                }
                if (password == null || password.isEmpty()) { // Check for CharArray must look like this
                    statusDialog.dismiss()
                    Toast.makeText(this@MainActivity, "No password entered, aborted.", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // (2) Encryption step
                statusDialog.updateStatus("Encrypting file(s)...", 15)
                var successCount = 0
                var failedCount = 0
                for ((i, file) in selectedFiles.withIndex()) {
                    val ok = withContext(Dispatchers.IO) {
                        CryptoUtils.encryptFileInPlace(this@MainActivity, file, password)
                    }
                    if (ok) {
                        successCount++
                        // Fortschritt pro Datei
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

                // (3) Deletion step
                statusDialog.updateStatus("Deleting encryption key(s) and file(s)...", 85)
                for (file in selectedFiles) {
                    withContext(Dispatchers.IO) { file.delete() }
                }

                // (4) Double wipe step
                statusDialog.updateStatus("Wiping free space (1/2)...", 92)
                val documentsDir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: filesDir
                withContext(Dispatchers.IO) {
                    WipeUtils.doubleWipeFreeSpace(this@MainActivity, documentsDir)
                    Unit
                }
                statusDialog.updateStatus("Wiping free space (2/2)...", 97)
                withContext(Dispatchers.IO) {
                    WipeUtils.doubleWipeFreeSpace(this@MainActivity, documentsDir)
                    Unit
                }

                // Done
                statusDialog.updateStatus("Done!", 100)
                delay(700)
                statusDialog.dismiss()

                Toast.makeText(
                    this@MainActivity,
                    "$successCount of ${selectedFiles.size} files crypto-shredded. $failedCount failed.",
                    Toast.LENGTH_LONG
                ).show()

                // Refresh the FileExplorer view if present
                fileExplorerFragment?.refreshCurrentDir()

            } catch (e: Exception) {
                statusDialog.dismiss()
                Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Triggers a double wipe of the free space and updates the progress/status bar.
     * Wipes the free space in the Documents directory twice for improved forensics resistance.
     */
    private fun doubleWipeWithProgress() {
        val documentsDir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: filesDir
        progressBar.progress = 0
        progressBar.visibility = ProgressBar.VISIBLE
        statusText.visibility = TextView.VISIBLE
        statusText.text = "Wiping free space (2x)..."
        Thread {
            WipeUtils.doubleWipeFreeSpace(this@MainActivity, documentsDir)
            runOnUiThread {
                progressBar.visibility = ProgressBar.GONE
                statusText.text = "Secure deletion completed. If storage is not freed immediately, restart the device."
                Toast.makeText(this@MainActivity, "Secure deletion completed.", Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    /**
     * Starts the procedure to securely overwrite all available free space on internal storage.
     * Shows a progress bar and estimated time remaining.
     */
    private fun startFreeSpaceWipe() {
        Toast.makeText(this, "Starting free space wipe...", Toast.LENGTH_SHORT).show()
        val targetDir = filesDir
        val (total, free) = getStorageStats(targetDir)
        progressBar.max = if (free < Int.MAX_VALUE) free.toInt() else Int.MAX_VALUE
        progressBar.progress = 0
        progressBar.visibility = ProgressBar.VISIBLE
        statusText.visibility = TextView.VISIBLE
        statusText.text = "Total: ${formatSize(total)}, Free: ${formatSize(free)}"
        wipeCancelled = false
        Thread {
            val startTime = SystemClock.elapsedRealtime()
            val buffer = ByteArray(1024 * 1024)
            val rnd = java.security.SecureRandom()
            var written = 0L
            var idx = 0
            val wipeDir = File(targetDir, "wipe_tmp")
            if (!wipeDir.exists()) wipeDir.mkdirs()
            var cancelled = false
            try {
                while (written < free && !cancelled) {
                    val f = File(wipeDir, "wipe_dummy_$idx.bin")
                    idx++
                    FileOutputStream(f).use { out ->
                        var fileWritten = 0L
                        while (fileWritten < 100 * 1024 * 1024 && written < free && !cancelled) {
                            if (wipeCancelled) {
                                cancelled = true
                                return@use
                            }
                            rnd.nextBytes(buffer)
                            out.write(buffer)
                            written += buffer.size
                            fileWritten += buffer.size
                            runOnUiThread {
                                progressBar.progress = minOf(written, free).toInt()
                                val percent = 100 * written / free
                                val elapsed = SystemClock.elapsedRealtime() - startTime
                                val eta = if (written > 0) elapsed * (free - written) / written else 0
                                statusText.text = "Wiping: $percent% - ${formatSize(written)} / ${formatSize(free)}\nETA: ${formatTime(eta)}"
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            val cleaned = WipeUtils.cleanWipeDummyFiles(targetDir)
            wipeDir.deleteRecursively()
            runOnUiThread {
                progressBar.visibility = ProgressBar.GONE
                statusText.text = if (!cancelled)
                    "Wipe done! Freed ${formatSize(written)}. If storage is not freed immediately, restart the device."
                else
                    "Wipe cancelled! Cleaned up $cleaned files. Restart device if storage appears full."
            }
        }.start()
    }

    /**
     * Checks and requests runtime storage permissions (Play Store compliant).
     * On Android 13+ requests new media permissions. On older versions requests legacy storage permissions.
     */
    private fun requestAllStoragePermissionsIfNeeded() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13+
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED)
                perms.add(Manifest.permission.READ_MEDIA_IMAGES)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED)
                perms.add(Manifest.permission.READ_MEDIA_VIDEO)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED)
                perms.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
                perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
                perms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (perms.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, perms.toTypedArray(), STORAGE_PERMISSION_REQUEST_CODE)
        }
    }

    // ---------------- Helper/Utility Functions ----------------

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

    private fun formatTime(ms: Long): String {
        val s = ms / 1000
        val m = s / 60
        val sec = s % 60
        return if (m > 0) "$m min $sec s" else "$sec s"
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

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_REQUEST_CODE) {
            // Optionally provide feedback to the user about granted/denied permissions here
        }
    }
}
