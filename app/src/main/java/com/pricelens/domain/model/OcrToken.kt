package com.pricelens.domain.model

enum class OcrSource {
    TESSERACT,
    RAPID_OCR,
}

data class OcrToken(
    val text: String,
    val box: OcrBox,
    val confidence: Float,
    val source: OcrSource,
)
