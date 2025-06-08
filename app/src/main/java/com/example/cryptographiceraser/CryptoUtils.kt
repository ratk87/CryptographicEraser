package com.example.cryptographiceraser

import android.content.Context
import android.util.Log
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object CryptoUtils {
    private const val TAG = "CryptoUtils"

    /** Schwellwert: 20 MB */
    private const val SIZE_THRESHOLD = 20L * 1024 * 1024

    /**
     * Verschlüsselt eine Datei „in place“.
     * ≤ 20 MB → AES-GCM, > 20 MB → AES-CTR.
     * Schreibt [salt|iv|ciphertext] in eine temporäre Datei und ersetzt atomar die Originaldatei.
     */
    fun encryptFileInPlace(context: Context, file: File, password: CharArray): Boolean {
        return if (file.length() <= SIZE_THRESHOLD) {
            encryptWithGcm(file, password)
        } else {
            encryptWithCtr(file, password)
        }
    }

    private fun encryptWithGcm(file: File, password: CharArray): Boolean {
        val salt = ByteArray(16)
        val iv   = ByteArray(12)
        SecureRandom().nextBytes(salt)
        SecureRandom().nextBytes(iv)
        val iterations = 100_000
        val keyLength  = 256

        return try {
            // --- Schlüsselableitung PBKDF2 ---
            val spec = PBEKeySpec(password, salt, iterations, keyLength)
            val kf   = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val keyBytes = kf.generateSecret(spec).encoded
            val secretKey = SecretKeySpec(keyBytes, "AES")

            // --- AES/GCM init ---
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))

            // --- Verschlüsseln in Temp-Datei ---
            val temp = File(file.parent, "${file.name}.enc")
            file.inputStream().use { inp ->
                temp.outputStream().use { out ->
                    out.write(salt)
                    out.write(iv)
                    CipherOutputStream(out, cipher).use { cos ->
                        inp.copyTo(cos, 4096)
                    }
                }
            }

            // --- Atomischer Tausch ---
            if (!file.delete() || !temp.renameTo(file)) {
                temp.delete()
                return false
            }

            // --- Schlüssel+Passwort löschen ---
            spec.clearPassword()
            password.fill('\u0000')
            keyBytes.fill(0)
            true
        } catch (e: Exception) {
            Log.e(TAG, "AES-GCM encryption failed", e)
            false
        }
    }

    private fun encryptWithCtr(file: File, password: CharArray): Boolean {
        val salt = ByteArray(16)
        val iv   = ByteArray(16)
        SecureRandom().nextBytes(salt)
        SecureRandom().nextBytes(iv)
        val iterations = 100_000
        val keyLength  = 256

        return try {
            // --- Schlüsselableitung PBKDF2 ---
            val spec = PBEKeySpec(password, salt, iterations, keyLength)
            val kf   = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val keyBytes = kf.generateSecret(spec).encoded
            val secretKey = SecretKeySpec(keyBytes, "AES")

            // --- AES/CTR init ---
            val cipher = Cipher.getInstance("AES/CTR/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, IvParameterSpec(iv))

            // --- Verschlüsseln in Temp-Datei ---
            val temp = File(file.parent, "${file.name}.enc")
            file.inputStream().use { inp ->
                temp.outputStream().use { out ->
                    out.write(salt)
                    out.write(iv)
                    CipherOutputStream(out, cipher).use { cos ->
                        inp.copyTo(cos, 4096)
                    }
                }
            }

            // --- Atomischer Tausch ---
            if (!file.delete() || !temp.renameTo(file)) {
                temp.delete()
                return false
            }

            // --- Schlüssel+Passwort löschen ---
            spec.clearPassword()
            password.fill('\u0000')
            keyBytes.fill(0)
            true
        } catch (e: Exception) {
            Log.e(TAG, "AES-CTR encryption failed", e)
            false
        }
    }
}
