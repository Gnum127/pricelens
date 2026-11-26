package com.pricelens.domain.model

sealed interface OcrResult {
    data class Success(
        val parsed: ParsedPriceTag,
        val unitPrice: UnitPrice,
    ) : OcrResult

    data object EmptyText : OcrResult

    data class Unparseable(val rawText: String) : OcrResult

    data object CameraError : OcrResult

    data object OcrEngineError : OcrResult

    data object PermissionDenied : OcrResult
}
