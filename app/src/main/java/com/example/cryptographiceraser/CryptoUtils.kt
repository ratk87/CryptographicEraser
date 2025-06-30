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

/**
 * Objekt mit kryptografischen Hilfsfunktionen für „In-Place“-Verschlüsselung.
 * Wählt je nach Dateigröße AES-GCM (bis 20 MiB) oder AES-CTR (darüber)
 * und schreibt Salt, IV und Ciphertext zurück in dieselbe Datei.
 */
object CryptoUtils {
    /** Tag für Log-Ausgaben */
    const val TAG = "CryptoUtils"

    /** Grenze in Byte: Dateien ≤20 MiB → GCM, >20 MiB → CTR */
    private const val SIZE_THRESHOLD = 20L * 1024 * 1024

    /**
     * Verschlüsselt die übergebene Datei „in place“:
     * 1. Loggt Passwort-Länge und Originalgröße.
     * 2. Liest gesamten Inhalt in ein ByteArray.
     * 3. Wählt je nach Größe AES-GCM oder AES-CTR.
     * 4. Loggt generiertes Salt, IV und resultierende Größe.
     * 5. Überschreibt dieselbe Datei mit [salt ‖ iv ‖ ciphertext].
     *
     * @param file Zu verschlüsselnde Datei
     * @param password Passwort als CharArray (wird nach Verwendung gelöscht)
     * @return true bei Erfolg, false bei Fehler (wird geloggt)
     */
    fun encryptFileInPlace(file: File, password: CharArray): Boolean {
        return try {
            // (1) Passwort-Länge loggen
            Log.d(TAG, "Password received from user (length=${password.size})")

            // (1.1) Original-Dateigröße loggen
            val originalSize = file.length()
            Log.d(TAG, "Original filesize of '${file.name}' is $originalSize bytes")

            // (2) Datei komplett einlesen
            val plain = file.readBytes()

            // (3) Modus wählen: GCM bis Grenze, sonst CTR
            val ciphered = if (originalSize <= SIZE_THRESHOLD) {
                encryptGcm(plain, password)
            } else {
                encryptCtr(plain, password)
            }

            // (4) Ergebnisgröße loggen
            Log.d(TAG, "New encrypted bytestream generated, filesize is ${ciphered.size} bytes")

            // (5) In-place zurückschreiben
            file.outputStream().use { it.write(ciphered) }

            true
        } catch (e: Exception) {
            Log.e(TAG, "In-place encryption failed", e)
            false
        }
    }

    /**
     * Hilfsfunktion: Verschlüsselt Daten mit AES-GCM.
     * Header: salt (16 B) ‖ iv (12 B) ‖ ciphertext + Tag.
     */
    private fun encryptGcm(plain: ByteArray, password: CharArray): ByteArray {
        // Salt und IV generieren
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv   = ByteArray(12).also { SecureRandom().nextBytes(it) }
        Log.d(TAG, "SALT generated (${salt.size} bytes): ${salt.joinToString(",")}")
        Log.d(TAG, "IV   generated (${iv.size} bytes): ${iv.joinToString(",")}")

        // Schlüsselableitung via PBKDF2
        val spec = PBEKeySpec(password, salt, 100_000, 256)
        val kf   = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val key  = SecretKeySpec(kf.generateSecret(spec).encoded, "AES")

        // Cipher initialisieren und verschlüsseln
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val ct = cipher.doFinal(plain)

        // Sensible Daten aus RAM löschen
        spec.clearPassword()
        password.fill('\u0000')
        key.encoded.fill(0)

        // Salt ‖ iv ‖ ciphertext zurückgeben
        return salt + iv + ct
    }

    /**
     * Hilfsfunktion: Verschlüsselt Daten mit AES-CTR.
     * Header: salt (16 B) ‖ iv (16 B) ‖ ciphertext.
     */
    private fun encryptCtr(plain: ByteArray, password: CharArray): ByteArray {
        // Salt und IV generieren
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv   = ByteArray(16).also { SecureRandom().nextBytes(it) }
        Log.d(TAG, "SALT generated (${salt.size} bytes): ${salt.joinToString(",")}")
        Log.d(TAG, "IV   generated (${iv.size} bytes): ${iv.joinToString(",")}")

        // Schlüsselableitung via PBKDF2
        val spec = PBEKeySpec(password, salt, 100_000, 256)
        val kf   = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val key  = SecretKeySpec(kf.generateSecret(spec).encoded, "AES")

        // Cipher initialisieren und verschlüsseln
        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))
        val ct = cipher.doFinal(plain)

        // Sensible Daten aus RAM löschen
        spec.clearPassword()
        password.fill('\u0000')
        key.encoded.fill(0)

        // Salt ‖ iv ‖ ciphertext zurückgeben
        return salt + iv + ct
    }
}
