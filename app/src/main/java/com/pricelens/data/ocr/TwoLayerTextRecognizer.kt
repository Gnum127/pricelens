package com.pricelens.data.ocr

import androidx.camera.core.ImageProxy
import com.pricelens.domain.model.OcrFrame

/** Оба движка получают один и тот же Bitmap: координаты токенов совпадают. */
class TwoLayerTextRecognizer(
    private val tesseract: TesseractOcr,
    private val rapidOcr: RapidOcr,
) : TextRecognizer {
    override fun recognize(image: ImageProxy): OcrFrame? {
        val bitmap = OcrImageDecoder.decode(image) ?: return null
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
