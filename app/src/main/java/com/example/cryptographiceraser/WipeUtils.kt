package com.example.cryptographiceraser

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * Objekt mit Methoden zum Überschreiben freien Speicherplatzes
 * und Abfragen von Speicherstatistiken.
 */
object WipeUtils {
    /** Tag für Log-Ausgaben */
    private const val TAG = "WipeUtils"

    /**
     * Überschreibt den freien Speicher in [directory] mit Zufallsdaten.
     * Schritte:
     *  (1) Erstelle/wähle Verzeichnis "wipe_tmp".
     *  (2) Logge Start und verfügbaren Platz.
     *  (3) Schreibe fortlaufend 1 MiB-Blöcke, begrenze jede Dummy-Datei auf 100 MiB.
     *  (4) Fange Out-of-Space über Exception ab.
     *  (5) Logge Anzahl erstellter Dummy-Dateien und Gesamtbytes.
     *  (6) Lösche alle Dummy-Dateien (cleanWipeDummyFiles).
     *  (7) Logge Abschluss und Rückgabe der geschriebenen Byte-Anzahl.
     *
     * @param context Context für Log-Ausgaben (nicht zwingend benötigt)
     * @param directory Wurzelverzeichnis zum Überschreiben
     * @return Anzahl geschriebener Bytes (Summe aller Dateien)
     */
    fun wipeFreeSpaceInDirectory(context: Context, directory: File): Long {
        val wipeDir = File(directory, "wipe_tmp").apply { if (!exists()) mkdirs() }

        // (1) Startmeldung
        val totalSpace = directory.usableSpace
        Log.d(TAG, "Starting wipeFreeSpaceInDirectory on '${directory.absolutePath}', usableSpace=${formatGB(totalSpace)}")

        val buffer = ByteArray(1024 * 1024) // 1 MiB
        val rnd = java.security.SecureRandom()
        var fileIndex = 0
        var totalBytesWritten = 0L

        try {
            while (true) {
                // (2) Neue Dummy-Datei
                val dummyFile = File(wipeDir, "wipe_dummy_$fileIndex.bin")
                fileIndex++
                Log.d(TAG, "Creating dummy file #$fileIndex: ${dummyFile.absolutePath}")

                FileOutputStream(dummyFile).use { out ->
                    while (true) {
                        rnd.nextBytes(buffer)
                        out.write(buffer)
                        totalBytesWritten += buffer.size
                        // Begrenze Größe jeder Dummy-Datei
                        if (dummyFile.length() > 100L * 1024 * 1024) break
                    }
                }
            }
        } catch (e: Exception) {
            // (3) erwartet: kein Platz mehr
            Log.d(TAG, "Stopped writing dummy files (likely out of space): ${e.message}")
        }

        // (4) Zusammenfassung loggen
        Log.d(TAG, "Wipe summary: created $fileIndex dummy files, totalBytesWritten=${formatGB(totalBytesWritten)}")

        // (5) Cleanup und Log
        val deletedCount = cleanWipeDummyFiles(directory)
        Log.d(TAG, "Deleted $deletedCount dummy files from '${wipeDir.absolutePath}'")

        // (6) Abschluss
        Log.d(TAG, "wipeFreeSpaceInDirectory completed on '${directory.absolutePath}'")
        return totalBytesWritten
    }

    /**
     * Löscht alle Dummy-Dateien im Unterverzeichnis "wipe_tmp".
     * @param directory Wurzelverzeichnis
     * @return Anzahl gelöschter Dateien
     */
    fun cleanWipeDummyFiles(directory: File): Int {
        val wipeDir = File(directory, "wipe_tmp")
        var count = 0
        if (wipeDir.exists()) {
            wipeDir.listFiles()?.forEach { file ->
                if (file.delete()) count++
            }
            wipeDir.delete()
        }
        return count
    }

    /**
     * Liefert Gesamt- und freien Speicherplatz eines Verzeichnisses.
     * @param directory Verzeichnis
     * @return Pair(TotalBytes, FreeBytes)
     */
    fun getStorageStats(directory: File): Pair<Long, Long> {
        val stat = StatFs(directory.absolutePath)
        val total = stat.blockCountLong * stat.blockSizeLong
        val free  = stat.availableBlocksLong * stat.blockSizeLong
        return Pair(total, free)
    }

    /**
     * Überschreibt freien Speicher wie wipeFreeSpaceInDirectory,
     * jedoch mit Progress-Callback (0–100 %).
     *
     * @param context Context (für Log)
     * @param directory Wurzelverzeichnis
     * @param onProgress Callback für Prozent-Updates auf MainThread
     */
    fun wipeFreeSpaceWithFeedback(
        context: Context,
        directory: File,
        onProgress: (Int) -> Unit
    ) {
        Thread {
            val wipeDir = File(directory, "wipe_tmp").apply { if (!exists()) mkdirs() }
            val buffer = ByteArray(1024 * 1024)
            val rnd = java.security.SecureRandom()
            var fileIndex = 0
            var totalBytesWritten = 0L
            val totalSpace = directory.usableSpace

            // (1) Startmeldung
            Log.d(TAG, "Starting wipeFreeSpaceWithFeedback on '${directory.absolutePath}', usableSpace=${formatGB(totalSpace)}")

            try {
                while (true) {
                    val dummyFile = File(wipeDir, "wipe_dummy_$fileIndex.bin")
                    fileIndex++
                    Log.d(TAG, "Creating dummy file #$fileIndex for feedback: ${dummyFile.absolutePath}")

                    FileOutputStream(dummyFile).use { out ->
                        while (true) {
                            rnd.nextBytes(buffer)
                            out.write(buffer)
                            totalBytesWritten += buffer.size
                            // Fortschritt berechnen und MainThread-Callback
                            val percent = ((totalBytesWritten * 100) / totalSpace).toInt().coerceAtMost(100)
                            Handler(Looper.getMainLooper()).post { onProgress(percent) }
                            if (dummyFile.length() > 100L * 1024 * 1024) break
                        }
                    }
                }
            } catch (_: Exception) {
                // erwartet: Platzende
            }

            // letzte 100%-Meldung
            Handler(Looper.getMainLooper()).post { onProgress(100) }

            // (3) Zusammenfassung & Cleanup
            Log.d(TAG, "Feedback-wipe summary: created $fileIndex dummy files, totalWritten=${formatGB(totalBytesWritten)}")
            val deletedCount = cleanWipeDummyFiles(directory)
            Log.d(TAG, "Deleted $deletedCount dummy files after feedback-wipe in '${wipeDir.absolutePath}'")
            Log.d(TAG, "wipeFreeSpaceWithFeedback completed on '${directory.absolutePath}'")
        }.start()
    }

    /** Hilfsfunktion: Wandelt Byte-Anzahl in GB-String mit 2 Nachkommastellen um */
    private fun formatGB(bytes: Long): String {
        val gb = bytes / 1_073_741_824.0
        return String.format("%.2f GB", gb)
    }
}
