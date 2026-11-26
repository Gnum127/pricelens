package com.pricelens.data.ocr

import androidx.camera.core.ImageProxy
import com.pricelens.domain.model.OcrFrame

fun interface TextRecognizer {
    fun recognize(image: ImageProxy): OcrFrame?
}
