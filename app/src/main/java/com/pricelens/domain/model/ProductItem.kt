package com.pricelens.domain.model

import java.math.BigDecimal

data class ProductItem(
    val id: String,
    val name: String,
    val packageAmount: BigDecimal?,
    val packageAmountLabel: String? = null,
    val packageUnit: WeightUnit?,
    val price: BigDecimal?,
    val unitPrice: UnitPrice,
    val sourceText: String? = null,
)
