package com.pricelens.core.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pricelens.R

/** Два способа добавить товар: камера и ручной ввод. */
@Composable
fun EntryActions(
    enabled: Boolean,
    onScan: () -> Unit,
    onManual: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(
            onClick = onScan,
            enabled = enabled,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .weight(1f)
                .height(64.dp),
        ) {
            Text(text = stringResource(R.string.scan_price_tag))
        }
        OutlinedButton(
            onClick = onManual,
            enabled = enabled,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .weight(1f)
                .height(64.dp),
        ) {
            Text(text = stringResource(R.string.add_manually))
        }
    }
}
