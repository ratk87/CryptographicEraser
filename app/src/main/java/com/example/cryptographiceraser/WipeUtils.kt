package com.example.cryptographiceraser

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import android.os.StatFs
import android.os.Handler
import android.os.Looper
import android.widget.Toast

object WipeUtils {

    private const val TAG = "WipeUtils"

    /**
     * Überschreibt freien Speicherplatz in jedem beliebigen Verzeichnis – mit MANAGE_EXTERNAL_STORAGE jetzt auch außerhalb des Sandboxes!
     */
    fun wipeFreeSpaceInDirectory(context: Context, directory: File): Long {
        val wipeDir = File(directory, "wipe_tmp")
        if (!wipeDir.exists()) wipeDir.mkdirs()
        val buffer = ByteArray(1024 * 1024) // 1 MiB blocks
        val rnd = java.security.SecureRandom()
        var fileIndex = 0
        var totalBytesWritten: Long = 0

        try {
            while (true) {
                val dummyFile = File(wipeDir, "wipe_dummy_$fileIndex.bin")
                fileIndex++
                FileOutputStream(dummyFile).use { out ->
                    while (true) {
                        rnd.nextBytes(buffer)
                        out.write(buffer)
                        totalBytesWritten += buffer.size
                        if (dummyFile.length() > 100 * 1024 * 1024) break
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Wiping stopped (likely out of space): ${e.message}")
        }

        Log.d(TAG, "Total bytes written during wipe: $totalBytesWritten")
        return totalBytesWritten
    }

    fun doubleWipeFreeSpace(context: Context, directory: File) {
        repeat(2) { _ ->
            wipeFreeSpaceInDirectory(context, directory)
            cleanWipeDummyFiles(directory)
        }
    }

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

    fun wipeFreeSpaceAll(context: Context): Pair<Long, Long> {
        val internalDir = context.filesDir
        val bytesInternal = wipeFreeSpaceInDirectory(context, internalDir)

        val externalDir = context.getExternalFilesDir(null)
        val bytesExternal = if (externalDir != null && Environment.getExternalStorageState(externalDir) == Environment.MEDIA_MOUNTED) {
            wipeFreeSpaceInDirectory(context, externalDir)
        } else {
            0
        }
        return Pair(bytesInternal, bytesExternal)
    }

    fun getAvailableSpace(directory: File): Long {
        return directory.usableSpace
    }

    fun getStorageStats(directory: File): Pair<Long, Long> {
        val stat = StatFs(directory.absolutePath)
        val total = stat.blockCountLong * stat.blockSizeLong
        val free = stat.availableBlocksLong * stat.blockSizeLong
        return Pair(total, free)
    }

    fun wipeFreeSpaceWithFeedback(context: Context, directory: File) {
        Thread {
            doubleWipeFreeSpace(context, directory)
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(
                    context,
                    "Wipe done! If storage is not freed immediately, restart the device.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }.start()
    }
}
