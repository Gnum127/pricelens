package com.pricelens.domain.model

sealed interface RawTextResult {
    data class Success(val text: String) : RawTextResult

    data object EmptyText : RawTextResult

    data object CameraError : RawTextResult

    data object PermissionDenied : RawTextResult
}
