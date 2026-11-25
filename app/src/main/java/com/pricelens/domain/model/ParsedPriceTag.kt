package com.pricelens.domain.model

import java.math.BigDecimal

data class ParsedPriceTag(
    val name: String? = null,
    val packageAmount: BigDecimal? = null,
    val packageUnit: WeightUnit? = null,
    val price: BigDecimal? = null,
    val explicitUnitPrice: BigDecimal? = null,
    val explicitUnitPriceUnit: UnitPrice.Unit? = null,
    val sourceText: String? = null,
)
