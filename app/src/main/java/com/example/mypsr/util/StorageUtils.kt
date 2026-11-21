package com.example.mypsr.util

import android.content.Context
import android.os.StatFs

object StorageUtils {
    private const val MIN_REQUIRED_BYTES = 50 * 1024 * 1024L // 50 MB

    fun getAvailableStorageBytes(context: Context): Long {
        return try {
            val stat = StatFs(context.filesDir.absolutePath)
            stat.availableBytes
        } catch (_: Exception) {
            Long.MAX_VALUE
        }
    }

    fun isStorageAvailable(context: Context, minBytes: Long = MIN_REQUIRED_BYTES): Boolean {
        return getAvailableStorageBytes(context) >= minBytes
    }
}
