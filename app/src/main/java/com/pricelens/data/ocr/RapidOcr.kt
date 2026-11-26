package com.pricelens.data.ocr

import android.content.Context
import android.graphics.Bitmap
import com.benjaminwan.ocrlibrary.OcrEngine
import com.pricelens.domain.model.OcrBox
import com.pricelens.domain.model.OcrSource
import com.pricelens.domain.model.OcrToken

/** Крупные цифры: RapidOCR читает рубли, которые Tesseract пропускает. */
class RapidOcr(context: Context) {
    private val engine = OcrEngine(context.applicationContext).apply {
        doAngle = false
        mostAngle = false
    }

    @Synchronized
    fun recognize(bitmap: Bitmap): List<OcrToken> {
        val boxImage = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        try {
            val result = engine.detect(bitmap, boxImage, maxOf(bitmap.width, bitmap.height))
            return result.textBlocks.map { block ->
                val xs = block.boxPoint.map { it.x }
                val ys = block.boxPoint.map { it.y }
                OcrToken(
                    text = block.text.trim(),
                    box = OcrBox(
                        left = xs.minOrElse(0),
                        top = ys.minOrElse(0),
                        right = xs.maxOrElse(0),
                        bottom = ys.maxOrElse(0),
                    ),
                    confidence = block.confidence(),
                    source = OcrSource.RAPID_OCR,
                )
            }.filter { it.text.isNotBlank() }
        } finally {
            boxImage.recycle()
        }
    }
}

private fun List<Int>.minOrElse(fallback: Int): Int = minOrNull() ?: fallback

private fun List<Int>.maxOrElse(fallback: Int): Int = maxOrNull() ?: fallback

private fun com.benjaminwan.ocrlibrary.TextBlock.confidence(): Float =
    charScores.takeIf { it.isNotEmpty() }?.average()?.toFloat() ?: boxScore
