package com.example.cryptographiceraser

import android.util.Log
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object CryptoUtils {
    public const val TAG = "CryptoUtils"

    /** Schwellwert 20 MiB: bis dahin GCM, darüber CTR */
    private const val SIZE_THRESHOLD = 20L * 1024 * 1024

    /**
     * Verschlüsselt die Datei „in place“:
     * 1) komplette Original–Datei in ByteArray einlesen
     * 2) Salt + IV erzeugen und vor den Ciphertext setzen
     * 3) resultierende ByteArray zurück in dieselbe Datei schreiben
     *
     * Loggt:
     *  (1) Passwort-Länge
     *  (1.1) Original-Dateigröße
     *  (2) Salt + IV
     *  (3) neue Byte-Array-Größe
     */
    fun encryptFileInPlace(file: File, password: CharArray): Boolean {
        try {
            // (1) Passwort erhalten
            Log.d(TAG, "Password received from user (length=${password.size})")

            // (1.1) Original-Dateigröße ermitteln
            val originalSize = file.length()
            Log.d(TAG, "Original filesize of '${file.name}' is $originalSize bytes")

            // 1) gesamte Originaldatei lesen
            val plain = file.readBytes()

            // 2) je nach Dateigröße GCM oder CTR
            val ciphered: ByteArray = if (originalSize <= SIZE_THRESHOLD) {
                encryptGcm(plain, password)
            } else {
                encryptCtr(plain, password)
            }

            // (3) Neue verschlüsselte Byte-Array-Größe
            Log.d(TAG, "New encrypted bytestream generated, filesize is ${ciphered.size} bytes")

            // 3) überschreibe Datei mit [salt|iv|ciphertext]
            file.outputStream().use { it.write(ciphered) }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "In-place encryption failed", e)
            return false
        }
    }

    // --- Hilfsfunktion für AES-GCM ---
    private fun encryptGcm(plain: ByteArray, password: CharArray): ByteArray {
        // Salt (16 B) + IV (12 B)
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv   = ByteArray(12).also { SecureRandom().nextBytes(it) }

        // (2) Salt + IV generiert
        Log.d(TAG, "SALT generated (${salt.size} bytes): ${salt.joinToString(",")}")
        Log.d(TAG, "IV   generated (${iv.size} bytes): ${iv.joinToString(",")}")

        // Schlüsselableitung
        val spec = PBEKeySpec(password, salt, 100_000, 256)
        val kf   = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val key  = SecretKeySpec(kf.generateSecret(spec).encoded, "AES")

        // Cipher initialisieren
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))

        // Auth-Tag und ciphertext erzeugen
        val ct = cipher.doFinal(plain)

        // Passwort+Schlüsselmaterial sicher löschen
        spec.clearPassword()
        password.fill('\u0000')
        key.encoded.fill(0)

        // ByteArray = salt ‖ iv ‖ ciphertext
        return salt + iv + ct
    }

    // --- Hilfsfunktion für AES-CTR ---
    private fun encryptCtr(plain: ByteArray, password: CharArray): ByteArray {
        // Salt (16 B) + IV (16 B für CTR)
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv   = ByteArray(16).also { SecureRandom().nextBytes(it) }

        // (2) Salt + IV generiert
        Log.d(TAG, "SALT generated (${salt.size} bytes): ${salt.joinToString(",")}")
        Log.d(TAG, "IV   generated (${iv.size} bytes): ${iv.joinToString(",")}")

        // Schlüsselableitung
        val spec = PBEKeySpec(password, salt, 100_000, 256)
        val kf   = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val key  = SecretKeySpec(kf.generateSecret(spec).encoded, "AES")

        // Cipher initialisieren
        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))

        // ciphertext erzeugen (CTR liefert gleiche Länge wie plaintext)
        val ct = cipher.doFinal(plain)

        // Passwort+Schlüsselmaterial löschen
        spec.clearPassword()
        password.fill('\u0000')
        key.encoded.fill(0)

        // ByteArray = salt ‖ iv ‖ ciphertext
        return salt + iv + ct
    }
}
