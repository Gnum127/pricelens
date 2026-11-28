package com.pricelens.ui.compare

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pricelens.R

@Composable
fun ManualEntryDialog(
    state: ManualEntryState,
    onFieldChange: (ManualField, String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.manual_entry_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.recognitionFailed) {
                    Text(
                        text = stringResource(R.string.manual_recognition_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                OutlinedTextField(
                    value = state.draft.name,
                    onValueChange = { onFieldChange(ManualField.NAME, it) },
                    label = { Text(stringResource(R.string.name_label)) },
                    singleLine = true,
                    modifier = Modifier.testTag("manual_name"),
                )
                OutlinedTextField(
                    value = state.draft.amount,
                    onValueChange = { onFieldChange(ManualField.AMOUNT, it) },
                    label = { Text(stringResource(R.string.amount_label)) },
                    singleLine = true,
                    modifier = Modifier.testTag("manual_amount"),
                )
                Text(
                    text = stringResource(R.string.manual_entry_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = state.draft.price,
                    onValueChange = { onFieldChange(ManualField.PRICE, it) },
                    label = { Text(stringResource(R.string.price_label)) },
                    singleLine = true,
                    modifier = Modifier.testTag("manual_price"),
                )
                OutlinedTextField(
                    value = state.draft.priceByCard,
                    onValueChange = { onFieldChange(ManualField.PRICE_BY_CARD, it) },
                    label = { Text(stringResource(R.string.price_by_card_label)) },
                    singleLine = true,
                    modifier = Modifier.testTag("manual_price_by_card"),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) {
                Text(text = stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        },
    )
}
