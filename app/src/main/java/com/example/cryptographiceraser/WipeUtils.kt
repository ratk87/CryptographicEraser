package com.example.cryptographiceraser

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.util.Log
import java.io.File
import java.io.FileOutputStream

object WipeUtils {
    private const val TAG = "WipeUtils"

    /**
     * Überschreibt freien Speicherplatz in [directory] mit Zufallsdaten.
     * Logt:
     *  (1) Start des Wipes mit Pfad und verfügbarem Platz
     *  (2) Erstellung jeder Dummy-Datei
     *  (3) Am Ende: Anzahl der erstellten Dummy-Dateien und Gesamtgröße
     *  (4) Erfolgreiches Löschen aller Dummy-Dateien
     *  (5) Beenden des Wipes
     */
    fun wipeFreeSpaceInDirectory(context: Context, directory: File): Long {
        val wipeDir = File(directory, "wipe_tmp")
        if (!wipeDir.exists()) wipeDir.mkdirs()

        // (1) Startmeldung
        val totalSpace = directory.usableSpace
        Log.d(TAG, "Starting wipeFreeSpaceInDirectory on '${directory.absolutePath}', usableSpace=${formatGB(totalSpace)}")

        val buffer = ByteArray(1024 * 1024) // 1 MiB
        val rnd = java.security.SecureRandom()
        var fileIndex = 0
        var totalBytesWritten: Long = 0

        try {
            while (true) {
                val dummyFile = File(wipeDir, "wipe_dummy_$fileIndex.bin")
                fileIndex++
                // (2) Dummy-Datei wird angelegt
                Log.d(TAG, "Creating dummy file #$fileIndex: ${dummyFile.absolutePath}")
                FileOutputStream(dummyFile).use { out ->
                    while (true) {
                        rnd.nextBytes(buffer)
                        out.write(buffer)
                        totalBytesWritten += buffer.size
                        // begrenze jede Dummy-Datei auf 100 MiB - beliebig, hier eine mögliche Größe
                        if (dummyFile.length() > 100L * 1024 * 1024) break
                    }
                }
            }
        } catch (e: Exception) {
            // erwartet: kein Platz mehr
            Log.d(TAG, "Stopped writing dummy files (likely out of space): ${e.message}")
        }

        // (3) Zusammenfassung
        Log.d(TAG, "Wipe summary: created $fileIndex dummy files, totalBytesWritten=${formatGB(totalBytesWritten)}")

        // (4) Datei‐Cleanup
        val deletedCount = cleanWipeDummyFiles(directory)
        Log.d(TAG, "Deleted $deletedCount dummy files from '${wipeDir.absolutePath}'")

        // (5) Ende
        Log.d(TAG, "wipeFreeSpaceInDirectory completed on '${directory.absolutePath}'")

        return totalBytesWritten
    }

    /**
     * Löscht alle Dummy-Dateien in [directory]/wipe_tmp
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
     * Liefert Gesamt- und freien Speicherplatz des Verzeichnisses.
     */
    fun getStorageStats(directory: File): Pair<Long, Long> {
        val stat = StatFs(directory.absolutePath)
        val total = stat.blockCountLong * stat.blockSizeLong
        val free = stat.availableBlocksLong * stat.blockSizeLong
        return Pair(total, free)
    }

    /**
     * Wie [wipeFreeSpaceInDirectory], aber mit Feedback-Callback 0…100 %.
     * Logt am Anfang und am Ende.
     */
    fun wipeFreeSpaceWithFeedback(
        context: Context,
        directory: File,
        onProgress: (Int) -> Unit
    ) {
        Thread {
            val wipeDir = File(directory, "wipe_tmp")
            if (!wipeDir.exists()) wipeDir.mkdirs()

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
                            val percent = ((totalBytesWritten * 100) / totalSpace).toInt().coerceAtMost(100)
                            Handler(Looper.getMainLooper()).post { onProgress(percent) }
                            if (dummyFile.length() > 100L * 1024 * 1024) break
                        }
                    }
                }
            } catch (_: Exception) {
                // Platzende – hier normaler Abbruch
            }

            // letzte 100%-Meldung
            Handler(Looper.getMainLooper()).post { onProgress(100) }

            // (3) Zusammenfassung
            Log.d(TAG, "Feedback‐wipe summary: created $fileIndex dummy files, totalWritten=${formatGB(totalBytesWritten)}")

            // (4) Cleanup & Log
            val deletedCount = cleanWipeDummyFiles(directory)
            Log.d(TAG, "Deleted $deletedCount dummy files after feedback‐wipe in '${wipeDir.absolutePath}'")

            // (5) Ende
            Log.d(TAG, "wipeFreeSpaceWithFeedback completed on '${directory.absolutePath}'")
        }.start()
    }

    /** Hilfsfunktion: Bytes → GB-String */
    private fun formatGB(bytes: Long): String {
        val gb = bytes / 1_073_741_824.0
        return String.format("%.2f GB", gb)
    }
}
