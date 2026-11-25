package com.pricelens.core.util

import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

private val russianSymbols = DecimalFormatSymbols(Locale("ru"))
private val moneyFormat = DecimalFormat("0.00", russianSymbols)
private val amountFormat = DecimalFormat("0.##", russianSymbols)

fun formatMoney(value: BigDecimal): String = moneyFormat.format(value)

fun formatAmount(value: BigDecimal): String = amountFormat.format(value)
