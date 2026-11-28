package com.pricelens.domain.usecase

import com.pricelens.domain.model.ManualEntryInput
import com.pricelens.domain.model.ProductItem
import com.pricelens.domain.model.UnitPriceSource
import com.pricelens.domain.parser.PriceTagParser
import com.pricelens.domain.pricing.UnitPriceCalculator
import java.math.BigDecimal

/**
 * Собирает товар из полей ручного ввода тем же путём, что и распознанный ценник:
 * парсер → расчёт цены за единицу. Возвращает null, если данных недостаточно.
 */
class CreateManualItemUseCase(
    private val parser: PriceTagParser,
    private val calculator: UnitPriceCalculator,
) {
    operator fun invoke(id: String, input: ManualEntryInput): ProductItem? {
        val regular = input.price.toPositiveMoney()
        val card = input.priceByCard.toPositiveMoney()
        if (input.price.isNotBlank() && regular == null) return null
        if (input.priceByCard.isNotBlank() && card == null) return null

        // Если введена только цена по карте, это ordinary price товара: расчёту нужна цена.
        val price = regular ?: card ?: return null
        val priceByCard = if (regular != null) card else null
        // Название не идёт в парсер: числа в нём («Молоко 1 л») не должны подменять вес упаковки.
        val rawText = buildList {
            add("${input.amount.trim()} ${input.unit.symbol}")
            add("$price ₽")
            if (priceByCard != null) add("$priceByCard ₽ по карте")
        }.filter { it.isNotBlank() }.joinToString("\n")

        val parsed = parser.parse(rawText) ?: return null
        val unitPrice = calculator.calculate(parsed) ?: return null
        return ProductItem(
            id = id,
            name = input.name.trim(),
            packageAmount = parsed.packageAmount,
            packageAmountLabel = parsed.packageAmountLabel,
            packageUnit = parsed.packageUnit,
            price = price,
            priceByCard = priceByCard,
            unitPrice = unitPrice,
            unitPriceSource = UnitPriceSource.PACKAGE,
            sourceText = null,
        )
    }

    private fun String.toPositiveMoney(): BigDecimal? =
        trim().replace(',', '.').toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO }
}
