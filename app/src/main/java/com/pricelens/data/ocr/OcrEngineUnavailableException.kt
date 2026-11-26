package com.pricelens.data.ocr

/** Обязательный OCR-движок недоступен: распознавание нельзя считать успешным. */
class OcrEngineUnavailableException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)
