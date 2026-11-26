package com.pricelens.domain.model

sealed interface RawTextResult {
    data class Success(val frame: OcrFrame) : RawTextResult

    data object EmptyText : RawTextResult

    data object CameraError : RawTextResult

    data object OcrEngineError : RawTextResult

    data object PermissionDenied : RawTextResult
}
