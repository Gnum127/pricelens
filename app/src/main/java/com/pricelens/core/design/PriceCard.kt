package com.pricelens.core.design

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pricelens.R
import com.pricelens.domain.model.ProductItem
import com.pricelens.domain.model.UnitPriceSource
import com.pricelens.domain.pricing.isCardPriceUsed

/** Параметры контура лучшей карточки вынесены, чтобы их можно было проверить тестом. */
internal data class BestCardOutlineSpec(val widthDp: Int, val colorRes: Int)

internal object BestCardOutline {
    const val WIDTH_DP = 2
    val COLOR_RES: Int = R.color.price_lens_primary
    val spec = BestCardOutlineSpec(WIDTH_DP, COLOR_RES)
}

internal val BestCardOutlineKey = SemanticsPropertyKey<BestCardOutlineSpec>("BestCardOutline")

/** Контур рисуется и одновременно публикует свои параметры в семантике для UI-тестов. */
@Composable
internal fun Modifier.bestCardOutline(isBest: Boolean): Modifier {
    if (!isBest) return this
    return this
        .border(
            width = BestCardOutline.WIDTH_DP.dp,
            color = colorResource(BestCardOutline.COLOR_RES),
            shape = MaterialTheme.shapes.medium,
        )
        .semantics { this[BestCardOutlineKey] = BestCardOutline.spec }
}

@Composable
fun PriceCard(
    item: ProductItem,
    nameValue: String,
    amountValue: String,
    priceValue: String,
    priceByCardValue: String,
    isBest: Boolean,
    difference: String?,
    onNameChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onPriceChange: (String) -> Unit,
    onPriceByCardChange: (String) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .bestCardOutline(isBest)
            .then(if (isBest) Modifier.testTag("best_card") else Modifier),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = nameValue,
                    onValueChange = onNameChange,
                    label = { Text(stringResource(R.string.name_label)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRemove) {
                    Text(stringResource(R.string.remove_item))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = amountValue,
                    onValueChange = onAmountChange,
                    label = { Text(stringResource(R.string.amount_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = priceValue,
                    onValueChange = onPriceChange,
                    label = { Text(stringResource(R.string.price_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = priceByCardValue,
                    onValueChange = onPriceByCardChange,
                    label = { Text(stringResource(R.string.price_by_card_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(
                        when {
                            item.unitPriceSource == UnitPriceSource.EXPLICIT ->
                                R.string.used_explicit_price
                            isCardPriceUsed(item.price, item.priceByCard) ->
                                R.string.used_card_price
                            else -> R.string.used_regular_price
                        },
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                UnitPriceBadge(item.unitPrice)
                when {
                    isBest -> Text(
                        text = stringResource(R.string.best_price),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    difference != null -> Text(
                        text = stringResource(R.string.difference, difference),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
