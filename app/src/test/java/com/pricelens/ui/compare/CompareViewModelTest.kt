package com.pricelens.ui.compare

import com.google.common.truth.Truth.assertThat
import com.pricelens.domain.model.RawTextResult
import com.pricelens.domain.model.UnitPrice
import com.pricelens.domain.model.WeightUnit
import com.pricelens.domain.parser.PriceTagParser
import com.pricelens.domain.pricing.UnitPriceCalculator
import com.pricelens.domain.repository.ImageTextProvider
import com.pricelens.domain.usecase.AddItemUseCase
import com.pricelens.domain.usecase.CompareItemsUseCase
import com.pricelens.domain.usecase.RecognizePriceTagUseCase
import com.pricelens.domain.usecase.RemoveItemUseCase
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
        provider.result = RawTextResult.Success("Цена за 1 кг: 685,71 ₽")
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

        assertThat(
            viewModel.uiState.value.items.single().unitPrice.value.compareTo(BigDecimal("979.59")),
        ).isEqualTo(0)
    }

    @Test
    fun explicitLiterPriceGetsEditableBaseAmountAndPrice() {
        provider.result = RawTextResult.Success("Цена за 1 л: 129,90 ₽")
        val viewModel = viewModel()

        viewModel.onAction(UiAction.CaptureConfirmed)
        advance()

        val item = viewModel.uiState.value.items.single()
        assertThat(item.packageAmount?.compareTo(BigDecimal("1000"))).isEqualTo(0)
        assertThat(item.packageUnit).isEqualTo(WeightUnit.MILLILITER)
        assertThat(item.unitPrice.displayUnit).isEqualTo(UnitPrice.Unit.LITER)
        assertThat(viewModel.uiState.value.drafts[item.id]?.amount).isEqualTo("1000")

        viewModel.onAction(UiAction.AmountChanged(item.id, "800"))

        assertThat(
            viewModel.uiState.value.items.single().unitPrice.value.compareTo(BigDecimal("162.38")),
        ).isEqualTo(0)
    }

    @Test
    fun emptyTextEmitsExactlyOneMessageAndAddsNothing() {
        provider.result = RawTextResult.EmptyText
        val viewModel = viewModel()

        val messages = messagesDuring(viewModel) { viewModel.onAction(UiAction.CaptureConfirmed) }

        assertThat(messages).containsExactly(UiEvent.Message(CompareMessage.EmptyText))
        assertThat(viewModel.uiState.value.items).isEmpty()
        assertThat(viewModel.uiState.value.isCaptureOpen).isFalse()
    }

    @Test
    fun unparseableTextEmitsMessageWithRawText() {
        provider.result = RawTextResult.Success("непонятный ценник")
        val viewModel = viewModel()

        val event = expectMessage(viewModel) { viewModel.onAction(UiAction.CaptureConfirmed) }

        assertThat(event).isEqualTo(
            UiEvent.Message(CompareMessage.Unparseable("непонятный ценник")),
        )
    }

    @Test
    fun cameraErrorEmitsMessage() {
        provider.result = RawTextResult.CameraError
        val viewModel = viewModel()

        val event = expectMessage(viewModel) { viewModel.onAction(UiAction.CaptureConfirmed) }

        assertThat(event).isEqualTo(UiEvent.Message(CompareMessage.CameraError))
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
        provider.result = RawTextResult.Success("320 г 212 ₽")
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
        assertThat(state.items.single().unitPrice).isEqualTo(item.unitPrice)
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
    fun permissionDeniedActionEmitsMessageWithoutCapture() {
        val viewModel = viewModel()

        val event = expectMessage(viewModel) { viewModel.onAction(UiAction.PermissionDenied) }

        assertThat(event).isEqualTo(UiEvent.Message(CompareMessage.PermissionDenied))
        assertThat(provider.readCalls).isEqualTo(0)
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

    private fun addItem(viewModel: CompareViewModel, text: String) {
        provider.result = RawTextResult.Success(text)
        viewModel.onAction(UiAction.CaptureConfirmed)
        advance()
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

    private fun viewModel(): CompareViewModel {
        val calculator = UnitPriceCalculator()
        return CompareViewModel(
            recognizePriceTag = RecognizePriceTagUseCase(provider, PriceTagParser(), calculator),
            addItem = AddItemUseCase(),
            removeItemUseCase = RemoveItemUseCase(),
            compareItems = CompareItemsUseCase(),
            calculator = calculator,
        )
    }

    private class FakeImageTextProvider : ImageTextProvider {
        var result: RawTextResult = RawTextResult.Success("350 г 240 ₽")
        var readCalls: Int = 0

        override suspend fun readText(): RawTextResult {
            readCalls++
            return result
        }
    }
}
