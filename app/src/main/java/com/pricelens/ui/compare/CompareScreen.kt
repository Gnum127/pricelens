package com.pricelens.ui.compare

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.pricelens.R
import com.pricelens.core.design.AddTile
import com.pricelens.core.design.ComparisonSummary
import com.pricelens.core.design.PriceCard
import com.pricelens.core.util.formatAmount
import com.pricelens.core.util.formatMoney
import com.pricelens.data.camera.CameraController
import com.pricelens.data.camera.CameraPermissionChecker
import com.pricelens.domain.usecase.AddItemUseCase
import com.pricelens.ui.capture.CapturePanel

@Composable
fun CompareScreen(
    state: CompareUiState,
    cameraController: CameraController,
    permissionChecker: CameraPermissionChecker,
    snackbarHostState: SnackbarHostState,
    onAction: (UiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val best = state.items.firstOrNull { it.id == state.cheapestId }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.compare_title),
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
            items(items = state.items, key = { it.id }) { item ->
                val draft = state.drafts[item.id] ?: ItemDraft(
                    name = item.name,
                    amount = item.packageAmountLabel ?: item.packageAmount?.let(::formatAmount).orEmpty(),
                    price = item.price?.let(::formatMoney).orEmpty(),
                    priceByCard = item.priceByCard?.let(::formatMoney).orEmpty(),
                )
                val difference = if (item.id != state.cheapestId && best != null) {
                    formatMoney(item.unitPrice.value - best.unitPrice.value)
                } else {
                    null
                }
                PriceCard(
                    item = item,
                    nameValue = draft.name,
                    amountValue = draft.amount,
                    priceValue = draft.price,
                    priceByCardValue = draft.priceByCard,
                    isBest = item.id == state.cheapestId,
                    difference = difference,
                    onNameChange = { onAction(UiAction.NameChanged(item.id, it)) },
                    onAmountChange = { onAction(UiAction.AmountChanged(item.id, it)) },
                    onPriceChange = { onAction(UiAction.PriceChanged(item.id, it)) },
                    onPriceByCardChange = { onAction(UiAction.PriceByCardChanged(item.id, it)) },
                    onRemove = { onAction(UiAction.ItemRemoved(item.id)) },
                )
            }
            if (state.isLoading) {
                item {
                    Text(
                        text = stringResource(R.string.loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                AddTile(
                    enabled = !state.isLoading &&
                        state.items.size < AddItemUseCase.MAX_ITEMS && !state.isBusy,
                    onClick = { onAction(UiAction.AddClicked) },
                )
            }
            if (!state.isLoading && !state.isPersistenceAvailable) {
                item {
                    Text(
                        text = stringResource(R.string.error_storage),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            if (!state.isLoading) {
                item {
                    ComparisonSummary(best = best, itemCount = state.items.size)
                }
            }
        }
    }

    if (state.isCaptureOpen) {
        Dialog(onDismissRequest = { onAction(UiAction.CaptureDismissed) }) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
            ) {
                CapturePanel(
                    cameraController = cameraController,
                    permissionChecker = permissionChecker,
                    isBusy = state.isBusy,
                    permissionDenied = state.isPermissionDenied,
                    onCapture = { onAction(UiAction.CaptureConfirmed) },
                    onDismiss = { onAction(UiAction.CaptureDismissed) },
                    onPermissionDenied = { onAction(UiAction.PermissionDenied) },
                    onCameraError = { onAction(UiAction.CameraBindFailed) },
                )
            }
        }
    }
}
