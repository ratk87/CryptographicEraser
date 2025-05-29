package com.example.cryptographiceraser

import android.content.Context
import android.util.Log
import java.io.*
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object CryptoUtils {

    private const val TAG = "CryptoUtils"

    /**
     * Encrypts a file IN PLACE using AES-GCM and PBKDF2.
     * Overwrites the original file by writing [salt | iv | ciphertext].
     * Returns true if successful, false otherwise.
     * With MANAGE_EXTERNAL_STORAGE also works in /storage/emulated/0.
     */
    fun encryptFileInPlace(context: Context, file: File, password: CharArray): Boolean {
        val salt = ByteArray(16)
        val iv = ByteArray(12)
        SecureRandom().nextBytes(salt)
        SecureRandom().nextBytes(iv)
        val iterations = 100_000
        val keyLength = 256

        try {
            // Hash & Log VOR der Verschlüsselung (nur Debug Mode)
            if (AppConfig.debugModeEnabled) {
                val hashVorher = DebugCryptoFunc.sha256(file)
                DebugCryptoFunc.log(context, "Original SHA256: $hashVorher (Datei: ${file.name})")
            }

            val spec = PBEKeySpec(password, salt, iterations, keyLength)
            val keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val secretKey = SecretKeySpec(keyFactory.generateSecret(spec).encoded, "AES")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)

            // Schreibe verschlüsselten Inhalt in temporäre Datei
            val tempFile = File(file.parent, file.name + ".enc")
            file.inputStream().use { input ->
                tempFile.outputStream().use { output ->
                    output.write(salt)
                    output.write(iv)
                    CipherOutputStream(output, cipher).use { cipherOut ->
                        input.copyTo(cipherOut, bufferSize = 4096)
                    }
                }
            }
            // Ersetze Originaldatei atomar durch verschlüsselte Datei
            val ok = if (file.delete()) {
                tempFile.renameTo(file)
            } else {
                tempFile.delete()
                false
            }

            // Hash & Log NACH der Verschlüsselung (nur Debug Mode)
            if (AppConfig.debugModeEnabled && ok) {
                val hashNachher = DebugCryptoFunc.sha256(file)
                DebugCryptoFunc.log(context, "Nach Verschlüsselung SHA256: $hashNachher (Datei: ${file.name})")
            }

            // Schlüsselmaterial NUR im User-Mode löschen!
            if (!AppConfig.debugModeEnabled) {
                spec.clearPassword()
                password.fill('\u0000')
                secretKey.encoded.fill(0)
            }

            return ok
        } catch (e: Exception) {
            Log.e(TAG, "Error during in-place encryption: ", e)
            return false
        }
    }

    /**
     * Beispiel für eine Entschlüsselungsfunktion mit Logging.
     * Du musst diese anpassen, falls du eigene Header-Struktur hast!
     */
    fun decryptFile(context: Context, encryptedFile: File, password: CharArray, outputFile: File): Boolean {
        try {
            // Header lesen (salt & iv)
            val inputStream = FileInputStream(encryptedFile)
            val salt = ByteArray(16)
            val iv = ByteArray(12)
            if (inputStream.read(salt) != 16 || inputStream.read(iv) != 12) {
                inputStream.close()
                return false
            }
            val iterations = 100_000
            val keyLength = 256
            val spec = PBEKeySpec(password, salt, iterations, keyLength)
            val keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val secretKey = SecretKeySpec(keyFactory.generateSecret(spec).encoded, "AES")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

            CipherOutputStream(FileOutputStream(outputFile), cipher).use { out ->
                inputStream.use { inp ->
                    inp.copyTo(out, bufferSize = 4096)
                }
            }

            // Logging Hash nach Entschlüsselung (nur Debug Mode)
            if (AppConfig.debugModeEnabled) {
                val hash = DebugCryptoFunc.sha256(outputFile)
                DebugCryptoFunc.log(context, "Nach Entschlüsselung SHA256: $hash (Datei: ${outputFile.name})")
            }

            // Schlüsselmaterial NUR im User-Mode löschen!
            if (!AppConfig.debugModeEnabled) {
                spec.clearPassword()
                password.fill('\u0000')
                secretKey.encoded.fill(0)
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error during decryption: ", e)
            return false
        }
    }
    // DEBUG MODE: Testfunktion welche Datei lediglich verschlüsselt und nicht löscht!
    fun encryptFileInPlaceCustom(context: Context, inputFile: File, password: CharArray, outputFile: File): Boolean {
        val salt = ByteArray(16)
        val iv = ByteArray(12)
        SecureRandom().nextBytes(salt)
        SecureRandom().nextBytes(iv)
        val iterations = 100_000
        val keyLength = 256

        try {
            val spec = PBEKeySpec(password, salt, iterations, keyLength)
            val keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val secretKey = SecretKeySpec(keyFactory.generateSecret(spec).encoded, "AES")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)

            inputFile.inputStream().use { input ->
                outputFile.outputStream().use { output ->
                    output.write(salt)
                    output.write(iv)
                    CipherOutputStream(output, cipher).use { cipherOut ->
                        input.copyTo(cipherOut, bufferSize = 4096)
                    }
                }
            }

            // Debug: Keine Key-Löschung!
            return true
        } catch (e: Exception) {
            Log.e("CryptoUtils", "Error during debug encryption: ", e)
            return false
        }
    }

}
