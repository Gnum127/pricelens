package com.pricelens.core.design

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pricelens.R
import com.pricelens.core.util.formatMoney
import com.pricelens.domain.model.UnitPrice

@Composable
fun UnitPriceBadge(
    unitPrice: UnitPrice,
    modifier: Modifier = Modifier,
) {
    val unitLabel = stringResource(
        if (unitPrice.displayUnit == UnitPrice.Unit.KILOGRAM) {
            R.string.per_kilogram
        } else {
            R.string.per_liter
        },
    )
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Text(
            text = "${formatMoney(unitPrice.value)} $unitLabel",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}
