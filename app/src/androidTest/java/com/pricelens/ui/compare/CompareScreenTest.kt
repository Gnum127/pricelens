package com.pricelens.ui.compare

import android.Manifest
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
import androidx.camera.view.PreviewView
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pricelens.R
import com.pricelens.core.design.AppTheme
import com.pricelens.data.camera.CameraController
import com.pricelens.data.camera.CameraPermissionChecker
import com.pricelens.domain.model.ProductItem
import com.pricelens.domain.model.UnitPrice
import com.pricelens.domain.model.UnitPriceSource
import com.pricelens.domain.model.WeightUnit
import com.pricelens.ui.capture.CapturePanel
import java.io.File
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompareScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun loadingStateHidesSummaryAndDisablesBothEntryButtons() {
        renderCompare(CompareUiState(isLoading = true))

        composeTestRule.onNodeWithText(text(R.string.loading)).assertIsDisplayed()
        composeTestRule.onNodeWithText(text(R.string.best_price)).assertIsNotDisplayed()
        composeTestRule.onNodeWithText(text(R.string.scan_price_tag)).assertHasNoClickAction()
        composeTestRule.onNodeWithText(text(R.string.add_manually)).assertHasNoClickAction()
    }

    @Test
    fun loadedStateShowsBestItemAndEnablesBothEntryButtons() {
        renderCompare(
            CompareUiState(
                items = listOf(
                    product("a", "Творог", "685.71"),
                    product("b", "Творог 5%", "662.50"),
                ),
                cheapestId = "b",
                isLoading = false,
            ),
        )

        composeTestRule.onNodeWithText(text(R.string.loading)).assertIsNotDisplayed()
        composeTestRule.onNodeWithText(
            text(R.string.summary_best, "Творог 5%", "662,50 " + text(R.string.per_kilogram)),
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText(text(R.string.best_price)).assertIsDisplayed()
        // Регрессия: заголовок плашки удалён, остаётся только сообщение сводки.
        composeTestRule.onAllNodesWithText("Итог").assertCountEquals(0)
        composeTestRule.onNodeWithText(text(R.string.error_storage)).assertIsNotDisplayed()
        composeTestRule.onNodeWithText(text(R.string.scan_price_tag)).assertHasClickAction()
        composeTestRule.onNodeWithText(text(R.string.add_manually)).assertHasClickAction()
    }

    @Test
    fun entryButtonsSendTheirActions() {
        val actions = mutableListOf<UiAction>()
        renderCompare(CompareUiState(isLoading = false), onAction = actions::add)

        composeTestRule.onNodeWithText(text(R.string.scan_price_tag)).performClick()
        composeTestRule.onNodeWithText(text(R.string.add_manually)).performClick()

        assertEquals(listOf(UiAction.AddClicked, UiAction.ManualEntryClicked), actions)
    }

    @Test
    fun manualDialogShowsAllFieldsAndSendsValues() {
        val actions = mutableListOf<UiAction>()
        renderCompare(
            CompareUiState(
                isLoading = false,
                manualEntry = ManualEntryState(ItemDraft("", "", "", "")),
            ),
            onAction = actions::add,
        )

        composeTestRule.onNodeWithText(text(R.string.manual_entry_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(text(R.string.name_label)).assertIsDisplayed()
        composeTestRule.onNodeWithText(text(R.string.amount_label)).assertIsDisplayed()
        composeTestRule.onNodeWithText(text(R.string.price_label)).assertIsDisplayed()
        composeTestRule.onNodeWithText(text(R.string.price_by_card_label)).assertIsDisplayed()

        composeTestRule.onNodeWithTag("manual_name").performTextReplacement("Творог")
        composeTestRule.onNodeWithTag("manual_amount").performTextReplacement("350 г")
        composeTestRule.onNodeWithTag("manual_price").performTextReplacement("240")
        composeTestRule.onNodeWithTag("manual_price_by_card").performTextReplacement("220")
        composeTestRule.onNodeWithText(text(R.string.save)).performClick()

        assertEquals(
            listOf(
                UiAction.ManualFieldChanged(ManualField.NAME, "Творог"),
                UiAction.ManualFieldChanged(ManualField.AMOUNT, "350 г"),
                UiAction.ManualFieldChanged(ManualField.PRICE, "240"),
                UiAction.ManualFieldChanged(ManualField.PRICE_BY_CARD, "220"),
                UiAction.ManualEntrySubmitted,
            ),
            actions,
        )
    }

    @Test
    fun manualDialogCancelSendsDismissAction() {
        val actions = mutableListOf<UiAction>()
        renderCompare(
            CompareUiState(
                isLoading = false,
                manualEntry = ManualEntryState(ItemDraft("Творог", "350 г", "240", "")),
            ),
            onAction = actions::add,
        )

        composeTestRule.onNodeWithText(text(R.string.cancel)).performClick()

        assertEquals(listOf(UiAction.ManualEntryDismissed), actions)
    }

    @Test
    fun failedRecognitionDialogExplainsWhyManualEntryIsOpen() {
        renderCompare(
            CompareUiState(
                isLoading = false,
                manualEntry = ManualEntryState(recognitionFailed = true),
            ),
        )

        composeTestRule.onNodeWithText(text(R.string.manual_recognition_failed)).assertIsDisplayed()
    }

    @Test
    fun cardPriceFieldIsEditableAndUsedPriceIsVisible() {
        val actions = mutableListOf<UiAction>()
        val item = product("card", "Печенье", "1215.00").copy(priceByCard = BigDecimal("243"))
        val initialDraft = ItemDraft("Печенье", "200", "270,00", "243,00")
        val currentState = mutableStateOf(
            CompareUiState(
                items = listOf(item),
                drafts = mapOf(item.id to initialDraft),
                isLoading = false,
            ),
        )
        renderCompare(
            state = { currentState.value },
            onAction = { action ->
                actions.add(action)
                if (action is UiAction.PriceByCardChanged) {
                    currentState.value = currentState.value.copy(
                        drafts = currentState.value.drafts +
                            (item.id to initialDraft.copy(priceByCard = action.value)),
                    )
                }
            },
        )

        composeTestRule.onNodeWithText(text(R.string.price_by_card_label)).assertIsDisplayed()
        composeTestRule.onNodeWithText(text(R.string.used_card_price)).assertIsDisplayed()
        composeTestRule.onNodeWithText("243,00").performTextReplacement("250")

        assertEquals(listOf(UiAction.PriceByCardChanged(item.id, "250")), actions)
    }

    @Test
    fun explicitUnitPriceSourceIsShownInsteadOfCardDiscount() {
        val item = product("explicit", "Печенье", "685.71")
            .copy(priceByCard = BigDecimal("200"), unitPriceSource = UnitPriceSource.EXPLICIT)
        renderCompare(CompareUiState(items = listOf(item), isLoading = false))

        composeTestRule.onNodeWithText(text(R.string.used_explicit_price)).assertIsDisplayed()
        composeTestRule.onNodeWithText(text(R.string.used_card_price)).assertIsNotDisplayed()
    }

    @Test
    fun regularPriceIsShownAsUsedWhenCardPriceIsAbsentOrWorse() {
        val withoutCard = product("a", "Печенье", "1215.00")
        val worseCard = withoutCard.copy(priceByCard = BigDecimal("400"))

        renderCompare(CompareUiState(items = listOf(withoutCard, worseCard), isLoading = false))

        composeTestRule.onAllNodesWithText(text(R.string.used_regular_price)).apply {
            assertCountEquals(2)
            get(0).assertIsDisplayed()
            get(1).assertIsDisplayed()
        }
        composeTestRule.onAllNodesWithText(text(R.string.used_card_price)).assertCountEquals(0)
    }

    @Test
    fun fiveItemsDisableBothEntryButtons() {
        val items = (1..5).map { index -> product("item$index", "Товар $index", "700.00") }
        renderCompare(CompareUiState(items = items, isLoading = false))

        composeTestRule.onNodeWithText(text(R.string.scan_price_tag)).assertHasNoClickAction()
        composeTestRule.onNodeWithText(text(R.string.add_manually)).assertHasNoClickAction()
    }

    @Test
    fun permissionDeniedHintFromStateIsVisibleInCaptureDialog() {
        renderCompare(
            CompareUiState(isCaptureOpen = true, isPermissionDenied = true, isLoading = false),
        )

        composeTestRule.onNodeWithText(text(R.string.capture_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(text(R.string.error_permission)).assertIsDisplayed()
        composeTestRule.onNodeWithText(text(R.string.capture_button)).assertHasNoClickAction()
    }

    @Test
    fun compareScreenRoutesSystemDenialToPermissionDeniedAction() {
        val registry = RecordingRegistry()
        val actions = mutableListOf<UiAction>()
        composeTestRule.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides FakeOwner(registry)) {
                var denied by remember { mutableStateOf(false) }
                AppTheme {
                    CompareScreen(
                        state = CompareUiState(
                            isCaptureOpen = true,
                            isPermissionDenied = denied,
                            isLoading = false,
                        ),
                        cameraController = FakeCameraController(),
                        permissionChecker = CameraPermissionChecker { false },
                        snackbarHostState = SnackbarHostState(),
                        onAction = { action ->
                            actions.add(action)
                            if (action == UiAction.PermissionDenied) denied = true
                        },
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        assertEquals(1, registry.launches)

        composeTestRule.runOnUiThread { registry.deny() }
        composeTestRule.waitForIdle()

        assertEquals(listOf(UiAction.PermissionDenied), actions)
        assertEquals(1, registry.launches)
        composeTestRule.onNodeWithText(text(R.string.capture_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(text(R.string.error_permission)).assertIsDisplayed()
        composeTestRule.onNodeWithText(text(R.string.capture_button)).assertHasNoClickAction()
    }

    @Test
    fun permissionDeniedHintIsVisibleAndCaptureDisabled() {
        renderCapture(CameraPermissionChecker { false })

        composeTestRule.onNodeWithText(text(R.string.error_permission)).assertIsDisplayed()
        composeTestRule.onNodeWithText(text(R.string.capture_button)).assertHasNoClickAction()
    }

    @Test
    fun permissionGrantedHidesHintAndEnablesCapture() {
        renderCapture(CameraPermissionChecker { true })

        composeTestRule.onNodeWithText(text(R.string.error_permission)).assertIsNotDisplayed()
        composeTestRule.onNodeWithText(text(R.string.capture_button)).assertHasClickAction()
    }

    @Test
    fun permissionRequestIsLaunchedBeforeDenialAndHintIsHidden() {
        val registry = RecordingRegistry()
        composeTestRule.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides FakeOwner(registry)) {
                AppTheme { CapturePanelFixture(CameraPermissionChecker { false }, permissionDenied = false) }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(text(R.string.error_permission)).assertIsNotDisplayed()
        assertEquals(1, registry.launches)
        assertTrue(registry.contract is ActivityResultContracts.RequestPermission)
        assertEquals(Manifest.permission.CAMERA, registry.input)
    }

    @Test
    fun systemPermissionDenialShowsHintAndNotifiesCaller() {
        val registry = RecordingRegistry()
        var callbacks = 0
        composeTestRule.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides FakeOwner(registry)) {
                AppTheme {
                    var denied by remember { mutableStateOf(false) }
                    CapturePanel(
                        cameraController = FakeCameraController(),
                        permissionChecker = CameraPermissionChecker { false },
                        isBusy = false,
                        permissionDenied = denied,
                        onCapture = {},
                        onDismiss = {},
                        onPermissionDenied = {
                            callbacks++
                            denied = true
                        },
                        onCameraError = {},
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        assertEquals(1, registry.launches)

        composeTestRule.runOnUiThread { registry.deny() }
        composeTestRule.waitForIdle()

        assertEquals(1, callbacks)
        assertEquals(1, registry.launches)
        composeTestRule.onNodeWithText(text(R.string.error_permission)).assertIsDisplayed()
        composeTestRule.onNodeWithText(text(R.string.capture_button)).assertHasNoClickAction()
    }

    @Test
    fun deniedHintDoesNotLaunchSystemPermissionRequest() {
        val registry = RecordingRegistry()
        composeTestRule.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides FakeOwner(registry)) {
                AppTheme { CapturePanelFixture(CameraPermissionChecker { false }) }
            }
        }

        composeTestRule.onNodeWithText(text(R.string.error_permission)).assertIsDisplayed()
        assertEquals(0, registry.launches)
    }

    @Test
    fun returningFromSettingsWithPermissionHidesHint() {
        val checker = MutablePermissionChecker(granted = false)
        renderCapture(checker)
        composeTestRule.onNodeWithText(text(R.string.error_permission)).assertIsDisplayed()
        composeTestRule.onNodeWithText(text(R.string.capture_button)).assertHasNoClickAction()

        checker.granted = true
        composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(text(R.string.error_permission)).assertIsNotDisplayed()
        composeTestRule.onNodeWithText(text(R.string.capture_button)).assertHasClickAction()
    }

    @Test
    fun returningWithoutPermissionKeepsHintAndDoesNotRelaunch() {
        val registry = RecordingRegistry()
        val checker = MutablePermissionChecker(granted = false)
        composeTestRule.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides FakeOwner(registry)) {
                AppTheme { CapturePanelFixture(checker) }
            }
        }

        composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(text(R.string.error_permission)).assertIsDisplayed()
        composeTestRule.onNodeWithText(text(R.string.capture_button)).assertHasNoClickAction()
        assertEquals(0, registry.launches)
    }

    private fun text(id: Int, vararg args: Any): String = composeTestRule.activity.getString(id, *args)

    private fun product(id: String, name: String, perKg: String) = ProductItem(
        id = id,
        name = name,
        packageAmount = BigDecimal("500"),
        packageUnit = WeightUnit.GRAM,
        price = BigDecimal("350"),
        unitPrice = UnitPrice(BigDecimal(perKg), UnitPrice.Unit.KILOGRAM),
    )

    private fun renderCompare(state: CompareUiState, onAction: (UiAction) -> Unit = {}) {
        renderCompare(state = { state }, onAction = onAction)
    }

    private fun renderCompare(state: () -> CompareUiState, onAction: (UiAction) -> Unit = {}) {
        composeTestRule.setContent {
            AppTheme {
                CompareScreen(
                    state = state(),
                    cameraController = FakeCameraController(),
                    permissionChecker = CameraPermissionChecker { false },
                    snackbarHostState = SnackbarHostState(),
                    onAction = onAction,
                )
            }
        }
    }

    private fun renderCapture(checker: CameraPermissionChecker) {
        composeTestRule.setContent {
            AppTheme { CapturePanelFixture(checker) }
        }
    }

    @Composable
    private fun CapturePanelFixture(checker: CameraPermissionChecker, permissionDenied: Boolean = true) {
        CapturePanel(
            cameraController = FakeCameraController(),
            permissionChecker = checker,
            isBusy = false,
            permissionDenied = permissionDenied,
            onCapture = {},
            onDismiss = {},
            onPermissionDenied = {},
            onCameraError = {},
        )
    }

    private class MutablePermissionChecker(var granted: Boolean) : CameraPermissionChecker {
        override fun isGranted(): Boolean = granted
    }

    private class FakeOwner(
        override val activityResultRegistry: ActivityResultRegistry,
    ) : ActivityResultRegistryOwner

    private class RecordingRegistry : ActivityResultRegistry() {
        var launches: Int = 0
            private set
        var contract: ActivityResultContract<*, *>? = null
            private set
        var input: Any? = null
            private set
        private var requestCode: Int? = null

        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?,
        ) {
            this.launches++
            this.requestCode = requestCode
            this.contract = contract
            this.input = input
        }

        fun deny() {
            dispatchResult(requireNotNull(requestCode), false)
        }
    }

    private class FakeCameraController : CameraController {
        override suspend fun bind(
            lifecycleOwner: LifecycleOwner,
            previewView: PreviewView,
        ) = Unit

        override suspend fun captureToFile(file: File): Boolean = false

        override fun unbind() = Unit
    }
}
