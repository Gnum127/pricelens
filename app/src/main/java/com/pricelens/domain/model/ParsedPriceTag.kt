package com.pricelens.domain.model

import java.math.BigDecimal

data class ParsedPriceTag(
    val name: String? = null,
    val packageAmount: BigDecimal? = null,
    /** Диапазон веса с ценника («230-240 г»); для расчёта берётся верхняя граница. */
    val packageAmountLabel: String? = null,
    val packageUnit: WeightUnit? = null,
    val price: BigDecimal? = null,
    /** Вторая цена ценника («по карте»); в сравнении участвует меньшая из двух. */
    val priceByCard: BigDecimal? = null,
    val explicitUnitPrice: BigDecimal? = null,
    val explicitUnitPriceUnit: UnitPrice.Unit? = null,
    val sourceText: String? = null,
)
