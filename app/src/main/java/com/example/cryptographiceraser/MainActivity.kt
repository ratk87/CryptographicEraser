package com.example.cryptographiceraser

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream

/**
 * MainActivity
 *
 * Was?
 *   - Bietet UI (Button) zur Dateiauswahl und startet Crypto-Shredding
 */
class MainActivity : AppCompatActivity() {

    // Launcher zur Auswahl einer Datei
    private val pickFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { shredFile(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Layout setzen
        setContentView(R.layout.activity_main)

        // Button-Click → Datei auswählen
        findViewById<Button>(R.id.btnShred).setOnClickListener {
            pickFileLauncher.launch(arrayOf("*/*"))
        }
    }

    /**
     * Funktion: shredFile
     * Input: uri: Uri (Ausgewählte Datei)
     * Output: nichts (zeigt Toasts)
     * Was?
     *   1. Ephemeral-Key generieren (KDF mit Passwort-Dialog)
     *   2. Datei verschlüsseln → temporäre Datei
     *   3. Schlüssel aus RAM löschen
     *   4. Original + Ciphertext löschen
     *   5. Freien Speicher wipe
     */
    private fun shredFile(uri: Uri) {
        Toast.makeText(this, "Shredding gestartet…", Toast.LENGTH_SHORT).show()

        // Coroutinen-Scope für Hintergrund-Arbeit
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1) Passwort einholen (Blockiert UI-Thread kurz)
                val password = getPasswordFromUser()

                // 2) Ephemeral-Key & Salt erzeugen
                val (key, salt) = CryptoUtils.generateEphemeralXtsKey(password)

                // 3) File-Objekte erzeugen
                val srcFile = DocumentFile.fromSingleUri(this@MainActivity, uri)
                    ?: throw IllegalStateException("Datei nicht gefunden")
                val inputFile = File(cacheDir, "input.tmp")
                // Original in Cache kopieren
                contentResolver.openInputStream(uri)?.use { inp ->
                    FileOutputStream(inputFile).use { out -> inp.copyTo(out) }
                }
                val encryptedFile = File(cacheDir, "shredded.enc")

                // 4) Verschlüsseln
                CryptoUtils.encryptFileWithXtsKey(inputFile, encryptedFile, key)

                // 5) Key aus RAM löschen
                CryptoUtils.clearEphemeralKey(key)

                // 6) Original und verschlüsselte Datei löschen
                srcFile.delete()
                inputFile.delete()
                encryptedFile.delete()

                // 7) Freien Speicher füllen & löschen
                CryptoUtils.wipeFreeSpace(this@MainActivity)

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Shredding abgeschlossen!",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Fehler: ${e.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    /**
     * Funktion: getPasswordFromUser
     * Input: keine
     * Output: CharArray (Passwort)
     * Was?
     *   - Öffnet einen Dialog auf dem UI-Thread
     *   - Liest Passwort sicher ein
     */
    private suspend fun getPasswordFromUser(): CharArray = withContext(Dispatchers.Main) {
        // Hier als Platzhalter: In Echt z.B. AlertDialog mit EditText (inputType=password)
        // Blockiert so lange, bis User bestätigt
        // Rückgabe eines CharArray mit Passwort
        return@withContext charArrayOf(/* TODO: Passwort einfügen */)
    }
}
