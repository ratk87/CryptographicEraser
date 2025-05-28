package com.example.cryptographiceraser

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object CryptoUtils {

    private const val TAG = "CryptoUtils"

    /**
     * Verschlüsselt eine Datei IN PLACE mit AES-GCM und PBKDF2.
     * Überschreibt die Originaldatei mit [salt | iv | ciphertext].
     * Nutzt explizites Buffering, damit auch sehr große Dateien ohne OutOfMemoryError bearbeitet werden können.
     * Gibt true zurück bei Erfolg, sonst false.
     *
     * @param context   Android Context (reserviert für Logging/Fehlermeldungen)
     * @param file      Zu verschlüsselnde Datei
     * @param password  Passwort als CharArray für PBKDF2
     */
    fun encryptFileInPlace(context: Context, file: File, password: CharArray): Boolean {
        val salt = ByteArray(16)
        val iv = ByteArray(12)
        java.security.SecureRandom().nextBytes(salt)
        java.security.SecureRandom().nextBytes(iv)
        val iterations = 100_000
        val keyLength = 256

        var input: java.io.InputStream? = null
        var output: java.io.OutputStream? = null
        var cipherOut: CipherOutputStream? = null

        try {
            val spec = PBEKeySpec(password, salt, iterations, keyLength)
            val keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val secretKey = SecretKeySpec(keyFactory.generateSecret(spec).encoded, "AES")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)

            // Schreibe in temporäre Datei, um Atomizität zu gewährleisten
            val tempFile = File(file.parent, file.name + ".enc")
            input = file.inputStream()
            output = tempFile.outputStream()
            output.write(salt)   // 16 Byte: Salt
            output.write(iv)     // 12 Byte: IV
            cipherOut = CipherOutputStream(output, cipher)

            val buffer = ByteArray(16 * 1024) // 16KB Buffer für große Dateien
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                cipherOut.write(buffer, 0, bytesRead)
            }
            cipherOut.flush() // sicherstellen, dass alles geschrieben wurde

            // Atomarer Dateitausch
            input.close()
            cipherOut.close()
            output.close()

            if (file.delete()) {
                if (tempFile.renameTo(file)) {
                    // Schlüsselmaterial aus RAM löschen
                    spec.clearPassword()
                    password.fill('\u0000')
                    secretKey.encoded.fill(0)
                    return true
                } else {
                    tempFile.delete()
                    return false
                }
            } else {
                tempFile.delete()
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Encryption failed: ", e)
            return false
        } finally {
            // Fallback: Streams schließen, falls noch offen
            try { cipherOut?.close() } catch (_: Exception) {}
            try { output?.close() } catch (_: Exception) {}
            try { input?.close() } catch (_: Exception) {}
        }
    }

    /**
     * Nur zum Debuggen: Schreibt eine Testdatei ins Documents-Verzeichnis.
     */
    fun writeTestDummyFile(context: Context) {
        try {
            val outDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            outDir?.mkdirs()
            val dummyFile = File(outDir, "test_dummy.txt")
            FileOutputStream(dummyFile).use { out ->
                out.write("Hello from CryptographicEraser!\nThis is a dummy test file.".toByteArray())
            }
            Log.d(TAG, "Dummy file written successfully: ${dummyFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error writing dummy file: ", e)
        }
    }
}
