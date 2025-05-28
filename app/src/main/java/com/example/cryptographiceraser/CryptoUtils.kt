package com.example.cryptographiceraser

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream
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

            // Write to a temporary file first
            val tempFile = File(file.parent, file.name + ".enc")
            file.inputStream().use { input ->
                tempFile.outputStream().use { output ->
                    output.write(salt)
                    output.write(iv)
                    CipherOutputStream(output, cipher).use { cipherOut ->
                        input.copyTo(cipherOut)
                    }
                }
            }
            // Replace original file atomically
            if (file.delete()) {
                if (tempFile.renameTo(file)) {
                    // Wipe key material from RAM
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
            return false
        }
    }

    /**
     * Retrieves a display name for a file from its SAF Uri.
     * Returns: filename or null
     */
    fun getFileNameFromUri(context: Context, uri: Uri): String? {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (it.moveToFirst() && nameIndex >= 0) it.getString(nameIndex) else null
        }
    }

    /**
     * For debugging only: Write a test dummy file to the app's Documents directory.
     * Call this from your Activity to verify write permissions and path!
     * Input: context
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



