package com.pricelens.data.ocr

import com.pricelens.domain.model.OcrFrame
import java.io.File

/** Оба движка получают один и тот же Bitmap: координаты токенов совпадают. */
class TwoLayerTextRecognizer(
    private val tesseract: TesseractOcr,
    private val rapidOcr: RapidOcr,
) : TextRecognizer {
    override fun recognize(photo: File): OcrFrame? {
        val bitmap = OcrImageDecoder.decode(photo) ?: return null
        return try {
            OcrFrame(
                width = bitmap.width,
                height = bitmap.height,
                tokens = tesseract.recognize(bitmap) + rapidOcr.recognize(bitmap),
            )
        } finally {
            bitmap.recycle()
        }
    }
}
