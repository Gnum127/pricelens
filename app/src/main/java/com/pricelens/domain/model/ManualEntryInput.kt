package com.pricelens.domain.model

/** Значения полей диалога ручного ввода: количество вводится числом, единица выбирается отдельно. */
data class ManualEntryInput(
    val name: String,
    val amount: String,
    val unit: ManualUnit,
    val price: String,
    val priceByCard: String,
)
