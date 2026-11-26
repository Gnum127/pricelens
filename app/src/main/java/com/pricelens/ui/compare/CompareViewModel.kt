package com.pricelens.ui.compare

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pricelens.core.util.formatAmount
import com.pricelens.core.util.formatMoney
import com.pricelens.domain.model.OcrResult
import com.pricelens.domain.model.ParsedPriceTag
import com.pricelens.domain.model.ProductItem
import com.pricelens.domain.model.UnitPrice
import com.pricelens.domain.model.WeightUnit
import com.pricelens.domain.pricing.UnitPriceCalculator
import com.pricelens.domain.repository.PriceTagRepository
import com.pricelens.domain.usecase.AddItemUseCase
import com.pricelens.domain.usecase.CompareItemsUseCase
import com.pricelens.domain.usecase.RecognizePriceTagUseCase
import com.pricelens.domain.usecase.RemoveItemUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class CompareViewModel @Inject constructor(
    private val recognizePriceTag: RecognizePriceTagUseCase,
    private val addItem: AddItemUseCase,
    private val removeItemUseCase: RemoveItemUseCase,
    private val compareItems: CompareItemsUseCase,
    private val calculator: UnitPriceCalculator,
    private val repository: PriceTagRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CompareUiState())
    val uiState: StateFlow<CompareUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>(replay = 0, extraBufferCapacity = 7)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    private val invalidFields = mutableSetOf<String>()
    private var isLoaded = false
    private var canPersist = false
    private var captureJob: Job? = null

    init {
        viewModelScope.launch {
            val saved = try {
                repository.loadCurrent().also { canPersist = true }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                send(CompareMessage.StorageError)
                _uiState.update { it.copy(isPersistenceAvailable = false) }
                emptyList()
            }
            _uiState.update { state ->
                state.copy(
                    items = saved,
                    drafts = saved.associate { it.id to it.toDraft() },
                    cheapestId = compareItems(saved).cheapestId,
                    isLoading = false,
                )
            }
            isLoaded = true
            observeSaves(saved)
        }
    }

    private suspend fun observeSaves(initial: List<ProductItem>) {
        var lastSaved = initial
        _uiState
            .map { it.items }
            .distinctUntilChanged()
            .collect { items ->
                if (items == lastSaved || !canPersist) return@collect
                try {
                    repository.saveCurrent(items)
                    lastSaved = items
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    send(CompareMessage.StorageError)
                }
            }
    }

    fun onAction(action: UiAction) {
        if (!isLoaded) return
        when (action) {
            UiAction.AddClicked -> _uiState.update {
                it.copy(isCaptureOpen = true, isPermissionDenied = false)
            }
            UiAction.CaptureDismissed -> _uiState.update {
                it.copy(isCaptureOpen = false, isPermissionDenied = false)
            }
            UiAction.CameraBindFailed -> failCapture(CompareMessage.CameraError)
            UiAction.CaptureConfirmed -> capture()
            UiAction.PermissionDenied -> {
                // Отказ во время захвата отменяет его: результат больше не должен попасть в список.
                captureJob?.cancel()
                captureJob = null
                // Диалог остаётся открытым: пользователь видит подсказку про настройки.
                send(CompareMessage.PermissionDenied)
                _uiState.update { it.copy(isPermissionDenied = true, isBusy = false) }
            }
            is UiAction.NameChanged -> changeName(action.id, action.value)
            is UiAction.AmountChanged -> changeNumber(action.id, action.value, isAmount = true)
            is UiAction.PriceChanged -> changeNumber(action.id, action.value, isAmount = false)
            is UiAction.ItemRemoved -> removeItem(action.id)
        }
    }

    private fun capture() {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isBusy = true) }
        val job = viewModelScope.launch {
            try {
                val result = recognizePriceTag()
                // Отказ мог прийти, пока OCR висел: отменённый результат нельзя применять.
                if (!isActive) return@launch
                when (result) {
                    is OcrResult.Success -> {
                        val item = result.toItem(UUID.randomUUID().toString())
                        val updated = addItem(_uiState.value.items, item)
                        if (updated == null) {
                            failCapture(CompareMessage.LimitReached)
                        } else {
                            _uiState.update { state ->
                                state.copy(
                                    items = updated,
                                    drafts = state.drafts + (item.id to item.toDraft()),
                                    cheapestId = compareItems(updated).cheapestId,
                                    isCaptureOpen = false,
                                )
                            }
                        }
                    }

                    OcrResult.EmptyText -> failCapture(CompareMessage.EmptyText)
                    is OcrResult.Unparseable -> {
                        failCapture(CompareMessage.Unparseable(result.rawText))
                    }
                    OcrResult.CameraError -> failCapture(CompareMessage.CameraError)
                    OcrResult.OcrEngineError -> failCapture(CompareMessage.OcrEngineError)
                    OcrResult.PermissionDenied -> failCapture(CompareMessage.PermissionDenied)
                }
            } finally {
                // Отменённый захват не сбрасывает isBusy у нового захвата.
                if (isActive) _uiState.update { it.copy(isBusy = false) }
            }
        }
        captureJob = job
    }

    private fun failCapture(message: CompareMessage) {
        send(message)
        _uiState.update { it.copy(isCaptureOpen = false, isBusy = false) }
    }

    private fun changeName(id: String, value: String) {
        _uiState.update { state ->
            state.copy(
                items = state.items.replace(id) { it.copy(name = value) },
                drafts = state.drafts.with(id) { it.copy(name = value) },
            )
        }
    }

    private fun changeNumber(id: String, value: String, isAmount: Boolean) {
        val state = _uiState.value
        val original = state.items.firstOrNull { it.id == id } ?: return
        val draft = if (isAmount) {
            (state.drafts[id] ?: original.toDraft()).copy(amount = value)
        } else {
            (state.drafts[id] ?: original.toDraft()).copy(price = value)
        }

        // В поле amount изначально лежит подпись диапазона («230-240 г»): это не число,
        // но до первой правки веса расчёт должен идти от верхней границы диапазона.
        val amount = if (draft.amount == original.packageAmountLabel) {
            original.packageAmount
        } else {
            draft.amount.toDecimal()
        }
        val price = draft.price.toDecimal()
        val changedValue = if (isAmount) draft.amount else draft.price
        val parsedValue = if (isAmount) amount else price
        val fieldKey = id + if (isAmount) ":amount" else ":price"
        val isInvalid = changedValue.isNotBlank() &&
            (parsedValue == null || parsedValue <= BigDecimal.ZERO)
        if (isInvalid) {
            if (invalidFields.add(fieldKey)) send(CompareMessage.InvalidInput)
        } else {
            invalidFields.remove(fieldKey)
        }
        val unitPrice = if (amount != null && price != null && original.packageUnit != null) {
            calculator.calculate(
                ParsedPriceTag(
                    packageAmount = amount,
                    packageUnit = original.packageUnit,
                    price = price,
                ),
            )
        } else {
            null
        }
        // Невалидная или незавершённая правка не портит сохранённый товар.
        val updated = unitPrice?.let {
            original.copy(
                packageAmount = amount,
                // Подпись диапазона сбрасывает только ручная правка веса.
                packageAmountLabel = if (isAmount) null else original.packageAmountLabel,
                price = price,
                unitPrice = it,
            )
        }
        val items = state.items.replace(id) { updated ?: original }

        _uiState.update {
            it.copy(
                items = items,
                drafts = it.drafts + (id to draft),
                cheapestId = compareItems(items).cheapestId,
            )
        }
    }

    private fun removeItem(id: String) {
        invalidFields.remove("$id:amount")
        invalidFields.remove("$id:price")
        _uiState.update { state ->
            val items = removeItemUseCase(state.items, id)
            state.copy(
                items = items,
                drafts = state.drafts - id,
                cheapestId = compareItems(items).cheapestId,
            )
        }
    }

    private fun send(message: CompareMessage) {
        _events.tryEmit(UiEvent.Message(message))
    }

    private fun OcrResult.Success.toItem(id: String): ProductItem {
        val explicitPrice = parsed.explicitUnitPrice
        val explicitUnit = parsed.explicitUnitPriceUnit
        return ProductItem(
            id = id,
            name = parsed.name.orEmpty(),
            packageAmount = if (explicitPrice != null && explicitUnit != null) {
                BigDecimal("1000")
            } else {
                parsed.packageAmount
            },
            packageAmountLabel = parsed.packageAmountLabel,
            packageUnit = when (explicitUnit) {
                UnitPrice.Unit.KILOGRAM -> WeightUnit.GRAM
                UnitPrice.Unit.LITER -> WeightUnit.MILLILITER
                null -> parsed.packageUnit
            },
            price = explicitPrice ?: parsed.price,
            unitPrice = unitPrice,
            sourceText = parsed.sourceText,
        )
    }

    private fun ProductItem.toDraft() = ItemDraft(
        name = name,
        amount = packageAmountLabel ?: packageAmount?.let(::formatAmount).orEmpty(),
        price = price?.let(::formatMoney).orEmpty(),
    )

    private fun String.toDecimal(): BigDecimal? =
        takeIf { isNotBlank() }?.replace(',', '.')?.toBigDecimalOrNull()

    private fun List<ProductItem>.replace(id: String, transform: (ProductItem) -> ProductItem) =
        map { if (it.id == id) transform(it) else it }

    private fun Map<String, ItemDraft>.with(id: String, transform: (ItemDraft) -> ItemDraft) =
        this + (id to transform(this[id] ?: ItemDraft("", "", "")))
}
