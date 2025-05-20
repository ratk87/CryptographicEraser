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
     * Encrypts a file from a SAF Uri using AES-GCM and PBKDF2.
     * Writes [salt | iv | ciphertext] to a new ".encrypted" file in the app's Documents directory.
     * The original file is NOT deleted (test mode).
     * Input: context, fileUri, password
     * Output: true if successful, false otherwise
     */
    fun encryptFileAndSaveCopy(context: Context, fileUri: Uri, password: CharArray): Boolean {
        // Prepare crypto parameters
        val salt = ByteArray(16)
        val iv = ByteArray(12)
        SecureRandom().nextBytes(salt)
        SecureRandom().nextBytes(iv)

        val iterations = 100_000
        val keyLength = 256

        try {
            // Derive encryption key using PBKDF2
            val spec = PBEKeySpec(password, salt, iterations, keyLength)
            val keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val secretKey = SecretKeySpec(keyFactory.generateSecret(spec).encoded, "AES")

            // Init AES-GCM cipher
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)

            // Get file name from Uri
            val name = getFileNameFromUri(context, fileUri) ?: "unknown"
            val encName = "$name.encrypted"
            Log.d(TAG, "Output encrypted file name: $encName")

            // Prepare output file in app's Documents directory
            val outDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            outDir?.mkdirs()
            val outFile = File(outDir, encName)
            Log.d(TAG, "Output path: ${outFile.absolutePath}")

            // Open streams and perform encryption
            context.contentResolver.openInputStream(fileUri).use { input ->
                FileOutputStream(outFile).use { fileOut ->
                    // Write salt and IV to start of file
                    fileOut.write(salt)
                    fileOut.write(iv)
                    // Encrypt and write the rest
                    CipherOutputStream(fileOut, cipher).use { cipherOut ->
                        input?.copyTo(cipherOut)
                    }
                }
            }

            Log.d(TAG, "Encrypted file written successfully!")

            // Securely wipe key material from memory
            spec.clearPassword()
            password.fill('\u0000')
            secretKey.encoded.fill(0)

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error during encryption: ", e)
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
