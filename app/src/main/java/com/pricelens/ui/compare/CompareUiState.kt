package com.pricelens.ui.compare

import com.pricelens.domain.model.ManualUnit
import com.pricelens.domain.model.ProductItem

data class ItemDraft(
    val name: String,
    val amount: String,
    val price: String,
    val priceByCard: String = "",
)

enum class ManualField { NAME, AMOUNT, PRICE, PRICE_BY_CARD }

data class ManualEntryState(
    val draft: ItemDraft = ItemDraft("", "", "", ""),
    val unit: ManualUnit = ManualUnit.GRAM,
    /** Диалог открыт после неудачного распознавания: пользователю предлагают ввести данные. */
    val recognitionFailed: Boolean = false,
)

data class CompareUiState(
    val items: List<ProductItem> = emptyList(),
    val drafts: Map<String, ItemDraft> = emptyMap(),
    val cheapestId: String? = null,
    val isCaptureOpen: Boolean = false,
    val isPermissionDenied: Boolean = false,
    val isBusy: Boolean = false,
    val isLoading: Boolean = true,
    val manualEntry: ManualEntryState? = null,
)

sealed interface CompareMessage {
    data object CameraError : CompareMessage
    data object OcrEngineError : CompareMessage
    data object PermissionDenied : CompareMessage
    data object LimitReached : CompareMessage
    data object StorageError : CompareMessage
    data object InvalidInput : CompareMessage
}

sealed interface UiAction {
    data object AddClicked : UiAction
    data object ManualEntryClicked : UiAction
    data class ManualFieldChanged(val field: ManualField, val value: String) : UiAction
    data class ManualUnitChanged(val unit: ManualUnit) : UiAction
    data object ManualEntrySubmitted : UiAction
    data object ClearAll : UiAction
    data object ManualEntryDismissed : UiAction
    data object CaptureDismissed : UiAction
    data object CameraBindFailed : UiAction
    data object CaptureConfirmed : UiAction
    data object PermissionDenied : UiAction
    data class NameChanged(val id: String, val value: String) : UiAction
    data class AmountChanged(val id: String, val value: String) : UiAction
    data class PriceChanged(val id: String, val value: String) : UiAction
    data class PriceByCardChanged(val id: String, val value: String) : UiAction
    data class ItemRemoved(val id: String) : UiAction
}

sealed interface UiEvent {
    data class Message(val message: CompareMessage) : UiEvent
}
