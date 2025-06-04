package com.example.cryptographiceraser

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import java.io.File

class MainActivity : AppCompatActivity(), FileExplorer.OnFileSelectedListener {

    private lateinit var controller: CryptoEraseController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Controller initialisieren
        controller = CryptoEraseController(
            context = this,
            fragmentManager = supportFragmentManager,
            lifecycleScope = lifecycleScope
        )

        // UI-Elemente
        val textInternalStorage = findViewById<TextView>(R.id.textInternalStorage)
        val externalStorageRow = findViewById<LinearLayout>(R.id.externalStorageRow)
        val textExternalStorage = findViewById<TextView>(R.id.textExternalStorage)

        // Speicherinformationen anzeigen
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

        // Button: Shred Single File
        findViewById<Button>(R.id.btnShredSingle).setOnClickListener {
            openFileExplorer(singleSelection = true)
        }

        // Button: Shred Multiple Files
        findViewById<Button>(R.id.btnShredMultiple).setOnClickListener {
            openFileExplorer(singleSelection = false)
        }

        // Button: Wipe Free Space
        findViewById<Button>(R.id.btnWipe).setOnClickListener {
            val dir = filesDir // oder getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            WipeUtils.wipeFreeSpaceWithFeedback(this, dir)
        }
    }

    // Öffnet den FileExplorer (Fragment)
    private fun openFileExplorer(singleSelection: Boolean) {
        supportFragmentManager.popBackStack("FileExplorer", FragmentManager.POP_BACK_STACK_INCLUSIVE)
        val fragment = FileExplorer()
        fragment.setOnFileSelectedListener(this)
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment, "FileExplorer")
            .addToBackStack("FileExplorer")
            .commit()
    }

    // Callback vom FileExplorer – startet Controller-Workflow
    override fun onFilesSelected(selectedFiles: List<File>) {
        if (selectedFiles.isNotEmpty()) {
            controller.shredFiles(selectedFiles) { ok, fail ->
                Toast.makeText(this, "$ok Dateien gelöscht, $fail Fehler.", Toast.LENGTH_LONG).show()
                // NEU: Nach Löschvorgang den FileExplorer aktualisieren!
                val fragment = supportFragmentManager.findFragmentByTag("FileExplorer")
                if (fragment is FileExplorer) {
                    fragment.refreshCurrentDir()
                }
            }
        } else {
            Toast.makeText(this, "Keine Datei gewählt.", Toast.LENGTH_SHORT).show()
        }
    }


    // Helfer für Speichergrößenanzeige
    private fun formatGB(bytes: Long): String {
        val gb = bytes / 1_073_741_824.0
        return String.format("%.2f GB", gb)
    }
}
