package com.pricelens.domain.model

/** Результат одного кадра: оба OCR-слоя работают с одними и теми же координатами. */
data class OcrFrame(
    val width: Int,
    val height: Int,
    val tokens: List<OcrToken>,
)
