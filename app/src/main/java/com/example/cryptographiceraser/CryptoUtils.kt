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
            if (file.delete()) {
                if (tempFile.renameTo(file)) {
                    // Schlüsselmaterial aus dem RAM entfernen
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
            Log.e(TAG, "Error during in-place encryption: ", e)
            return false
        }
    }
}
