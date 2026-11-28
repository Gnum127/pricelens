package com.pricelens.ui.compare

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pricelens.R
import com.pricelens.domain.model.ManualUnit

@Composable
fun ManualEntryDialog(
    state: ManualEntryState,
    onFieldChange: (ManualField, String) -> Unit,
    onUnitChange: (ManualUnit) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    var unitsExpanded by remember { mutableStateOf(false) }
    val amountFocus = remember { FocusRequester() }

    // Пользователь сразу попадает в числовое поле: клавиатура без букв, вводить нужно только количество.
    LaunchedEffect(Unit) { amountFocus.requestFocus() }

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
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("manual_name"),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = state.draft.amount,
                        onValueChange = { onFieldChange(ManualField.AMOUNT, it) },
                        label = { Text(stringResource(R.string.amount_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(amountFocus)
                            .testTag("manual_amount"),
                    )
                    Box {
                        OutlinedButton(
                            onClick = { unitsExpanded = true },
                            modifier = Modifier.testTag("manual_unit"),
                        ) {
                            Text(text = stringResource(state.unit.labelRes()))
                        }
                        DropdownMenu(
                            expanded = unitsExpanded,
                            onDismissRequest = { unitsExpanded = false },
                        ) {
                            ManualUnit.entries.forEach { unit ->
                                DropdownMenuItem(
                                    text = { Text(text = stringResource(unit.labelRes())) },
                                    onClick = {
                                        onUnitChange(unit)
                                        unitsExpanded = false
                                    },
                                    modifier = Modifier.testTag("manual_unit_${unit.name.lowercase()}"),
                                )
                            }
                        }
                    }
                }
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
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("manual_price"),
                )
                OutlinedTextField(
                    value = state.draft.priceByCard,
                    onValueChange = { onFieldChange(ManualField.PRICE_BY_CARD, it) },
                    label = { Text(stringResource(R.string.price_by_card_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("manual_price_by_card"),
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

private fun ManualUnit.labelRes(): Int = when (this) {
    ManualUnit.GRAM -> R.string.unit_gram
    ManualUnit.KILOGRAM -> R.string.unit_kilogram
    ManualUnit.MILLILITER -> R.string.unit_milliliter
    ManualUnit.LITER -> R.string.unit_liter
}
