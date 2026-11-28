package com.pricelens.core.design

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pricelens.R
import com.pricelens.core.util.formatMoney
import com.pricelens.domain.model.ProductItem
import com.pricelens.domain.model.UnitPrice

@Composable
fun ComparisonSummary(
    best: ProductItem?,
    itemCount: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            val message = when {
                best == null || itemCount == 0 -> stringResource(R.string.summary_empty)
                itemCount == 1 -> stringResource(R.string.summary_single)
                else -> {
                    val unit = stringResource(
                        if (best.unitPrice.displayUnit == UnitPrice.Unit.KILOGRAM) {
                            R.string.per_kilogram
                        } else {
                            R.string.per_liter
                        },
                    )
                    stringResource(
                        R.string.summary_best,
                        best.name.ifBlank { stringResource(R.string.unnamed_product) },
                        "${formatMoney(best.unitPrice.value)} $unit",
                    )
                }
            }
            Text(text = message, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
