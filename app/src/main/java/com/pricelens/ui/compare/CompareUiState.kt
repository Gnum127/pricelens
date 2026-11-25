package com.pricelens.ui.compare

import com.pricelens.domain.model.ProductItem

data class ItemDraft(
    val name: String,
    val amount: String,
    val price: String,
)

data class CompareUiState(
    val items: List<ProductItem> = emptyList(),
    val drafts: Map<String, ItemDraft> = emptyMap(),
    val cheapestId: String? = null,
    val isCaptureOpen: Boolean = false,
    val isBusy: Boolean = false,
    val isLoading: Boolean = true,
    val isPersistenceAvailable: Boolean = true,
)

sealed interface CompareMessage {
    data object EmptyText : CompareMessage
    data class Unparseable(val rawText: String) : CompareMessage
    data object CameraError : CompareMessage
    data object PermissionDenied : CompareMessage
    data object LimitReached : CompareMessage
    data object StorageError : CompareMessage
    data object InvalidInput : CompareMessage
}

sealed interface UiAction {
    data object AddClicked : UiAction
    data object CaptureDismissed : UiAction
    data object CameraBindFailed : UiAction
    data object CaptureConfirmed : UiAction
    data object PermissionDenied : UiAction
    data class NameChanged(val id: String, val value: String) : UiAction
    data class AmountChanged(val id: String, val value: String) : UiAction
    data class PriceChanged(val id: String, val value: String) : UiAction
    data class ItemRemoved(val id: String) : UiAction
}

sealed interface UiEvent {
    data class Message(val message: CompareMessage) : UiEvent
}
