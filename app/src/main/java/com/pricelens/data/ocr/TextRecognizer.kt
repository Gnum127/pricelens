package com.pricelens.data.ocr

import com.pricelens.domain.model.OcrFrame
import java.io.File

fun interface TextRecognizer {
    fun recognize(photo: File): OcrFrame?
}
