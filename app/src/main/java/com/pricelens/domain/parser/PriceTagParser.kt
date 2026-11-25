package com.pricelens.domain.parser

import com.pricelens.domain.model.ParsedPriceTag
import com.pricelens.domain.model.UnitPrice
import com.pricelens.domain.model.WeightUnit
import java.math.BigDecimal

class PriceTagParser {
    fun parse(rawText: String): ParsedPriceTag? {
        if (rawText.isBlank()) return null

        val normalized = rawText
            .replace('\u00A0', ' ')
            .replace(',', '.')
        if (normalized.isBlank()) return null

        val explicitMatch = explicitUnitPriceRegex.find(normalized)
        val explicitUnitPrice = explicitMatch?.let { match ->
            val value = match.groupValues[2].toBigDecimalOrNull() ?: return null
            if (value <= BigDecimal.ZERO) return null
            value
        }
        val explicitUnit = explicitMatch?.let { match ->
            when (match.groupValues[1].lowercase()) {
                "кг", "kg" -> UnitPrice.Unit.KILOGRAM
                else -> UnitPrice.Unit.LITER
            }
        }

        val searchable = maskUnitPricePhrases(normalized, explicitMatch?.range)

        val amountMatch = amountRegex.find(searchable)
        if (amountMatch == null) {
            val price = explicitUnitPrice ?: return null
            return ParsedPriceTag(
                name = explicitMatch?.let { extractName(rawText.substring(0, it.range.first)) },
                explicitUnitPrice = price,
                explicitUnitPriceUnit = explicitUnit ?: return null,
                sourceText = rawText,
            )
        }

        val amount = amountMatch.groupValues[1].toBigDecimalOrNull() ?: return null
        if (amount <= BigDecimal.ZERO) return null

        val packageUnit: WeightUnit
        val baseAmount: BigDecimal
        when (amountMatch.groupValues[2].lowercase()) {
            "кг", "kg" -> {
                packageUnit = WeightUnit.GRAM
                baseAmount = amount.multiply(THOUSAND)
            }
            "г", "g" -> {
                packageUnit = WeightUnit.GRAM
                baseAmount = amount
            }
            "л", "l" -> {
                packageUnit = WeightUnit.MILLILITER
                baseAmount = amount.multiply(THOUSAND)
            }
            "мл", "ml" -> {
                packageUnit = WeightUnit.MILLILITER
                baseAmount = amount
            }
            else -> return null
        }

        val price = findPrice(searchable.removeRange(amountMatch.range)) ?: return null
        if (price <= BigDecimal.ZERO) return null

        val name = extractName(rawText.substring(0, amountMatch.range.first))
            ?: explicitMatch?.let { extractName(rawText.substring(0, it.range.first)) }

        return ParsedPriceTag(
            name = name,
            packageAmount = baseAmount,
            packageUnit = packageUnit,
            price = price,
            explicitUnitPrice = explicitUnitPrice,
            explicitUnitPriceUnit = explicitUnit,
            sourceText = rawText,
        )
    }

    private fun maskUnitPricePhrases(text: String, fullMatchRange: IntRange?): String {
        val chars = text.toCharArray()
        val ranges = buildList<IntRange> {
            fullMatchRange?.let(::add)
            explicitPhraseRegex.findAll(text).forEach { add(it.range) }
        }
        ranges.forEach { range ->
            for (index in range) {
                chars[index] = ' '
            }
        }
        return String(chars)
    }

    private fun findPrice(text: String): BigDecimal? {
        priceWithCurrencyRegex.find(text)?.groupValues?.get(1)?.toBigDecimalOrNull()
            ?.let { return it }

        val withoutNoise = text
            .replace(percentRegex, " ")
            .replace(dateRegex, " ")
            .replace(longDigitRegex, " ")

        return priceLikeNumberRegex.findAll(withoutNoise)
            .mapNotNull { it.value.toBigDecimalOrNull() }
            .lastOrNull()
    }

    private fun extractName(prefix: String): String? {
        val cleaned = prefix
            .replace(percentRegex, " ")
            .replace(numberRegex, " ")
            .replace(punctuationRegex, " ")
            .trim()

        return cleaned
            .split(whitespaceRegex)
            .filter { it.length > 1 && it.lowercase() !in noiseWords }
            .joinToString(" ")
            .takeIf { it.isNotBlank() }
    }

    private companion object {
        val THOUSAND = BigDecimal("1000")
        val numberRegex = Regex("""\d+(?:\.\d+)?""")
        val priceLikeNumberRegex = Regex("""(?<!\d)\d+(?:\.\d{1,2})?(?!\d)""")
        val dateRegex = Regex("""\b\d{1,2}\.\d{1,2}\.\d{2,4}\b""")
        val longDigitRegex = Regex("""\b\d{6,}\b""")
        val explicitPhraseRegex = Regex("""(?iu)цена[ \t]+за[ \t]+1[ \t]*(кг|kg|л|l)""")
        val amountRegex = Regex("""(?iu)(\d+(?:\.\d+)?)[ \t]*(кг|kg|г|g|мл|ml|л|l)(?!\p{L})""")
        val explicitUnitPriceRegex = Regex(
            """(?iu)цена[ \t]+за[ \t]+1[ \t]*(кг|kg|л|l)[ \t]*:[ \t]*(\d+(?:\.\d{1,2})?)[ \t]*(?:₽|руб\.?|р\.?|rub\.?|r\.?)(?!\p{L})""",
        )
        val priceWithCurrencyRegex = Regex(
            """(?iu)(\d+(?:\.\d+)?)[ \t]*(₽|руб\.?|р\.?|rub\.?|r\.?)(?!\p{L})""",
        )
        val percentRegex = Regex("""\d+(?:\.\d+)?\s*%""")
        val punctuationRegex = Regex("""[^\p{L}\s-]+""")
        val whitespaceRegex = Regex("""\s+""")
        val noiseWords = setOf(
            "объём", "объем", "вес", "цена", "за", "акция", "скидка",
            "штрихкод", "штрих", "код", "товар", "продукт",
            "кг", "kg", "мл", "ml", "руб", "rub",
            "volume", "weight", "price", "action",
        )
    }
}
