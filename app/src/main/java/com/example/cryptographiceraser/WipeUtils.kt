package com.example.cryptographiceraser

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import android.os.StatFs

/**
 * Utility object for securely overwriting ("wiping") free space in app-accessible directories.
 * Handles both internal and external (SD card) app-specific storage.
 */
object WipeUtils {

    private const val TAG = "WipeUtils"

    /**
     * Securely overwrites all available free space in the given directory by writing
     * dummy files filled with random data until no space remains.
     *
     * Input: context (required for SecureRandom), directory (File)
     * Output: total number of bytes written (Int, for debugging)
     */
    fun wipeFreeSpaceInDirectory(context: Context, directory: File): Int {
        val wipeDir = File(directory, "wipe_tmp")
        if (!wipeDir.exists()) wipeDir.mkdirs()
        val buffer = ByteArray(1024 * 1024) // 1 MiB blocks
        val rnd = java.security.SecureRandom()
        var fileIndex = 0
        var totalBytesWritten = 0

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


    /**
     * Deletes all dummy files created during the wipe process in the specified directory.
     * Input: directory (File)
     * Output: number of files deleted (Int)
     */
    fun cleanWipeDummyFiles(directory: File): Int {
        val wipeDir = File(directory, "wipe_tmp")
        var count = 0
        if (wipeDir.exists()) {
            wipeDir.listFiles()?.forEach { file ->
                if (file.delete()) count++
            }
            wipeDir.delete() // delete the folder itself if empty
        }
        return count
    }


    /**
     * Wipes free space in both internal and external app-specific directories (if available).
     * Input: context
     * Output: Pair of total bytes written (internal, external)
     */
    fun wipeFreeSpaceAll(context: Context): Pair<Int, Int> {
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

    /**
     * Reports available free space in the given directory, in bytes.
     * Input: directory (File)
     * Output: available space (Long)
     */
    fun getAvailableSpace(directory: File): Long {
        return directory.usableSpace
    }

    /** Returns total and free space (bytes) for a directory */
    fun getStorageStats(directory: File): Pair<Long, Long> {
        val stat = StatFs(directory.absolutePath)
        val total = stat.blockCountLong * stat.blockSizeLong
        val free = stat.availableBlocksLong * stat.blockSizeLong
        return Pair(total, free)
    }
}
