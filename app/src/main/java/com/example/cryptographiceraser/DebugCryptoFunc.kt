package com.example.cryptographiceraser

import android.content.Context
import java.io.File
import java.io.FileWriter
import java.security.MessageDigest

object DebugCryptoFunc {
    private const val LOG_FILE = "debug_crypto_log.txt"

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun log(context: Context, message: String) {
        val logFile = File(context.getExternalFilesDir(null), LOG_FILE)
        FileWriter(logFile, true).use { fw ->
            fw.appendLine("[${System.currentTimeMillis()}] $message")
        }
    }
}