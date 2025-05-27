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
     * Encrypts the file IN PLACE via SAF-URI using AES-GCM and PBKDF2.
     * This method overwrites the original file with its encrypted content.
     *
     * Input: context, fileUri, password
     * Output: true if successful, false otherwise
     */
    fun encryptFileInPlace(context: Context, fileUri: Uri, password: CharArray): Boolean {
        // Generate salt and IV (nonce) for AES-GCM
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

            // Open streams for SAF-URI: use "wt" mode for truncating/overwriting
            val inputStream = context.contentResolver.openInputStream(fileUri)
            val outputStream = context.contentResolver.openOutputStream(fileUri, "wt")
            if (inputStream == null || outputStream == null) {
                Log.e(TAG, "Could not open file streams for in-place encryption.")
                return false
            }

            // Write salt and IV to start of file
            outputStream.write(salt)
            outputStream.write(iv)

            // Encrypt and write file content in-place (block-wise)
            CipherOutputStream(outputStream, cipher).use { cipherOut ->
                inputStream.copyTo(cipherOut)
            }

            inputStream.close()
            outputStream.close()

            // Securely wipe key material from memory
            spec.clearPassword()
            password.fill('\u0000')
            secretKey.encoded.fill(0)

            Log.d(TAG, "In-place encryption successful!")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error during in-place encryption: ", e)
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
