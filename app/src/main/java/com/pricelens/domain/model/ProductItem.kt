package com.pricelens.domain.model

import java.math.BigDecimal

data class ProductItem(
    val id: String,
    val name: String,
    val packageAmount: BigDecimal?,
    val packageAmountLabel: String? = null,
    val packageUnit: WeightUnit?,
    val price: BigDecimal?,
    val priceByCard: BigDecimal? = null,
    val unitPrice: UnitPrice,
    val unitPriceSource: UnitPriceSource = UnitPriceSource.PACKAGE,
    val sourceText: String? = null,
)
