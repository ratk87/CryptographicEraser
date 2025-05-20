package com.example.cryptographiceraser

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Objekt mit Hilfsfunktionen für:
 * - Ephemeral-Key-Generierung (AES-XTS-256 via PBKDF2)
 * - Datei-Verschlüsselung mit AES-XTS
 * - Freier Speicher-Wipe
 */
object CryptoUtils {

    /**
     * Funktion: generateEphemeralXtsKey
     * Input: password: CharArray
     * Output: Pair<SecretKey, ByteArray> (SecretKey, Salt)
     * Was?
     *   - NIST SP 800-88r1 §2.6: AES-XTS-256 Schlüssel ableiten
     *   - Salt (128 Bit) erzeugen für KDF
     *   - PBKDF2WithHmacSHA256 (100 000 Iterationen)
     *   - SecretKeySpec für AES erstellen
     *   - Passwort + Zwischendaten aus RAM löschen
     */
    fun generateEphemeralXtsKey(password: CharArray): Pair<SecretKey, ByteArray> {
        // Salt erzeugen (128 Bit)
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }

        // KDF-Parameter
        val iterations = 100_000
        val spec = PBEKeySpec(password, salt, iterations, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded

        // AES-XTS SecretKey
        val secretKey = SecretKeySpec(keyBytes, "AES")

        // Speicher säubern
        spec.clearPassword()
        password.fill('\u0000')
        keyBytes.fill(0)

        return Pair(secretKey, salt)
    }

    /**
     * Funktion: encryptFileWithXtsKey
     * Input:
     *   - input: File (zu verschlüsselnde Datei)
     *   - output: File (Ziel für Ciphertext)
     *   - key: SecretKey (AES-XTS Key)
     * Output: nichts (schreibt verschlüsselte Datei)
     * Was?
     *   - IV (128 Bit) generieren
     *   - Cipher „AES/XTS/NoPadding“ initialisieren
     *   - IV am Dateianfang schreiben
     *   - Input blockweise verschlüsseln → output
     */
    fun encryptFileWithXtsKey(input: File, output: File, key: SecretKey) {
        // IV erzeugen
        val iv = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val ivSpec = IvParameterSpec(iv)

        // Cipher initialisieren
        val cipher = Cipher.getInstance("AES/XTS/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, ivSpec)

        // Streams öffnen
        FileInputStream(input).use { fis ->
            FileOutputStream(output).use { fos ->
                fos.write(iv)  // IV voranstellen
                val buffer = ByteArray(4096)
                var read: Int
                while (fis.read(buffer).also { read = it } != -1) {
                    val ct = cipher.update(buffer, 0, read)
                    fos.write(ct)
                }
                fos.write(cipher.doFinal())
            }
        }
    }

    /**
     * Funktion: clearEphemeralKey
     * Input: key: SecretKey?
     * Output: nichts
     * Was?
     *   - Wenn SecretKeySpec: key.encoded mit Nullen überschreiben
     *   - Referenz in aufrufender Klasse verwerfen
     */
    fun clearEphemeralKey(key: SecretKey?) {
        if (key is SecretKeySpec) {
            key.encoded.fill(0)
        }
        // Variable in aufrufender Methode auf null setzen
    }

    /**
     * Funktion: wipeFreeSpace
     * Input: context: Context
     * Output: nichts
     * Was?
     *   - Legt Dummy-Dateien im Cache-Verzeichnis an
     *   - Füllt sie mit Zufallsblöcken, bis kein Platz mehr bleibt
     *   - Löscht anschließend alle Dummy-Dateien
     */
    fun wipeFreeSpace(context: Context) {
        val dir = context.cacheDir
        val rnd = SecureRandom()
        val buffer = ByteArray(1024 * 1024)  // 1 MiB-Blöcke

        var index = 0
        // 1. Freiraum füllen
        while (true) {
            val dummy = File(dir, "wipe_$index.bin")
            try {
                FileOutputStream(dummy).use { out ->
                    while (true) {
                        rnd.nextBytes(buffer)
                        out.write(buffer)
                    }
                }
            } catch (e: Exception) {
                // Kein Platz mehr
                break
            } finally {
                index++
            }
        }
        // 2. Dummy-Dateien löschen
        dir.listFiles { f -> f.name.startsWith("wipe_") }?.forEach { it.delete() }
    }
}
