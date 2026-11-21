package com.example.mypsr.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.media.Image
import android.util.Base64
import com.example.mypsr.model.RedactionBox
import java.io.ByteArrayOutputStream

object ImageUtils {

    /**
     * Converts Image object from ImageReader to Bitmap, handling plane strides and padding.
     */
    fun imageToBitmap(image: Image): Bitmap? {
        return try {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * image.width

            val bitmap = Bitmap.createBitmap(
                image.width + rowPadding / pixelStride,
                image.height,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)

            // Crop out padding if necessary
            if (rowPadding > 0) {
                val croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
                bitmap.recycle()
                croppedBitmap
            } else {
                bitmap
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Applies privacy redaction boxes onto a copy of the given Bitmap.
     */
    fun applyRedactions(bitmap: Bitmap, redactions: List<RedactionBox>): Bitmap {
        if (redactions.isEmpty()) return bitmap

        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)
        val paint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val width = mutableBitmap.width.toFloat()
        val height = mutableBitmap.height.toFloat()

        redactions.forEach { box ->
            val left = (box.left * width).coerceIn(0f, width)
            val top = (box.top * height).coerceIn(0f, height)
            val right = (box.right * width).coerceIn(0f, width)
            val bottom = (box.bottom * height).coerceIn(0f, height)

            canvas.drawRect(left, top, right, bottom, paint)
        }

        return mutableBitmap
    }

    /**
     * Applies a solid blackout rect using raw pixel bounds.
     */
    fun applyBoundsRedaction(bitmap: Bitmap, boundsRect: Rect): Bitmap {
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)
        val paint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRect(boundsRect, paint)
        return mutableBitmap
    }

    /**
     * Compresses Bitmap into JPEG ByteArray with specified quality.
     */
    fun compressBitmapToJpeg(bitmap: Bitmap, quality: Int = 80): ByteArray {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        return outputStream.toByteArray()
    }

    /**
     * Converts JPEG bytes to Base64 data URI string.
     */
    fun encodeBase64Jpeg(jpegBytes: ByteArray): String {
        val base64 = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
        return "data:image/jpeg;base64,$base64"
    }
}
