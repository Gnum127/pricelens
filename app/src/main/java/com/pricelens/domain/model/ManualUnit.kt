package com.pricelens.domain.model

/** Единица, выбираемая в диалоге ручного ввода; символы совпадают с ожидаемыми парсером. */
enum class ManualUnit(val symbol: String) {
    GRAM("г"),
    KILOGRAM("кг"),
    MILLILITER("мл"),
    LITER("л"),
}
