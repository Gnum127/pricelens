package com.pricelens.domain.model

/** Откуда взята цена за 1 кг/л: из явной строки на ценнике или из цены упаковки. */
enum class UnitPriceSource {
    PACKAGE,
    EXPLICIT,
}
