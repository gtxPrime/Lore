package com.gxdevs.athera.utils

import android.graphics.Bitmap
import android.util.LruCache

/** Singleton cache for video thumbnails to prevent re-decoding on scroll. */
object VideoThumbnailCache {
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    // Use 1/8th of the available memory for this memory cache.
    private val cacheSize = maxMemory / 8

    private val memoryCache: LruCache<String, Bitmap> =
            object : LruCache<String, Bitmap>(cacheSize) {
                override fun sizeOf(key: String, bitmap: Bitmap): Int {
                    // The cache size will be measured in kilobytes rather than number of items.
                    return bitmap.byteCount / 1024
                }
            }

    fun addBitmapToMemoryCache(key: String, bitmap: Bitmap) {
        if (getBitmapFromMemCache(key) == null) {
            memoryCache.put(key, bitmap)
        }
    }

    fun getBitmapFromMemCache(key: String): Bitmap? {
        return memoryCache.get(key)
    }
}


