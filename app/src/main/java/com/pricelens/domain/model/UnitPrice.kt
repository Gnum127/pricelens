package com.pricelens.domain.model

import java.math.BigDecimal

data class UnitPrice(
    val value: BigDecimal,
    val displayUnit: Unit,
) {
    enum class Unit {
        KILOGRAM,
        LITER,
    }
}
