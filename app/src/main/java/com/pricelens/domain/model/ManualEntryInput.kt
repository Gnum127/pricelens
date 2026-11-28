package com.pricelens.domain.model

/** Значения полей диалога ручного ввода; строки оставляются как их видит пользователь. */
data class ManualEntryInput(
    val name: String,
    val amount: String,
    val price: String,
    val priceByCard: String,
)
