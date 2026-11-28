package com.pricelens.domain.pricing

import com.pricelens.domain.model.ParsedPriceTag
import com.pricelens.domain.model.UnitPrice
import com.pricelens.domain.model.WeightUnit
import java.math.BigDecimal
import java.math.RoundingMode

class UnitPriceCalculator {
    fun calculate(parsed: ParsedPriceTag): UnitPrice? {
        parsed.explicitUnitPrice?.let { value ->
            val unit = parsed.explicitUnitPriceUnit ?: return null
            if (value <= BigDecimal.ZERO) return null
            return UnitPrice(value.setScale(SCALE, RoundingMode.HALF_UP), unit)
        }

        val price = effectivePrice(parsed.price, parsed.priceByCard) ?: return null
        val amount = parsed.packageAmount ?: return null
        val unit = parsed.packageUnit ?: return null

        return when (unit) {
            WeightUnit.GRAM -> calculatePerKg(price, amount)
            WeightUnit.MILLILITER -> calculatePerLiter(price, amount)
        }
    }

    fun calculatePerKg(price: BigDecimal, grams: BigDecimal): UnitPrice? {
        if (price <= BigDecimal.ZERO || grams <= BigDecimal.ZERO) return null
        val value = price.multiply(THOUSAND).divide(grams, SCALE, RoundingMode.HALF_UP)
        return UnitPrice(value, UnitPrice.Unit.KILOGRAM)
    }

    fun calculatePerLiter(price: BigDecimal, milliliters: BigDecimal): UnitPrice? {
        if (price <= BigDecimal.ZERO || milliliters <= BigDecimal.ZERO) return null
        val value = price.multiply(THOUSAND).divide(milliliters, SCALE, RoundingMode.HALF_UP)
        return UnitPrice(value, UnitPrice.Unit.LITER)
    }

    private companion object {
        val THOUSAND = BigDecimal("1000")
        const val SCALE = 2
    }
}
