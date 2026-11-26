package com.pricelens.domain.pricing

import com.google.common.truth.Truth.assertThat
import com.pricelens.domain.model.ParsedPriceTag
import com.pricelens.domain.model.UnitPrice
import com.pricelens.domain.model.WeightUnit
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.math.BigDecimal

class UnitPriceCalculatorTest {
    private val calculator = UnitPriceCalculator()

    @Test
    fun calculatesPricePerKgFromParsedTag() {
        val parsed = ParsedPriceTag(
            packageAmount = BigDecimal("350"),
            packageUnit = WeightUnit.GRAM,
            price = BigDecimal("240"),
        )

        val result = calculator.calculate(parsed)

        assertThat(result).isNotNull()
        assertThat(result!!.displayUnit).isEqualTo(UnitPrice.Unit.KILOGRAM)
        assertDecimal(result.value, "685.71")
    }

    @Test
    fun calculatesPricePerLiterFromParsedTag() {
        val parsed = ParsedPriceTag(
            packageAmount = BigDecimal("950"),
            packageUnit = WeightUnit.MILLILITER,
            price = BigDecimal("119"),
        )

        val result = calculator.calculate(parsed)

        assertThat(result).isNotNull()
        assertThat(result!!.displayUnit).isEqualTo(UnitPrice.Unit.LITER)
        assertDecimal(result.value, "125.26")
    }

    @Test
    fun calculatesPricePerLiterWhenAmountIsExactlyOneLiter() {
        val parsed = ParsedPriceTag(
            packageAmount = BigDecimal("1000"),
            packageUnit = WeightUnit.MILLILITER,
            price = BigDecimal("129.90"),
        )

        val result = calculator.calculate(parsed)

        assertThat(result).isNotNull()
        assertThat(result!!.displayUnit).isEqualTo(UnitPrice.Unit.LITER)
        assertDecimal(result.value, "129.90")
    }

    @Test
    fun prefersExplicitUnitPriceAndNormalizesScale() {
        val parsed = ParsedPriceTag(
            packageAmount = BigDecimal("350"),
            packageUnit = WeightUnit.GRAM,
            price = BigDecimal("240"),
            explicitUnitPrice = BigDecimal("685.7"),
            explicitUnitPriceUnit = UnitPrice.Unit.KILOGRAM,
        )

        val result = calculator.calculate(parsed)

        assertThat(result).isNotNull()
        assertThat(result!!.displayUnit).isEqualTo(UnitPrice.Unit.KILOGRAM)
        assertDecimal(result.value, "685.70")
        assertThat(result.value.scale()).isEqualTo(2)
    }

    @Test
    fun explicitUnitPriceWithoutUnitIsRejected() {
        val parsed = ParsedPriceTag(
            explicitUnitPrice = BigDecimal("100"),
            explicitUnitPriceUnit = null,
        )

        assertThat(calculator.calculate(parsed)).isNull()
    }

    @Test
    fun nonPositiveExplicitUnitPriceIsRejected() {
        val zero = ParsedPriceTag(
            explicitUnitPrice = BigDecimal.ZERO,
            explicitUnitPriceUnit = UnitPrice.Unit.KILOGRAM,
        )
        val negative = ParsedPriceTag(
            explicitUnitPrice = BigDecimal("-1"),
            explicitUnitPriceUnit = UnitPrice.Unit.LITER,
        )

        assertThat(calculator.calculate(zero)).isNull()
        assertThat(calculator.calculate(negative)).isNull()
    }

    @Test
    fun incompleteParsedTagIsRejected() {
        assertThat(calculator.calculate(ParsedPriceTag())).isNull()
        assertThat(
            calculator.calculate(
                ParsedPriceTag(price = BigDecimal("100"), packageUnit = WeightUnit.GRAM),
            ),
        ).isNull()
    }

    @Test
    fun calculatePerKgRejectsNonPositiveInputs() {
        assertThat(calculator.calculatePerKg(BigDecimal("100"), BigDecimal.ZERO)).isNull()
        assertThat(calculator.calculatePerKg(BigDecimal.ZERO, BigDecimal("350"))).isNull()
        assertThat(calculator.calculatePerKg(BigDecimal("-1"), BigDecimal("350"))).isNull()
        assertThat(calculator.calculatePerKg(BigDecimal("100"), BigDecimal("-1"))).isNull()
    }

    @Test
    fun calculatePerLiterRejectsNonPositiveInputs() {
        assertThat(calculator.calculatePerLiter(BigDecimal("100"), BigDecimal.ZERO)).isNull()
        assertThat(calculator.calculatePerLiter(BigDecimal.ZERO, BigDecimal("950"))).isNull()
        assertThat(calculator.calculatePerLiter(BigDecimal("-1"), BigDecimal("950"))).isNull()
        assertThat(calculator.calculatePerLiter(BigDecimal("100"), BigDecimal("-1"))).isNull()
    }

    @ParameterizedTest
    @CsvSource(
        "1, 240, GRAM, 240000.00, KILOGRAM",
        "999, 240, GRAM, 240.24, KILOGRAM",
        "1001, 240, GRAM, 239.76, KILOGRAM",
        "1, 0.01, MILLILITER, 10.00, LITER",
        "1, 1000000000, GRAM, 1000000000000.00, KILOGRAM",
    )
    fun boundaryAmountsAndPricesAreCalculatedWithoutOverflow(
        amount: String,
        price: String,
        unit: String,
        expected: String,
        expectedUnit: String,
    ) {
        val result = calculator.calculate(
            ParsedPriceTag(
                packageAmount = BigDecimal(amount),
                packageUnit = WeightUnit.valueOf(unit),
                price = BigDecimal(price),
            ),
        )

        assertThat(result).isNotNull()
        assertThat(result!!.displayUnit).isEqualTo(UnitPrice.Unit.valueOf(expectedUnit))
        assertDecimal(result.value, expected)
    }

    @Test
    fun roundsHalfUpToKopeks() {
        val result = calculator.calculatePerKg(BigDecimal("1"), BigDecimal("6"))

        assertThat(result).isNotNull()
        assertDecimal(result!!.value, "166.67")
    }

    private fun assertDecimal(actual: BigDecimal, expected: String) {
        assertThat(actual.compareTo(BigDecimal(expected))).isEqualTo(0)
    }
}
