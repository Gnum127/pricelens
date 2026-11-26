package com.pricelens.data.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.ImageFormat
import androidx.camera.core.ImageProxy

/** CameraX в ImageCapture по умолчанию отдаёт JPEG; оба OCR-движка работают с Bitmap. */
internal object OcrImageDecoder {
    fun decode(image: ImageProxy): Bitmap? {
        if (image.format != ImageFormat.JPEG || image.planes.isEmpty()) return null

        val buffer = image.planes[0].buffer.duplicate()
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null

        val rotation = image.imageInfo.rotationDegrees
        if (rotation == 0) return bitmap

        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            .also { if (it !== bitmap) bitmap.recycle() }
    }
}
