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
            .replace('–', '-')
            .replace('—', '-')
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
        val rangeMatch = rangeAmountRegex.find(searchable)
        val amountMatch = if (rangeMatch != null) null else amountRegex.find(searchable)
        val amountRange = rangeMatch?.range ?: amountMatch?.range
        if (amountRange == null) {
            val price = explicitUnitPrice ?: return null
            return ParsedPriceTag(
                name = explicitMatch?.let { extractName(rawText.substring(0, it.range.first)) },
                explicitUnitPrice = price,
                explicitUnitPriceUnit = explicitUnit ?: return null,
                sourceText = rawText,
            )
        }

        val packageUnit: WeightUnit
        val baseAmount: BigDecimal
        var amountLabel: String? = null
        if (rangeMatch != null) {
            val from = rangeMatch.groupValues[1].toBigDecimalOrNull() ?: return null
            val to = rangeMatch.groupValues[2].toBigDecimalOrNull() ?: return null
            if (from <= BigDecimal.ZERO || to <= BigDecimal.ZERO) return null
            baseAmount = convertAmount(to, rangeMatch.groupValues[3]) ?: return null
            packageUnit = unitFor(rangeMatch.groupValues[3]) ?: return null
            amountLabel = "${displayNumber(from)}-${displayNumber(to)} ${canonicalUnit(rangeMatch.groupValues[3].lowercase())}"
        } else {
            val amount = amountMatch!!.groupValues[1].toBigDecimalOrNull() ?: return null
            if (amount <= BigDecimal.ZERO) return null
            baseAmount = convertAmount(amount, amountMatch.groupValues[2]) ?: return null
            packageUnit = unitFor(amountMatch.groupValues[2]) ?: return null
        }

        val price = findPrice(searchable.removeRange(amountRange)) ?: return null
        if (price <= BigDecimal.ZERO) return null

        val name = extractName(rawText.substring(0, amountRange.first))
            ?: explicitMatch?.let { extractName(rawText.substring(0, it.range.first)) }

        return ParsedPriceTag(
            name = name,
            packageAmount = baseAmount,
            packageAmountLabel = amountLabel,
            packageUnit = packageUnit,
            price = price,
            explicitUnitPrice = explicitUnitPrice,
            explicitUnitPriceUnit = explicitUnit,
            sourceText = rawText,
        )
    }

    private fun convertAmount(amount: BigDecimal, unit: String): BigDecimal? = when (unit.lowercase()) {
        "кг", "kg", "л", "l" -> amount.multiply(THOUSAND)
        "г", "g", "r", "мл", "ml" -> amount
        else -> null
    }

    private fun unitFor(unit: String): WeightUnit? = when (unit.lowercase()) {
        "кг", "kg", "г", "g", "r" -> WeightUnit.GRAM
        "мл", "ml", "л", "l" -> WeightUnit.MILLILITER
        else -> null
    }

    private fun canonicalUnit(unit: String): String = when (unit) {
        "kg" -> "кг"
        "g", "r" -> "г"
        "ml" -> "мл"
        "l" -> "л"
        else -> unit
    }

    private fun displayNumber(value: BigDecimal): String =
        value.stripTrailingZeros().toPlainString().replace('.', ',')

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
            .replace(dateRegex, " ")
            .replace(longDigitRegex, " ")
            .replace(explicitPhraseRegex, " ")
            .replace(priceWithCurrencyRegex, " ")
            .replace(numberRegex, " ")

        return cleaned
            .lines()
            .map { it.trim().trim(' ', ',', '.', ';', ':', '-') }
            .filter { it.isNotBlank() && it.lowercase() !in noiseWords && !rubleUnitRegex.containsMatchIn(it) }
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
        val amountRegex = Regex("""(?iu)(\d+(?:\.\d+)?)[ \t]*(кг|kg|г|g|r|мл|ml|л|l)(?!\p{L})""")
        val rangeAmountRegex = Regex(
            """(?iu)(\d+(?:\.\d+)?)\s*[-–—]\s*(\d+(?:\.\d+)?)\s*(кг|kg|г|g|r|мл|ml|л|l)(?!\p{L})""",
        )
        val explicitUnitPriceRegex = Regex(
            """(?iu)цена[ \t]+за[ \t]+1[ \t]*(кг|kg|л|l)[ \t]*:[ \t]*(\d+(?:\.\d{1,2})?)[ \t]*(?:₽|руб\.?|р\.?|rub\.?|r\.?)(?!\p{L})""",
        )
        val priceWithCurrencyRegex = Regex(
            """(?iu)(\d+(?:\.\d+)?)[ \t]*(₽|руб\.?|р\.?|rub\.?|r\.?)(?!\p{L})""",
        )
        val percentRegex = Regex("""\d+(?:\.\d+)?\s*%""")
        val rubleUnitRegex = Regex(
            """(?iu)(?<![\p{L}\d])(?:руб|py6|шт|wt|ut)(?![\p{L}\d])(?:\s*[./]\s*\S+)?""",
        )
        val noiseWords = setOf(
            "объём", "объем", "вес", "цена", "за", "акция", "скидка",
            "штрихкод", "штрих", "код", "товар", "продукт",
            "кг", "kg", "мл", "ml", "руб", "rub", "py6", "шт", "wt", "ut",
            "volume", "weight", "price", "action",
        )
    }
}
