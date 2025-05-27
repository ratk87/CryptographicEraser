package com.example.cryptographiceraser

import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.os.SystemClock
import android.widget.ProgressBar
import android.widget.TextView
import java.io.File
import java.io.FileOutputStream
import android.os.Environment
import android.os.StatFs
import android.widget.ImageView
import android.widget.LinearLayout
import android.view.Menu
import android.view.MenuItem
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.content.Intent




class MainActivity : AppCompatActivity() {

    /**
     * Returns a Pair of total and free space in bytes for the provided File directory.
     */
    private fun getStorageStats(directory: File): Pair<Long, Long> {
        val stat = StatFs(directory.absolutePath)
        val total = stat.blockCountLong * stat.blockSizeLong
        val free = stat.availableBlocksLong * stat.blockSizeLong
        return Pair(total, free)
    }

    /**
     * Formats bytes into a human-readable string (e.g., 2.5 GB)
     */
    private fun formatGB(bytes: Long): String {
        val gb = bytes / 1_073_741_824.0
        return String.format("%.2f GB", gb)
    }

    /**
     * "lateinit" means that the variable will be initialized later (not in the constructor).
     * It is only allowed for non-nullable var properties.
     * If you try to access it before assignment, you'll get an exception.
     * Used for Android views which are set in onCreate().
     */
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView

    /**
     * "@Volatile" means that this variable is stored in main memory and changes made by one thread are immediately visible to others.
     * Useful for flags or variables accessed by multiple threads, e.g., cancel signals for background operations.
     */
    @Volatile
    private var wipeCancelled = false

    /**
     * Launcher for single file selection using Storage Access Framework (SAF).
     * The user selects a file; after selection, the file is encrypted in-place (overwritten) and deleted.
     * The ephemeral encryption key is securely wiped from memory.
     * Afterwards, the app triggers a double wipe of the free space for extra security.
     */
    private val pickSingleFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            // Use a coroutine so the UI remains responsive
            lifecycleScope.launch {
                // 1. Prompt user for password (will be used for encryption)
                val password = requestPassword(this@MainActivity, "Enter password for secure erase")
                if (password != null && password.isNotEmpty()) {
                    // 2. Encrypt the file in-place (overwrite original file with ciphertext)
                    val encryptionSuccess = CryptoUtils.encryptFileInPlace(this@MainActivity, uri, password)

                    // 3. Key material is wiped inside CryptoUtils

                    if (encryptionSuccess) {
                        // 4. Inform user and request user-driven deletion via SAF Intent (ACTION_DELETE)
                        Toast.makeText(this@MainActivity, "File encrypted in place.", Toast.LENGTH_LONG).show()

                        // Create SAF delete intent
                        val deleteIntent = Intent(Intent.ACTION_DELETE).apply {
                            data = uri
                            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        }
                        try {
                            startActivity(deleteIntent)
                            Toast.makeText(
                                this@MainActivity,
                                "Please confirm deletion in the system dialog.\nThis is required by Android policy.",
                                Toast.LENGTH_LONG
                            ).show()
                        } catch (e: Exception) {
                            Toast.makeText(
                                this@MainActivity,
                                "Delete action not supported for this file/location!",
                                Toast.LENGTH_LONG
                            ).show()
                        }

                        // 5. Trigger double wipe (optional but recommended)
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
                    } else {
                        // Encryption failed (e.g., no permission, file locked, ...), show error
                        Toast.makeText(this@MainActivity, "Error during encryption", Toast.LENGTH_LONG).show()
                    }
                } else {
                    // User cancelled the password dialog
                    Toast.makeText(this@MainActivity, "No password entered, aborted.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }



    /**
     * Launcher for multiple file selection using Storage Access Framework (SAF).
     * The user selects one or more files; each file is encrypted in-place (overwritten) and deleted.
     * All ephemeral keys are securely wiped. At the end, a double wipe of free space is triggered.
     */
    private val pickMultipleFilesLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            lifecycleScope.launch {
                // 1. Prompt user for password (used for all selected files)
                val password = requestPassword(this@MainActivity, "Enter password for secure erase")
                if (password != null && password.isNotEmpty()) {
                    var successCount = 0    // Counter for successful encryptions
                    var failedCount = 0     // Counter for failures

                    // 2. Encrypt each file in-place (loop over all uris)
                    for (uri in uris) {
                        if (CryptoUtils.encryptFileInPlace(this@MainActivity, uri, password)) {
                            successCount++
                            // *** Delete each file with user confirmation (SAF Intent) ***
                            val deleteIntent = Intent(Intent.ACTION_DELETE).apply {
                                data = uri
                                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            }
                            try {
                                startActivity(deleteIntent)
                                Toast.makeText(
                                    this@MainActivity,
                                    "Please confirm deletion for each file in the system dialog.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } catch (e: Exception) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Delete not supported for one or more files!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } else {
                            failedCount++
                        }
                    }

                    // 3. Summary for user
                    Toast.makeText(
                        this@MainActivity,
                        "$successCount of ${uris.size} files encrypted in place. $failedCount failed.\nYou must confirm each file deletion in the system dialog.",
                        Toast.LENGTH_LONG
                    ).show()

                    // 4. Double wipe as last step
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
                } else {
                    Toast.makeText(this@MainActivity, "No password entered, aborted.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }



    private val STORAGE_PERMISSION_REQUEST_CODE = 1001


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Storage info Views
        val textInternalStorage = findViewById<TextView>(R.id.textInternalStorage)
        val iconInternal = findViewById<ImageView>(R.id.iconInternal)
        val externalStorageRow = findViewById<LinearLayout>(R.id.externalStorageRow)
        val textExternalStorage = findViewById<TextView>(R.id.textExternalStorage)
        val iconExternal = findViewById<ImageView>(R.id.iconExternal)

        // Internal Storage: always available
        val (intTotal, intFree) = getStorageStats(filesDir)
        textInternalStorage.text = "Internal Storage (Total / Free): ${formatGB(intTotal)} / ${formatGB(intFree)}"

        // External Storage (SD card): only show if mounted and accessible
        val extDir = getExternalFilesDir(null)
        if (extDir != null && Environment.getExternalStorageState(extDir) == Environment.MEDIA_MOUNTED) {
            val (extTotal, extFree) = getStorageStats(extDir)
            externalStorageRow.visibility = LinearLayout.VISIBLE
            textExternalStorage.text = "SD Card (Total / Free): ${formatGB(extTotal)} / ${formatGB(extFree)}"
        } else {
            externalStorageRow.visibility = LinearLayout.GONE
        }

        // Ensuring that access permission is / was being requested
        requestAllStoragePermissionsIfNeeded()

        // "lateinit" views are initialized here, after the layout is set.
        progressBar = findViewById(R.id.progressBarWipe)
        statusText = findViewById(R.id.textWipeStatus)

        //******DEBUGGING: Test if file writing is actually working
        CryptoUtils.writeTestDummyFile(this)
        //****************

        // Button for shredding a single file
        findViewById<Button>(R.id.btnShredSingle).setOnClickListener {
            // Launch file picker for a single file of any type
            pickSingleFileLauncher.launch(arrayOf("*/*"))
        }

        // Button for shredding multiple files
        findViewById<Button>(R.id.btnShredMultiple).setOnClickListener {
            // Launch file picker for multiple files of any type
            pickMultipleFilesLauncher.launch(arrayOf("*/*"))
        }

        // Button for wiping free space (internal, can be extended for external/SD)
        findViewById<Button>(R.id.btnWipe).setOnClickListener {
            /**
             * This triggers the secure free-space wiping procedure for the internal app directory.
             * The process runs in a background thread to avoid blocking the UI.
             * A progress bar and status text provide real-time feedback about the operation,
             * including total storage, free space, current progress, and an ETA (estimated time remaining).
             * After completion, both the number of bytes written (wiped) and the number of
             * deleted dummy files are reported via Toast and statusText.
             */

            Toast.makeText(this, "Starting free space wipe...", Toast.LENGTH_SHORT).show()

            // Choose target directory: here, internal storage directory
            val targetDir = filesDir

            // Gather storage stats for status and progress bar
            val (total, free) = WipeUtils.getStorageStats(targetDir)
            // Set ProgressBar max; for large values, ensure it's within Int range
            progressBar.max = if (free < Int.MAX_VALUE) free.toInt() else Int.MAX_VALUE
            progressBar.progress = 0
            progressBar.visibility = ProgressBar.VISIBLE
            statusText.visibility = TextView.VISIBLE
            statusText.text = "Total: ${formatSize(total)}, Free: ${formatSize(free)}"

            // Cancel flag for the operation
            wipeCancelled = false

            // Start wipe process in background
            Thread {
                val startTime = SystemClock.elapsedRealtime()
                val buffer = ByteArray(1024 * 1024) // 1 MB buffer
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
                                // Check if user requested cancel; @Volatile ensures thread visibility
                                if (wipeCancelled) {
                                    cancelled = true
                                    return@use // exit this FileOutputStream
                                }
                                rnd.nextBytes(buffer)
                                out.write(buffer)
                                written += buffer.size
                                fileWritten += buffer.size

                                // Update progress and ETA in UI thread
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
                // After wipe: clean up dummy files to restore space
                val cleaned = WipeUtils.cleanWipeDummyFiles(targetDir)
                //val wipeDir = File(targetDir, "wipe_tmp")
                wipeDir.deleteRecursively() // Ensures the folder itself is deleted
                runOnUiThread {
                    progressBar.visibility = ProgressBar.GONE
                    statusText.text = if (!cancelled)
                        "Wipe done! Freed ${formatSize(written)}. If storage is not freed immediately, restart the device."
                    else
                        "Wipe cancelled! Cleaned up $cleaned files. Restart device if storage appears full."
                }
            }.start()
        }
    }

    /**
     * Helper to cancel the wipe operation (can be bound to a Cancel button if desired).
     * @Volatile ensures immediate effect across threads.
     */
    private fun cancelWipe() { wipeCancelled = true }

    /**
     * Helper to format bytes as KB, MB, GB for status text.
     */
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

    /**
     * Helper to format milliseconds as a human-readable time string.
     */
    private fun formatTime(ms: Long): String {
        val s = ms / 1000
        val m = s / 60
        val sec = s % 60
        return if (m > 0) "$m min $sec s" else "$sec s"
    }

    /**
     * Inflates the options menu in the app bar (top right).
     * This method is called by the Android system when the options menu is created.
     * The menu resource (menu_main.xml) defines which menu items appear in the app bar.
     *
     * @param menu The options menu in which items are placed.
     * @return true to display the menu; false otherwise.
     */
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true // Return true so the menu is shown in the app bar
    }

    /**
     * Handles user clicks on the menu items in the app bar.
     * Called whenever a menu item is selected.
     *
     * @param item The menu item that was selected.
     * @return true if the event was handled; false to allow normal menu processing to proceed.
     */
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

    /**
     * Requests all relevant storage/media permissions for Android 9-15 in a Play Store-compliant way.
     * This enables access to files in Downloads, Pictures, etc. after user consent.
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
            // Android 9-12
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
                perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
                perms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (perms.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, perms.toTypedArray(), STORAGE_PERMISSION_REQUEST_CODE)
        }
    }
    // Optional Implementation: Handles the messaging to the user about the permission result -> Full Transparency!
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_REQUEST_CODE) {
            // Optional: Check what was granted/denied and show feedback
        }
    }

}
