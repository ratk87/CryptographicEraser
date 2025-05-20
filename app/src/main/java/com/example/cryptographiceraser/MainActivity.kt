package com.example.cryptographiceraser

import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    /**
     * Launcher for single file selection using Storage Access Framework.
     * Input: User selects a file.
     * Output: Callback with Uri of selected file, or null if cancelled.
     */
    private val pickSingleFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            lifecycleScope.launch {
                val password = requestPassword(this@MainActivity, "Enter password for secure erase")
                if (password != null && password.isNotEmpty()) {
                    val success = CryptoUtils.encryptFileAndSaveCopy(this@MainActivity, uri, password)
                    if (success) {
                        Toast.makeText(this@MainActivity, "File encrypted (.encrypted file created)", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this@MainActivity, "Error during encryption", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(this@MainActivity, "No password entered, aborted.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Launcher for multiple file selection using Storage Access Framework.
     * Input: User selects one or more files.
     * Output: Callback with list of Uris, or empty list if cancelled.
     */
    // For multiple files:
    private val pickMultipleFilesLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            lifecycleScope.launch {
                val password = requestPassword(this@MainActivity, "Enter password for secure erase")
                if (password != null && password.isNotEmpty()) {
                    var successCount = 0
                    for (uri in uris) {
                        if (CryptoUtils.encryptFileAndSaveCopy(this@MainActivity, uri, password)) {
                            successCount++
                        }
                    }
                    Toast.makeText(this@MainActivity, "$successCount of ${uris.size} files encrypted.", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@MainActivity, "No password entered, aborted.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

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
    }
}
