package com.pricelens.core.util

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class PriceFormatTest {
    @Test
    fun moneyUsesRussianDecimalSeparatorAndTwoDigits() {
        assertThat(formatMoney(BigDecimal("685.71"))).isEqualTo("685,71")
        assertThat(formatMoney(BigDecimal("129.9"))).isEqualTo("129,90")
    }

    @Test
    fun moneyFormatsZero() {
        assertThat(formatMoney(BigDecimal.ZERO)).isEqualTo("0,00")
    }

    @Test
    fun moneyDoesNotGroupThousands() {
        assertThat(formatMoney(BigDecimal("1000"))).isEqualTo("1000,00")
    }

    @Test
    fun amountDropsTrailingZeros() {
        assertThat(formatAmount(BigDecimal("350"))).isEqualTo("350")
        assertThat(formatAmount(BigDecimal("0.95"))).isEqualTo("0,95")
        assertThat(formatAmount(BigDecimal("1.00"))).isEqualTo("1")
    }
}
