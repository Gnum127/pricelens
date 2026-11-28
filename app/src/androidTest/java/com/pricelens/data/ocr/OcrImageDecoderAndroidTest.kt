package com.pricelens.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class OcrImageDecoderAndroidTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun jpegWithoutOrientationKeepsSize() {
        val photo = writeJpeg(width = 40, height = 20, orientation = ExifInterface.ORIENTATION_NORMAL)
        try {
            val decoded = OcrImageDecoder.decode(photo)!!
            assertEquals(40, decoded.width)
            assertEquals(20, decoded.height)
            assertTrue(isRed(decoded.getPixel(0, 0)))
        } finally {
            photo.delete()
        }
    }

    @Test
    fun rotation90MovesTopLeftPixelToTopRight() {
        val photo = writeJpeg(width = 40, height = 20, orientation = ExifInterface.ORIENTATION_ROTATE_90)
        try {
            val decoded = OcrImageDecoder.decode(photo)!!
            assertEquals(20, decoded.width)
            assertEquals(40, decoded.height)
            assertTrue(isRed(decoded.getPixel(decoded.width - 1, 0)))
        } finally {
            photo.delete()
        }
    }

    @Test
    fun rotation270MovesTopLeftPixelToBottomLeft() {
        val photo = writeJpeg(width = 40, height = 20, orientation = ExifInterface.ORIENTATION_ROTATE_270)
        try {
            val decoded = OcrImageDecoder.decode(photo)!!
            assertEquals(20, decoded.width)
            assertEquals(40, decoded.height)
            assertTrue(isRed(decoded.getPixel(0, decoded.height - 1)))
        } finally {
            photo.delete()
        }
    }

    @Test
    fun rotation180MovesCornerPixel() {
        val photo = writeJpeg(width = 40, height = 20, orientation = ExifInterface.ORIENTATION_ROTATE_180)
        try {
            val decoded = OcrImageDecoder.decode(photo)!!
            assertEquals(40, decoded.width)
            assertEquals(20, decoded.height)
            assertTrue(isRed(decoded.getPixel(decoded.width - 1, decoded.height - 1)))
        } finally {
            photo.delete()
        }
    }

    @Test
    fun brokenJpegReturnsNull() {
        val photo = File(context.cacheDir, "broken-${UUID.randomUUID()}.jpg")
        photo.writeText("not a jpeg")
        try {
            assertNull(OcrImageDecoder.decode(photo))
        } finally {
            photo.delete()
        }
    }

    private fun writeJpeg(width: Int, height: Int, orientation: Int): File {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixel(0, 0, Color.RED)
        val photo = File(context.cacheDir, "exif-${UUID.randomUUID()}.jpg")
        photo.outputStream().use { stream ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream))
        }
        ExifInterface(photo.path).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
            saveAttributes()
        }
        return photo
    }

    private fun isRed(pixel: Int): Boolean =
        Color.red(pixel) > 128 &&
            Color.red(pixel) > Color.green(pixel) + 80 &&
            Color.red(pixel) > Color.blue(pixel) + 80
}
