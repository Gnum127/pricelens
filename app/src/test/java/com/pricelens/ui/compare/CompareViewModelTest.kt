package com.pricelens.ui.compare

import com.google.common.truth.Truth.assertThat
import com.pricelens.domain.model.ProductItem
import com.pricelens.domain.model.RawTextResult
import com.pricelens.domain.model.UnitPrice
import com.pricelens.domain.model.UnitPriceSource
import com.pricelens.domain.model.WeightUnit
import com.pricelens.domain.parser.OcrTokenMerger
import com.pricelens.domain.parser.PriceTagParser
import com.pricelens.domain.pricing.UnitPriceCalculator
import com.pricelens.domain.repository.ImageTextProvider
import com.pricelens.domain.repository.PriceTagRepository
import com.pricelens.domain.usecase.AddItemUseCase
import com.pricelens.domain.usecase.CompareItemsUseCase
import com.pricelens.domain.usecase.CreateManualItemUseCase
import com.pricelens.domain.usecase.RecognizePriceTagUseCase
import com.pricelens.domain.usecase.RemoveItemUseCase
import com.pricelens.testing.singleLineFrame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.math.BigDecimal

private const val MESSAGE_TIMEOUT_MS = 1_000L

class CompareViewModelTest {
    private val provider = FakeImageTextProvider()
    private val repository = FakePriceTagRepository()
    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun addClickedOpensCapture() {
        val viewModel = viewModel()

        viewModel.onAction(UiAction.AddClicked)

        assertThat(viewModel.uiState.value.isCaptureOpen).isTrue()
    }

    @Test
    fun dismissClosesCapture() {
        val viewModel = viewModel()
        viewModel.onAction(UiAction.AddClicked)

        viewModel.onAction(UiAction.CaptureDismissed)

        assertThat(viewModel.uiState.value.isCaptureOpen).isFalse()
    }

    @Test
    fun manualEntryButtonOpensEmptyDialog() {
        val viewModel = viewModel()

        viewModel.onAction(UiAction.ManualEntryClicked)

        assertThat(viewModel.uiState.value.manualEntry)
            .isEqualTo(ManualEntryState(ItemDraft("", "", "", ""), recognitionFailed = false))
        assertThat(viewModel.uiState.value.isCaptureOpen).isFalse()
    }

    @Test
    fun manualFieldsAreStoredInDialogState() {
        val viewModel = viewModel()
        viewModel.onAction(UiAction.ManualEntryClicked)

        viewModel.onAction(UiAction.ManualFieldChanged(ManualField.NAME, "Молоко 1 л"))
        viewModel.onAction(UiAction.ManualFieldChanged(ManualField.AMOUNT, "950 мл"))
        viewModel.onAction(UiAction.ManualFieldChanged(ManualField.PRICE, "119"))
        viewModel.onAction(UiAction.ManualFieldChanged(ManualField.PRICE_BY_CARD, "100"))

        assertThat(viewModel.uiState.value.manualEntry?.draft).isEqualTo(
            ItemDraft("Молоко 1 л", "950 мл", "119", "100"),
        )
    }

    @Test
    fun manualEntryAddsItemWithCalculatedPriceAndClosesDialog() {
        val viewModel = viewModel()
        viewModel.onAction(UiAction.ManualEntryClicked)
        fillManualEntry(viewModel, name = "Творог", amount = "350 г", price = "240")

        viewModel.onAction(UiAction.ManualEntrySubmitted)
        advance()

        val state = viewModel.uiState.value
        assertThat(state.manualEntry).isNull()
        assertThat(state.items).hasSize(1)
        val item = state.items.single()
        assertThat(item.name).isEqualTo("Творог")
        assertThat(item.unitPrice.value.compareTo(BigDecimal("685.71"))).isEqualTo(0)
        assertThat(state.cheapestId).isEqualTo(item.id)
        assertThat(state.drafts[item.id]?.amount).isEqualTo("350")
        assertThat(repository.items).isEqualTo(state.items)
    }

    @Test
    fun manualEntryRejectsInvalidNumbersAndKeepsDialogOpen() {
        val viewModel = viewModel()
        viewModel.onAction(UiAction.ManualEntryClicked)
        fillManualEntry(viewModel, name = "Творог", amount = "350 г", price = "abc")

        val event = expectMessage(viewModel) { viewModel.onAction(UiAction.ManualEntrySubmitted) }

        assertThat(event).isEqualTo(UiEvent.Message(CompareMessage.InvalidInput))
        assertThat(viewModel.uiState.value.manualEntry?.draft).isEqualTo(
            ItemDraft("Творог", "350 г", "abc", ""),
        )
        assertThat(viewModel.uiState.value.items).isEmpty()
    }

    @Test
    fun manualEntryDismissDoesNotAddItem() {
        val viewModel = viewModel()
        viewModel.onAction(UiAction.ManualEntryClicked)
        fillManualEntry(viewModel, name = "Творог", amount = "350 г", price = "240")

        viewModel.onAction(UiAction.ManualEntryDismissed)

        assertThat(viewModel.uiState.value.manualEntry).isNull()
        assertThat(viewModel.uiState.value.items).isEmpty()
    }

    @Test
    fun manualEntryAtLimitIsRejected() {
        repository.items = (1..5).map { product("item$it", "Товар $it", "350", "240", "685.71") }
        val viewModel = viewModel()

        val event = expectMessage(viewModel) { viewModel.onAction(UiAction.ManualEntryClicked) }

        assertThat(event).isEqualTo(UiEvent.Message(CompareMessage.LimitReached))
        assertThat(viewModel.uiState.value.manualEntry).isNull()
        assertThat(viewModel.uiState.value.items).hasSize(5)
    }

    @Test
    fun manualEntrySubmitRechecksLimitAfterAnotherCapture() {
        repository.items = (1..4).map { product("item$it", "Товар $it", "350", "240", "685.71") }
        val viewModel = viewModel()
        viewModel.onAction(UiAction.ManualEntryClicked)
        fillManualEntry(viewModel, name = "Пятый", amount = "350 г", price = "240")
        viewModel.onAction(UiAction.CaptureConfirmed)
        advance()
        assertThat(viewModel.uiState.value.items).hasSize(5)

        val event = expectMessage(viewModel) { viewModel.onAction(UiAction.ManualEntrySubmitted) }

        assertThat(event).isEqualTo(UiEvent.Message(CompareMessage.LimitReached))
        assertThat(viewModel.uiState.value.items).hasSize(5)
        assertThat(viewModel.uiState.value.manualEntry?.draft)
            .isEqualTo(ItemDraft("Пятый", "350 г", "240", ""))
    }

    @Test
    fun successfulCaptureAddsItemWithCalculatedUnitPriceAndClosesDialog() {
        val viewModel = viewModel()
        viewModel.onAction(UiAction.AddClicked)

        viewModel.onAction(UiAction.CaptureConfirmed)
        advance()

        val state = viewModel.uiState.value
        assertThat(state.items).hasSize(1)
        val item = state.items.single()
        assertThat(item.unitPrice.value.compareTo(BigDecimal("685.71"))).isEqualTo(0)
        assertThat(state.cheapestId).isEqualTo(item.id)
        assertThat(state.drafts[item.id]?.amount).isEqualTo("350")
        assertThat(state.isBusy).isFalse()
        assertThat(state.isCaptureOpen).isFalse()
    }

    @Test
    fun explicitKilogramPriceGetsEditableBaseAmountAndPrice() {
        provider.result = textResult("Цена за 1 кг: 685,71 ₽")
        val viewModel = viewModel()

        viewModel.onAction(UiAction.CaptureConfirmed)
        advance()

        val item = viewModel.uiState.value.items.single()
        assertThat(item.packageAmount?.compareTo(BigDecimal("1000"))).isEqualTo(0)
        assertThat(item.packageUnit).isEqualTo(WeightUnit.GRAM)
        assertThat(item.unitPrice.displayUnit).isEqualTo(UnitPrice.Unit.KILOGRAM)
        assertThat(item.price?.compareTo(BigDecimal("685.71"))).isEqualTo(0)
        assertThat(viewModel.uiState.value.drafts[item.id]?.amount).isEqualTo("1000")

        viewModel.onAction(UiAction.AmountChanged(item.id, "700"))

        // Явная цена за 1 кг остаётся авторитетной: пересчёт из веса её не заменяет.
        assertThat(
            viewModel.uiState.value.items.single().unitPrice.value.compareTo(BigDecimal("685.71")),
        ).isEqualTo(0)
    }

    @Test
    fun explicitLiterPriceGetsEditableBaseAmountAndPrice() {
        provider.result = textResult("Цена за 1 л: 129,90 ₽")
        val viewModel = viewModel()

        viewModel.onAction(UiAction.CaptureConfirmed)
        advance()

        val item = viewModel.uiState.value.items.single()
        assertThat(item.packageAmount?.compareTo(BigDecimal("1000"))).isEqualTo(0)
        assertThat(item.packageUnit).isEqualTo(WeightUnit.MILLILITER)
        assertThat(item.unitPrice.displayUnit).isEqualTo(UnitPrice.Unit.LITER)
        assertThat(viewModel.uiState.value.drafts[item.id]?.amount).isEqualTo("1000")

        viewModel.onAction(UiAction.AmountChanged(item.id, "800"))

        // Явная цена за 1 л остаётся авторитетной: пересчёт из объёма её не заменяет.
        assertThat(
            viewModel.uiState.value.items.single().unitPrice.value.compareTo(BigDecimal("129.90")),
        ).isEqualTo(0)
        assertThat(viewModel.uiState.value.items.single().unitPriceSource)
            .isEqualTo(UnitPriceSource.EXPLICIT)
    }

    @Test
    fun emptyTextOpensManualEntryAndAddsNothing() {
        provider.result = RawTextResult.EmptyText
        val viewModel = viewModel()

        val messages = messagesDuring(viewModel) { viewModel.onAction(UiAction.CaptureConfirmed) }

        assertThat(messages).isEmpty()
        assertThat(viewModel.uiState.value.items).isEmpty()
        assertThat(viewModel.uiState.value.isCaptureOpen).isFalse()
        assertThat(viewModel.uiState.value.isBusy).isFalse()
        assertThat(viewModel.uiState.value.manualEntry?.recognitionFailed).isTrue()
    }

    @Test
    fun unparseableTextOpensManualEntryWithoutSnackbar() {
        provider.result = textResult("непонятный ценник")
        val viewModel = viewModel()

        val messages = messagesDuring(viewModel) { viewModel.onAction(UiAction.CaptureConfirmed) }

        assertThat(messages).isEmpty()
        assertThat(viewModel.uiState.value.isCaptureOpen).isFalse()
        assertThat(viewModel.uiState.value.isBusy).isFalse()
        assertThat(viewModel.uiState.value.manualEntry?.recognitionFailed).isTrue()
        assertThat(viewModel.uiState.value.manualEntry?.draft).isEqualTo(ItemDraft("", "", "", ""))
    }

    @Test
    fun failedRecognitionAtItemLimitDoesNotOpenManualEntry() {
        repository.items = (1..5).map { product("item$it", "Товар $it", "350", "240", "685.71") }
        provider.result = RawTextResult.EmptyText
        val viewModel = viewModel()

        val messages = messagesDuring(viewModel) { viewModel.onAction(UiAction.CaptureConfirmed) }

        assertThat(messages).isEmpty()
        assertThat(viewModel.uiState.value.manualEntry).isNull()
        assertThat(viewModel.uiState.value.isCaptureOpen).isFalse()
        assertThat(viewModel.uiState.value.isBusy).isFalse()
        assertThat(viewModel.uiState.value.items).hasSize(5)
    }

    @Test
    fun unexpectedCaptureFailureClosesCaptureAndReportsCameraError() {
        provider.failure = IllegalStateException("camera exploded")
        val viewModel = viewModel()
        viewModel.onAction(UiAction.AddClicked)

        val event = expectMessage(viewModel) { viewModel.onAction(UiAction.CaptureConfirmed) }

        assertThat(event).isEqualTo(UiEvent.Message(CompareMessage.CameraError))
        assertThat(viewModel.uiState.value.isCaptureOpen).isFalse()
        assertThat(viewModel.uiState.value.isBusy).isFalse()
    }

    @Test
    fun cameraErrorEmitsMessage() {
        provider.result = RawTextResult.CameraError
        val viewModel = viewModel()

        val event = expectMessage(viewModel) { viewModel.onAction(UiAction.CaptureConfirmed) }

        assertThat(event).isEqualTo(UiEvent.Message(CompareMessage.CameraError))
    }

    @Test
    fun ocrEngineErrorEmitsMessage() {
        provider.result = RawTextResult.OcrEngineError
        val viewModel = viewModel()

        val event = expectMessage(viewModel) { viewModel.onAction(UiAction.CaptureConfirmed) }

        assertThat(event).isEqualTo(UiEvent.Message(CompareMessage.OcrEngineError))
        assertThat(viewModel.uiState.value.items).isEmpty()
    }

    @Test
    fun ocrPermissionDeniedEmitsMessageAndClosesCapture() {
        provider.result = RawTextResult.PermissionDenied
        val viewModel = viewModel()
        viewModel.onAction(UiAction.AddClicked)

        val event = expectMessage(viewModel) { viewModel.onAction(UiAction.CaptureConfirmed) }

        assertThat(event).isEqualTo(UiEvent.Message(CompareMessage.PermissionDenied))
        assertThat(viewModel.uiState.value.items).isEmpty()
        assertThat(viewModel.uiState.value.isCaptureOpen).isFalse()
        assertThat(provider.readCalls).isEqualTo(1)
    }

    @Test
    fun cameraBindFailureEmitsMessageAndClosesCapture() {
        val viewModel = viewModel()
        viewModel.onAction(UiAction.AddClicked)

        val event = expectMessage(viewModel) { viewModel.onAction(UiAction.CameraBindFailed) }

        assertThat(event).isEqualTo(UiEvent.Message(CompareMessage.CameraError))
        assertThat(viewModel.uiState.value.isCaptureOpen).isFalse()
    }

    @Test
    fun sixthItemIsRejectedAndStateUnchanged() {
        val viewModel = viewModel()
        repeat(5) {
            viewModel.onAction(UiAction.CaptureConfirmed)
            advance()
        }
        assertThat(viewModel.uiState.value.items).hasSize(5)
        val before = viewModel.uiState.value

        val event = expectMessage(viewModel) { viewModel.onAction(UiAction.CaptureConfirmed) }

        assertThat(event).isEqualTo(UiEvent.Message(CompareMessage.LimitReached))
        val after = viewModel.uiState.value
        assertThat(after.items).isEqualTo(before.items)
        assertThat(after.drafts).isEqualTo(before.drafts)
        assertThat(after.cheapestId).isEqualTo(before.cheapestId)
    }

    @Test
    fun editingAmountRecalculatesUnitPriceAndKeepsRawDraft() {
        val viewModel = viewModel()
        viewModel.onAction(UiAction.CaptureConfirmed)
        advance()
        val id = viewModel.uiState.value.items.single().id

        viewModel.onAction(UiAction.AmountChanged(id, "700"))

        val state = viewModel.uiState.value
        assertThat(state.items.single().unitPrice.value.compareTo(BigDecimal("342.86"))).isEqualTo(0)
        assertThat(state.drafts[id]?.amount).isEqualTo("700")
    }

    @Test
    fun editingPriceRecalculatesUnitPriceAndKeepsRawDraft() {
        val viewModel = viewModel()
        viewModel.onAction(UiAction.CaptureConfirmed)
        advance()
        val id = viewModel.uiState.value.items.single().id

        viewModel.onAction(UiAction.PriceChanged(id, "288"))

        val state = viewModel.uiState.value
        assertThat(state.items.single().unitPrice.value.compareTo(BigDecimal("822.86"))).isEqualTo(0)
        assertThat(state.drafts[id]?.price).isEqualTo("288")
    }

    @Test
    fun editingPriceUpdatesCheapest() {
        val viewModel = viewModel()
        viewModel.onAction(UiAction.CaptureConfirmed)
        advance()
        val firstId = viewModel.uiState.value.items.single().id
        provider.result = textResult("320 г 212 ₽")
        viewModel.onAction(UiAction.CaptureConfirmed)
        advance()
        assertThat(viewModel.uiState.value.cheapestId).isNotEqualTo(firstId)

        viewModel.onAction(UiAction.PriceChanged(firstId, "200"))

        assertThat(viewModel.uiState.value.cheapestId).isEqualTo(firstId)
        assertThat(
            viewModel.uiState.value.items.first { it.id == firstId }
                .unitPrice.value.compareTo(BigDecimal("571.43")),
        ).isEqualTo(0)
    }

    @Test
    fun twoPricesAreRecognizedAndBothDraftsAreEditable() {
        provider.result = textResult("200 г 270 ₽ 243 ₽ по карте")
        val viewModel = viewModel()
        viewModel.onAction(UiAction.CaptureConfirmed)
        advance()
        val id = viewModel.uiState.value.items.single().id
        val item = viewModel.uiState.value.items.single()

        assertDecimal(item.price, "270")
        assertDecimal(item.priceByCard, "243")
        assertDecimal(item.unitPrice.value, "1215.00")
        assertThat(viewModel.uiState.value.drafts[id]?.priceByCard).isEqualTo("243,00")

        viewModel.onAction(UiAction.PriceByCardChanged(id, "253"))

        assertDecimal(viewModel.uiState.value.items.single().priceByCard, "253")
        assertDecimal(viewModel.uiState.value.items.single().unitPrice.value, "1265.00")
        assertThat(viewModel.uiState.value.drafts[id]?.priceByCard).isEqualTo("253")
    }

    @Test
    fun editingRegularPriceDoesNotOverrideLowerCardPrice() {
        provider.result = textResult("200 г 270 ₽ 243 ₽ по карте")
        val viewModel = viewModel()
        viewModel.onAction(UiAction.CaptureConfirmed)
        advance()
        val id = viewModel.uiState.value.items.single().id

        viewModel.onAction(UiAction.PriceChanged(id, "250"))

        val item = viewModel.uiState.value.items.single()
        assertDecimal(item.price, "250")
        assertDecimal(item.priceByCard, "243")
        assertDecimal(item.unitPrice.value, "1215.00")
    }

    @Test
    fun invalidCardPriceDoesNotChangeItem() {
        provider.result = textResult("200 г 270 ₽ 243 ₽ по карте")
        val viewModel = viewModel()
        viewModel.onAction(UiAction.CaptureConfirmed)
        advance()
        val item = viewModel.uiState.value.items.single()

        val event = expectMessage(viewModel) {
            viewModel.onAction(UiAction.PriceByCardChanged(item.id, "abc"))
        }

        assertThat(event).isEqualTo(UiEvent.Message(CompareMessage.InvalidInput))
        assertThat(viewModel.uiState.value.items.single()).isEqualTo(item)
        assertThat(viewModel.uiState.value.drafts[item.id]?.priceByCard).isEqualTo("abc")
    }

    @Test
    fun explicitUnitPriceSurvivesManualEdits() {
        provider.result = textResult("цена за 1 кг: 685,71 ₽\n350 г 240 ₽")
        val viewModel = viewModel()
        viewModel.onAction(UiAction.CaptureConfirmed)
        advance()
        val id = viewModel.uiState.value.items.single().id

        viewModel.onAction(UiAction.PriceChanged(id, "200"))

        val item = viewModel.uiState.value.items.single()
        assertDecimal(item.unitPrice.value, "685.71")
        assertThat(item.unitPriceSource).isEqualTo(UnitPriceSource.EXPLICIT)
        assertDecimal(item.price, "200")
    }

    @Test
    fun clearingCardPriceRemovesItAndRecalculatesFromRegularPrice() {
        provider.result = textResult("200 г 270 ₽ 243 ₽ по карте")
        val viewModel = viewModel()
        viewModel.onAction(UiAction.CaptureConfirmed)
        advance()
        val id = viewModel.uiState.value.items.single().id

        viewModel.onAction(UiAction.PriceByCardChanged(id, ""))

        val item = viewModel.uiState.value.items.single()
        assertDecimal(item.price, "270")
        assertThat(item.priceByCard).isNull()
        assertDecimal(item.unitPrice.value, "1350.00")
    }

    @Test
    fun clearingRegularPriceKeepsCardPriceForComparison() {
        provider.result = textResult("200 г 270 ₽ 243 ₽ по карте")
        val viewModel = viewModel()
        viewModel.onAction(UiAction.CaptureConfirmed)
        advance()
        val id = viewModel.uiState.value.items.single().id

        viewModel.onAction(UiAction.PriceChanged(id, ""))

        val item = viewModel.uiState.value.items.single()
        assertThat(item.price).isNull()
        assertDecimal(item.priceByCard, "243")
        assertDecimal(item.unitPrice.value, "1215.00")
    }

    @Test
    fun invalidDraftInAnotherFieldDoesNotEraseValidPrice() {
        provider.result = textResult("200 г 270 ₽ 243 ₽ по карте")
        val viewModel = viewModel()
        viewModel.onAction(UiAction.CaptureConfirmed)
        advance()
        val id = viewModel.uiState.value.items.single().id
        viewModel.onAction(UiAction.PriceChanged(id, "abc"))

        viewModel.onAction(UiAction.PriceByCardChanged(id, "253"))

        val item = viewModel.uiState.value.items.single()
        assertDecimal(item.price, "270")
        assertDecimal(item.priceByCard, "253")
        assertDecimal(item.unitPrice.value, "1265.00")
    }

    @Test
    fun cheapestIdFollowsCardPriceEditAndItsClearing() {
        repository.items = listOf(
            product("a", "Печенье A", "200", "270", "1350.00"),
            product("b", "Печенье B", "200", "260", "1300.00"),
        )
        val viewModel = viewModel()
        assertThat(viewModel.uiState.value.cheapestId).isEqualTo("b")

        viewModel.onAction(UiAction.PriceByCardChanged("a", "240"))
        assertThat(viewModel.uiState.value.cheapestId).isEqualTo("a")

        viewModel.onAction(UiAction.PriceByCardChanged("a", ""))
        assertThat(viewModel.uiState.value.cheapestId).isEqualTo("b")
    }

    @ParameterizedTest
    @ValueSource(strings = ["0", "-1", "."])
    fun invalidCardPriceIsRejected(value: String) {
        provider.result = textResult("200 г 270 ₽ 243 ₽ по карте")
        val viewModel = viewModel()
        viewModel.onAction(UiAction.CaptureConfirmed)
        advance()
        val item = viewModel.uiState.value.items.single()

        val event = expectMessage(viewModel) {
            viewModel.onAction(UiAction.PriceByCardChanged(item.id, value))
        }

        assertThat(event).isEqualTo(UiEvent.Message(CompareMessage.InvalidInput))
        assertThat(viewModel.uiState.value.items.single()).isEqualTo(item)
        assertThat(viewModel.uiState.value.drafts[item.id]?.priceByCard).isEqualTo(value)
    }

    @Test
    fun invalidInputIsReportedForRegularAndCardPriceSeparately() {
        provider.result = textResult("200 г 270 ₽ 243 ₽ по карте")
        val viewModel = viewModel()
        viewModel.onAction(UiAction.CaptureConfirmed)
        advance()
        val id = viewModel.uiState.value.items.single().id

        val messages = messagesDuring(viewModel) {
            viewModel.onAction(UiAction.PriceChanged(id, "abc"))
            viewModel.onAction(UiAction.PriceByCardChanged(id, "abc"))
        }

        assertThat(messages).containsExactly(
            UiEvent.Message(CompareMessage.InvalidInput),
            UiEvent.Message(CompareMessage.InvalidInput),
        )
    }

    @Test
    fun invalidCardKeyIsClearedWhenItemIsRemoved() {
        provider.result = textResult("200 г 270 ₽ 243 ₽ по карте")
        val viewModel = viewModel()
        viewModel.onAction(UiAction.CaptureConfirmed)
        advance()
        val firstId = viewModel.uiState.value.items.single().id
        viewModel.onAction(UiAction.PriceByCardChanged(firstId, "abc"))
        viewModel.onAction(UiAction.ItemRemoved(firstId))
        viewModel.onAction(UiAction.CaptureConfirmed)
        advance()
        val secondId = viewModel.uiState.value.items.single().id

        val event = expectMessage(viewModel) {
            viewModel.onAction(UiAction.PriceByCardChanged(secondId, "abc"))
        }

        assertThat(event).isEqualTo(UiEvent.Message(CompareMessage.InvalidInput))
    }

    @Test
    fun clearingLastRemainingPriceKeepsComparisonStable() {
        provider.result = textResult("200 г 270 ₽ 243 ₽ по карте")
        val viewModel = viewModel()
        viewModel.onAction(UiAction.CaptureConfirmed)
        advance()
        val id = viewModel.uiState.value.items.single().id

        viewModel.onAction(UiAction.PriceByCardChanged(id, ""))
        val afterCard = viewModel.uiState.value.items.single()
        assertThat(afterCard.priceByCard).isNull()
        assertDecimal(afterCard.price, "270")
        assertDecimal(afterCard.unitPrice.value, "1350.00")

        // Без хотя бы одной цены цена за единицу недоступна, поэтому последняя цена не удаляется.
        viewModel.onAction(UiAction.PriceChanged(id, ""))
        val afterBoth = viewModel.uiState.value.items.single()
        assertDecimal(afterBoth.price, "270")
        assertThat(afterBoth.priceByCard).isNull()
        assertDecimal(afterBoth.unitPrice.value, "1350.00")
    }

    @Test
    fun explicitPriceSourceSurvivesReloadAndCardEdit() {
        repository.items = listOf(
            product("a", "Творог", "350", "240", "685.71")
                .copy(unitPriceSource = UnitPriceSource.EXPLICIT, priceByCard = BigDecimal("200")),
        )
        val viewModel = viewModel()

        viewModel.onAction(UiAction.PriceByCardChanged("a", "190"))

        val item = viewModel.uiState.value.items.single()
        assertDecimal(item.unitPrice.value, "685.71")
        assertThat(item.unitPriceSource).isEqualTo(UnitPriceSource.EXPLICIT)
        assertDecimal(item.priceByCard, "190")
    }

    @Test
    fun explicitPriceFromRecognitionIsNotRecalculatedAfterCardEdit() {
        provider.result = textResult("цена за 1 кг: 685,71 ₽\n350 г 240 ₽ 243 ₽ по карте")
        val viewModel = viewModel()
        viewModel.onAction(UiAction.CaptureConfirmed)
        advance()
        val id = viewModel.uiState.value.items.single().id

        viewModel.onAction(UiAction.PriceByCardChanged(id, "200"))

        val item = viewModel.uiState.value.items.single()
        assertDecimal(item.unitPrice.value, "685.71")
        assertThat(item.unitPriceSource).isEqualTo(UnitPriceSource.EXPLICIT)
        assertDecimal(item.priceByCard, "200")
    }

    @Test
    fun changingNameUpdatesItemAndDraft() {
        val viewModel = viewModel()
        viewModel.onAction(UiAction.CaptureConfirmed)
        advance()
        val id = viewModel.uiState.value.items.single().id

        viewModel.onAction(UiAction.NameChanged(id, "Творог 5%"))

        assertThat(viewModel.uiState.value.items.single().name).isEqualTo("Творог 5%")
        assertThat(viewModel.uiState.value.drafts[id]?.name).isEqualTo("Творог 5%")
    }

    @Test
    fun invalidInputEmitsMessageAndKeepsPreviousPriceAndRawDraft() {
        val viewModel = viewModel()
        viewModel.onAction(UiAction.CaptureConfirmed)
        advance()
        val item = viewModel.uiState.value.items.single()

        val event = expectMessage(viewModel) {
            viewModel.onAction(UiAction.AmountChanged(item.id, "abc"))
        }

        assertThat(event).isEqualTo(UiEvent.Message(CompareMessage.InvalidInput))
        val state = viewModel.uiState.value
        assertThat(state.items.single()).isEqualTo(item)
        assertThat(state.drafts[item.id]?.amount).isEqualTo("abc")
    }

    @ParameterizedTest
    @ValueSource(strings = ["0", "-1", "."])
    fun invalidNumericValuesAreRejected(value: String) {
        val viewModel = viewModel()
        viewModel.onAction(UiAction.CaptureConfirmed)
        advance()
        val item = viewModel.uiState.value.items.single()

        val event = expectMessage(viewModel) {
            viewModel.onAction(UiAction.AmountChanged(item.id, value))
        }

        assertThat(event).isEqualTo(UiEvent.Message(CompareMessage.InvalidInput))
        assertThat(viewModel.uiState.value.items.single().unitPrice).isEqualTo(item.unitPrice)
    }

    @Test
    fun invalidAmountEditKeepsRangeItemAndRawDraft() {
        repository.items = listOf(rangeItem("range"))
        val viewModel = viewModel()
        val item = viewModel.uiState.value.items.single()

        val event = expectMessage(viewModel) {
            viewModel.onAction(UiAction.AmountChanged(item.id, "abc"))
        }

        assertThat(event).isEqualTo(UiEvent.Message(CompareMessage.InvalidInput))
        val state = viewModel.uiState.value
        assertThat(state.items.single()).isEqualTo(item)
        assertThat(state.drafts[item.id]?.amount).isEqualTo("abc")
    }

    @Test
    fun validAmountEditClearsRangeLabelAndRecalculates() {
        repository.items = listOf(rangeItem("range"))
        val viewModel = viewModel()
        val id = viewModel.uiState.value.items.single().id

        viewModel.onAction(UiAction.AmountChanged(id, "230"))

        val item = viewModel.uiState.value.items.single()
        assertDecimal(item.packageAmount, "230")
        assertThat(item.packageAmountLabel).isNull()
        assertDecimal(item.price, "139.99")
        assertDecimal(item.unitPrice.value, "608.65")
        assertThat(viewModel.uiState.value.drafts[id]?.amount).isEqualTo("230")
    }

    @Test
    fun priceEditKeepsRangeLabelAndRecalculatesFromUpperBound() {
        repository.items = listOf(rangeItem("range"))
        val viewModel = viewModel()
        val id = viewModel.uiState.value.items.single().id

        viewModel.onAction(UiAction.PriceChanged(id, "139,00"))

        val item = viewModel.uiState.value.items.single()
        assertDecimal(item.price, "139.00")
        assertDecimal(item.packageAmount, "240")
        assertThat(item.packageAmountLabel).isEqualTo("230-240 г")
        assertDecimal(item.unitPrice.value, "579.17")
        assertThat(viewModel.uiState.value.drafts[id]?.price).isEqualTo("139,00")
    }

    @ParameterizedTest
    @ValueSource(strings = ["0", "-1", "abc"])
    fun invalidPriceEditDoesNotChangeItem(value: String) {
        val viewModel = viewModel()
        viewModel.onAction(UiAction.CaptureConfirmed)
        advance()
        val item = viewModel.uiState.value.items.single()

        val event = expectMessage(viewModel) { viewModel.onAction(UiAction.PriceChanged(item.id, value)) }

        assertThat(event).isEqualTo(UiEvent.Message(CompareMessage.InvalidInput))
        assertThat(viewModel.uiState.value.items.single()).isEqualTo(item)
        assertThat(viewModel.uiState.value.drafts[item.id]?.price).isEqualTo(value)
    }

    @Test
    fun invalidInputIsEmittedOncePerEpisode() {
        val viewModel = viewModel()
        viewModel.onAction(UiAction.CaptureConfirmed)
        advance()
        val id = viewModel.uiState.value.items.single().id

        val messages = messagesDuring(viewModel) {
            viewModel.onAction(UiAction.AmountChanged(id, "abc"))
            viewModel.onAction(UiAction.AmountChanged(id, "0"))
        }

        assertThat(messages).containsExactly(UiEvent.Message(CompareMessage.InvalidInput))
    }

    @Test
    fun invalidInputIsEmittedAgainAfterValueBecomesValid() {
        val viewModel = viewModel()
        viewModel.onAction(UiAction.CaptureConfirmed)
        advance()
        val id = viewModel.uiState.value.items.single().id

        val messages = messagesDuring(viewModel) {
            viewModel.onAction(UiAction.AmountChanged(id, "abc"))
            viewModel.onAction(UiAction.AmountChanged(id, "700"))
            viewModel.onAction(UiAction.AmountChanged(id, "abc"))
        }

        assertThat(messages).containsExactly(
            UiEvent.Message(CompareMessage.InvalidInput),
            UiEvent.Message(CompareMessage.InvalidInput),
        )
    }

    @Test
    fun invalidInputForNewItemAfterRemovalEmitsMessage() {
        val viewModel = viewModel()
        viewModel.onAction(UiAction.CaptureConfirmed)
        advance()
        val firstId = viewModel.uiState.value.items.single().id
        viewModel.onAction(UiAction.AmountChanged(firstId, "abc"))
        viewModel.onAction(UiAction.ItemRemoved(firstId))
        viewModel.onAction(UiAction.CaptureConfirmed)
        advance()
        val secondId = viewModel.uiState.value.items.single().id

        val event = expectMessage(viewModel) {
            viewModel.onAction(UiAction.AmountChanged(secondId, "abc"))
        }

        assertThat(event).isEqualTo(UiEvent.Message(CompareMessage.InvalidInput))
    }

    @Test
    fun removingCheapestSwitchesToNextBest() {
        val viewModel = viewModel()
        addItem(viewModel, "350 г 240 ₽")
        val expensiveId = viewModel.uiState.value.items.first().id
        addItem(viewModel, "320 г 212 ₽")
        val cheapestId = checkNotNull(viewModel.uiState.value.cheapestId)
        addItem(viewModel, "500 г 350 ₽")
        assertThat(viewModel.uiState.value.cheapestId).isEqualTo(cheapestId)

        viewModel.onAction(UiAction.ItemRemoved(cheapestId))

        assertThat(viewModel.uiState.value.cheapestId).isEqualTo(expensiveId)
        assertThat(viewModel.uiState.value.items).hasSize(2)
    }

    @Test
    fun removingLastItemClearsCheapest() {
        val viewModel = viewModel()
        addItem(viewModel, "350 г 240 ₽")
        val id = viewModel.uiState.value.items.single().id

        viewModel.onAction(UiAction.ItemRemoved(id))

        assertThat(viewModel.uiState.value.items).isEmpty()
        assertThat(viewModel.uiState.value.cheapestId).isNull()
        assertThat(viewModel.uiState.value.drafts).isEmpty()
    }

    @Test
    fun permissionDeniedActionKeepsCaptureOpenAndSetsFlag() {
        val viewModel = viewModel()
        viewModel.onAction(UiAction.AddClicked)
        viewModel.onAction(UiAction.CaptureConfirmed)
        assertThat(viewModel.uiState.value.isBusy).isTrue()

        val messages = messagesDuring(viewModel) {
            viewModel.onAction(UiAction.PermissionDenied)
        }

        assertThat(messages.map { it.message })
            .containsExactly(CompareMessage.PermissionDenied)
        assertThat(viewModel.uiState.value.isCaptureOpen).isTrue()
        assertThat(viewModel.uiState.value.isPermissionDenied).isTrue()
        assertThat(viewModel.uiState.value.isBusy).isFalse()
        assertThat(viewModel.uiState.value.items).isEmpty()
        assertThat(provider.readCalls).isEqualTo(0)
    }

    @Test
    fun permissionDeniedDuringInFlightOcrCancelsCapture() {
        val gated = GatedImageTextProvider()
        val viewModel = viewModel(imageTextProvider = gated)
        viewModel.onAction(UiAction.AddClicked)
        viewModel.onAction(UiAction.CaptureConfirmed)
        advance()
        assertThat(gated.readCalls).isEqualTo(1)

        val messages = messagesDuring(viewModel) {
            viewModel.onAction(UiAction.PermissionDenied)
            gated.gates[0].complete(Unit)
        }

        assertThat(messages.map { it.message })
            .containsExactly(CompareMessage.PermissionDenied)
        assertThat(viewModel.uiState.value.items).isEmpty()
        assertThat(viewModel.uiState.value.isCaptureOpen).isTrue()
        assertThat(viewModel.uiState.value.isPermissionDenied).isTrue()
        assertThat(viewModel.uiState.value.isBusy).isFalse()
        assertThat(gated.readCalls).isEqualTo(1)
    }

    @Test
    fun deniedCaptureDoesNotResetBusyOfNewCapture() {
        val gated = GatedImageTextProvider()
        val viewModel = viewModel(imageTextProvider = gated)
        viewModel.onAction(UiAction.AddClicked)
        viewModel.onAction(UiAction.CaptureConfirmed)
        advance()
        viewModel.onAction(UiAction.PermissionDenied)

        viewModel.onAction(UiAction.AddClicked)
        viewModel.onAction(UiAction.CaptureConfirmed)
        advance()
        assertThat(viewModel.uiState.value.isBusy).isTrue()
        assertThat(gated.readCalls).isEqualTo(2)

        gated.gates[0].complete(Unit)
        advance()
        assertThat(viewModel.uiState.value.isBusy).isTrue()
        assertThat(viewModel.uiState.value.items).isEmpty()

        gated.gates[1].complete(Unit)
        advance()
        assertThat(viewModel.uiState.value.items).hasSize(1)
        assertThat(viewModel.uiState.value.isBusy).isFalse()
    }

    @Test
    fun dismissingCaptureClearsPermissionDeniedFlag() {
        val viewModel = viewModel()
        viewModel.onAction(UiAction.AddClicked)
        viewModel.onAction(UiAction.PermissionDenied)

        viewModel.onAction(UiAction.CaptureDismissed)

        assertThat(viewModel.uiState.value.isCaptureOpen).isFalse()
        assertThat(viewModel.uiState.value.isPermissionDenied).isFalse()
    }

    @Test
    fun openingCaptureClearsPermissionDeniedFlag() {
        val viewModel = viewModel()
        viewModel.onAction(UiAction.AddClicked)
        viewModel.onAction(UiAction.PermissionDenied)

        viewModel.onAction(UiAction.AddClicked)

        assertThat(viewModel.uiState.value.isCaptureOpen).isTrue()
        assertThat(viewModel.uiState.value.isPermissionDenied).isFalse()
    }

    @Test
    fun twoQuickCapturesDoNotStartTwice() {
        val viewModel = viewModel()

        viewModel.onAction(UiAction.CaptureConfirmed)
        assertThat(viewModel.uiState.value.isBusy).isTrue()
        viewModel.onAction(UiAction.CaptureConfirmed)
        advance()

        assertThat(viewModel.uiState.value.items).hasSize(1)
        assertThat(provider.readCalls).isEqualTo(1)
    }

    @Test
    fun loadsSavedSelectionOnStart() {
        repository.items = listOf(
            product("a", "Творог", "350", "240", "685.71"),
            product("b", "Творог 5%", "320", "212", "662.50"),
        )
        val viewModel = viewModel()

        val state = viewModel.uiState.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.items).isEqualTo(repository.items)
        assertThat(state.cheapestId).isEqualTo("b")
        assertThat(state.drafts["a"]?.amount).isEqualTo("350")
        assertThat(state.drafts["b"]?.price).isEqualTo("212,00")
    }

    @Test
    fun savedSelectionIsNotOverwrittenOnStartup() {
        val saved = listOf(product("a", "Творог", "350", "240", "685.71"))
        repository.items = saved

        viewModel()

        assertThat(repository.items).isEqualTo(saved)
        assertThat(repository.saveCalls).isEqualTo(0)
    }

    @Test
    fun actionsBeforeLoadCompletesAreIgnored() {
        val saved = listOf(product("a", "Творог", "350", "240", "685.71"))
        repository.items = saved
        val viewModel = viewModel(completeLoad = false)
        assertThat(viewModel.uiState.value.isLoading).isTrue()

        viewModel.onAction(UiAction.AddClicked)
        viewModel.onAction(UiAction.CaptureConfirmed)
        viewModel.onAction(UiAction.NameChanged("a", "Перезаписано"))
        advance()

        assertThat(viewModel.uiState.value.isCaptureOpen).isFalse()
        assertThat(viewModel.uiState.value.items).isEqualTo(saved)
        assertThat(repository.items).isEqualTo(saved)
        assertThat(repository.saveCalls).isEqualTo(0)
    }

    @Test
    fun loadFailureIsSilentAndSelectionStartsEmpty() {
        repository.loadError = IllegalStateException("load failed")
        val viewModel = viewModel(completeLoad = false)

        val messages = messagesDuring(viewModel) { advance() }

        assertThat(messages).isEmpty()
        assertThat(viewModel.uiState.value.isLoading).isFalse()
        assertThat(viewModel.uiState.value.items).isEmpty()
    }

    @Test
    fun loadFailureDisablesPersistenceToKeepExistingData() {
        val saved = listOf(product("a", "Творог", "350", "240", "685.71"))
        repository.items = saved
        repository.loadError = IllegalStateException("load failed")
        val viewModel = viewModel(completeLoad = false)

        val messages = messagesDuring(viewModel) {
            advance()
            viewModel.onAction(UiAction.CaptureConfirmed)
            advance()
        }

        assertThat(messages).isEmpty()
        assertThat(viewModel.uiState.value.items).hasSize(1)
        assertThat(repository.items).isEqualTo(saved)
        assertThat(repository.saveCalls).isEqualTo(0)
    }

    @Test
    fun cancelledLoadEmitsNoEventAndKeepsLoadingState() {
        repository.loadError = CancellationException()
        val viewModel = viewModel(completeLoad = false)

        val messages = messagesDuring(viewModel) { advance() }

        assertThat(messages).isEmpty()
        assertThat(viewModel.uiState.value.isLoading).isTrue()
        assertThat(viewModel.uiState.value.items).isEmpty()
    }

    @Test
    fun cancelledSaveEmitsNoEventAndDoesNotPersist() {
        val viewModel = viewModel()
        repository.saveError = CancellationException()
        repository.failNextSaves = 1

        val messages = messagesDuring(viewModel) { addItem(viewModel, "350 г 240 ₽") }

        assertThat(messages).isEmpty()
        assertThat(repository.items).isEmpty()
        assertThat(viewModel.uiState.value.items).hasSize(1)
    }

    @Test
    fun saveFailureIsReportedAndLaterSavesStillWork() {
        val viewModel = viewModel()
        repository.failNextSaves = 1

        val addEvent = expectMessage(viewModel) { addItem(viewModel, "350 г 240 ₽") }
        assertThat(addEvent).isEqualTo(UiEvent.Message(CompareMessage.StorageError))
        assertThat(repository.items).isEmpty()
        assertThat(repository.saveCalls).isEqualTo(0)

        val id = viewModel.uiState.value.items.single().id
        viewModel.onAction(UiAction.NameChanged(id, "Творог 5%"))
        advance()

        assertThat(repository.saveCalls).isEqualTo(1)
        assertThat(repository.items.single().name).isEqualTo("Творог 5%")
    }

    @Test
    fun savesSelectionAfterAddEditAndRemove() {
        val viewModel = viewModel()

        addItem(viewModel, "350 г 240 ₽")
        assertThat(repository.items).isEqualTo(viewModel.uiState.value.items)

        val id = viewModel.uiState.value.items.single().id
        viewModel.onAction(UiAction.NameChanged(id, "Творог 5%"))
        viewModel.onAction(UiAction.AmountChanged(id, "700"))
        advance()
        assertThat(repository.items.single().name).isEqualTo("Творог 5%")
        assertThat(
            repository.items.single().unitPrice.value.compareTo(BigDecimal("342.86")),
        ).isEqualTo(0)

        viewModel.onAction(UiAction.ItemRemoved(id))
        advance()
        assertThat(repository.items).isEmpty()
        assertThat(repository.saveCalls).isEqualTo(3)
    }

    @Test
    fun fullScenarioSurvivesRestart() {
        val first = viewModel()
        val added = listOf(
            "350 г 240 ₽",
            "320 г 212 ₽",
            "500 г 350 ₽",
            "400 г 260 ₽",
            "250 г 180 ₽",
        ).map { addItem(first, it) }
        val before = first.uiState.value
        val expectedBestId = added[3].id
        assertThat(before.items).hasSize(5)
        assertThat(before.cheapestId).isEqualTo(expectedBestId)
        assertThat(before.items.first { it.id == expectedBestId }
            .unitPrice.value.compareTo(BigDecimal("650.00"))).isEqualTo(0)
        assertThat(repository.items).isEqualTo(before.items)

        val second = viewModel()

        val after = second.uiState.value
        assertThat(after.items).isEqualTo(before.items)
        assertThat(after.cheapestId).isEqualTo(before.cheapestId)
        assertThat(after.drafts).isEqualTo(before.drafts)
    }

    @Test
    fun manuallyAddedItemSurvivesRestart() {
        val first = viewModel()
        first.onAction(UiAction.ManualEntryClicked)
        fillManualEntry(first, name = "Творог", amount = "350 г", price = "240")
        first.onAction(UiAction.ManualEntrySubmitted)
        advance()
        val before = first.uiState.value

        val second = viewModel()

        assertThat(second.uiState.value.items).isEqualTo(before.items)
        assertThat(second.uiState.value.drafts).isEqualTo(before.drafts)
        assertThat(second.uiState.value.cheapestId).isEqualTo(before.cheapestId)
    }

    private fun textResult(text: String): RawTextResult = RawTextResult.Success(singleLineFrame(text))

    private fun addItem(viewModel: CompareViewModel, text: String): ProductItem {
        provider.result = textResult(text)
        viewModel.onAction(UiAction.CaptureConfirmed)
        advance()
        return viewModel.uiState.value.items.last()
    }

    private fun fillManualEntry(
        viewModel: CompareViewModel,
        name: String,
        amount: String,
        price: String,
        priceByCard: String = "",
    ) {
        viewModel.onAction(UiAction.ManualFieldChanged(ManualField.NAME, name))
        viewModel.onAction(UiAction.ManualFieldChanged(ManualField.AMOUNT, amount))
        viewModel.onAction(UiAction.ManualFieldChanged(ManualField.PRICE, price))
        viewModel.onAction(UiAction.ManualFieldChanged(ManualField.PRICE_BY_CARD, priceByCard))
    }

    private fun advance() {
        dispatcher.scheduler.advanceUntilIdle()
    }

    private fun expectMessage(
        viewModel: CompareViewModel,
        produce: () -> Unit,
    ): UiEvent = runBlocking {
        val pending = async(start = CoroutineStart.UNDISPATCHED) { viewModel.events.first() }
        produce()
        advance()
        withTimeout(MESSAGE_TIMEOUT_MS) { pending.await() }
    }

    private fun messagesDuring(
        viewModel: CompareViewModel,
        produce: () -> Unit,
    ): List<UiEvent.Message> = runBlocking {
        val messages = mutableListOf<UiEvent.Message>()
        val collector = async(start = CoroutineStart.UNDISPATCHED) {
            viewModel.events.collect { event ->
                if (event is UiEvent.Message) messages.add(event)
            }
        }
        try {
            produce()
            advance()
            yield()
        } finally {
            collector.cancel()
        }
        messages.toList()
    }

    private fun viewModel(
        completeLoad: Boolean = true,
        imageTextProvider: ImageTextProvider = provider,
    ): CompareViewModel {
        val calculator = UnitPriceCalculator()
        val parser = PriceTagParser()
        return CompareViewModel(
            recognizePriceTag = RecognizePriceTagUseCase(
                imageTextProvider = imageTextProvider,
                merger = OcrTokenMerger(),
                parser = parser,
                calculator = calculator,
            ),
            addItem = AddItemUseCase(),
            removeItemUseCase = RemoveItemUseCase(),
            compareItems = CompareItemsUseCase(),
            calculator = calculator,
            createManualItem = CreateManualItemUseCase(parser, calculator),
            repository = repository,
        ).also { if (completeLoad) advance() }
    }

    private fun assertDecimal(actual: BigDecimal?, expected: String) {
        assertThat(actual).isNotNull()
        assertThat(actual!!.compareTo(BigDecimal(expected))).isEqualTo(0)
    }

    private fun rangeItem(id: String) = ProductItem(
        id = id,
        name = "Консервы рыбные шпроты в масле",
        packageAmount = BigDecimal("240"),
        packageAmountLabel = "230-240 г",
        packageUnit = WeightUnit.GRAM,
        price = BigDecimal("139.99"),
        unitPrice = UnitPrice(BigDecimal("583.29"), UnitPrice.Unit.KILOGRAM),
    )

    private fun product(
        id: String,
        name: String,
        grams: String,
        price: String,
        perKg: String,
    ) = ProductItem(
        id = id,
        name = name,
        packageAmount = BigDecimal(grams),
        packageUnit = WeightUnit.GRAM,
        price = BigDecimal(price),
        unitPrice = UnitPrice(BigDecimal(perKg), UnitPrice.Unit.KILOGRAM),
    )

    private class FakePriceTagRepository : PriceTagRepository {
        var items: List<ProductItem> = emptyList()
        var saveCalls: Int = 0
        var loadError: Throwable? = null
        var saveError: Throwable = IllegalStateException("save failed")
        var failNextSaves: Int = 0

        override suspend fun saveCurrent(items: List<ProductItem>) {
            if (failNextSaves > 0) {
                failNextSaves--
                throw saveError
            }
            this.items = items
            saveCalls++
        }

        override suspend fun loadCurrent(): List<ProductItem> {
            loadError?.let { throw it }
            return items
        }
    }

    private class GatedImageTextProvider : ImageTextProvider {
        val gates = mutableListOf<CompletableDeferred<Unit>>()
        var result: RawTextResult = RawTextResult.Success(singleLineFrame("350 г 240 ₽"))
        var readCalls: Int = 0
            private set

        override suspend fun readText(): RawTextResult {
            readCalls++
            val gate = CompletableDeferred<Unit>()
            gates.add(gate)
            gate.await()
            return result
        }
    }

    private class FakeImageTextProvider : ImageTextProvider {
        var result: RawTextResult = RawTextResult.Success(singleLineFrame("350 г 240 ₽"))
        var failure: Throwable? = null
        var readCalls: Int = 0

        override suspend fun readText(): RawTextResult {
            readCalls++
            failure?.let { throw it }
            return result
        }
    }
}
