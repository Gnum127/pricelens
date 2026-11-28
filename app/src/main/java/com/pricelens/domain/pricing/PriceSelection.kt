package com.pricelens.domain.pricing

import java.math.BigDecimal

/** Цена упаковки, которая участвует в сравнении: меньшая из обычной и цены по карте. */
fun effectivePrice(price: BigDecimal?, priceByCard: BigDecimal?): BigDecimal? =
    listOfNotNull(price, priceByCard)
        .filter { it > BigDecimal.ZERO }
        .minOrNull()

/** Истина, что расчёт идёт именно по цене по карте (при равенстве — по обычной). */
fun isCardPriceUsed(price: BigDecimal?, priceByCard: BigDecimal?): Boolean {
    val regular = price?.takeIf { it > BigDecimal.ZERO }
    val card = priceByCard?.takeIf { it > BigDecimal.ZERO } ?: return false
    return regular == null || card.compareTo(regular) < 0
}
