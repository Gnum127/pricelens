package com.pricelens.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.pricelens.domain.model.ManualEntryInput
import com.pricelens.domain.model.ManualUnit
import com.pricelens.domain.model.UnitPrice
import com.pricelens.domain.model.UnitPriceSource
import com.pricelens.domain.model.WeightUnit
import com.pricelens.domain.parser.PriceTagParser
import com.pricelens.domain.pricing.UnitPriceCalculator
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.math.BigDecimal

class CreateManualItemUseCaseTest {
    private val useCase = CreateManualItemUseCase(PriceTagParser(), UnitPriceCalculator())

    @Test
    fun gramsAndPriceAreCalculatedPerKilogram() {
        val item = useCase("id", input(name = "Творог", amount = "350", price = "240"))

        assertThat(item).isNotNull()
        assertThat(item!!.name).isEqualTo("Творог")
        assertThat(item.packageAmount).isEqualTo(BigDecimal("350"))
        assertThat(item.packageUnit).isEqualTo(WeightUnit.GRAM)
        assertThat(item.price).isEqualTo(BigDecimal("240"))
        assertThat(item.unitPrice).isEqualTo(UnitPrice(BigDecimal("685.71"), UnitPrice.Unit.KILOGRAM))
        assertThat(item.unitPriceSource).isEqualTo(UnitPriceSource.PACKAGE)
    }

    @Test
    fun millilitersAreNotMistakenForGrams() {
        val item = useCase(
            "id",
            input(name = "Молоко", amount = "950", unit = ManualUnit.MILLILITER, price = "119"),
        )

        assertThat(item).isNotNull()
        assertThat(item!!.packageUnit).isEqualTo(WeightUnit.MILLILITER)
        assertThat(item.unitPrice).isEqualTo(UnitPrice(BigDecimal("125.26"), UnitPrice.Unit.LITER))
    }

    @Test
    fun kilogramsAreConvertedToGrams() {
        val item = useCase(
            "id",
            input(name = "Крупа", amount = "1,5", unit = ManualUnit.KILOGRAM, price = "300"),
        )

        assertThat(item).isNotNull()
        assertThat(item!!.packageAmount?.compareTo(BigDecimal("1500"))).isEqualTo(0)
        assertThat(item.packageUnit).isEqualTo(WeightUnit.GRAM)
        assertThat(item.unitPrice).isEqualTo(UnitPrice(BigDecimal("200.00"), UnitPrice.Unit.KILOGRAM))
    }

    @Test
    fun litersAreConvertedToMilliliters() {
        val item = useCase(
            "id",
            input(name = "Сок", amount = "0,95", unit = ManualUnit.LITER, price = "119"),
        )

        assertThat(item).isNotNull()
        assertThat(item!!.packageAmount?.compareTo(BigDecimal("950"))).isEqualTo(0)
        assertThat(item.packageUnit).isEqualTo(WeightUnit.MILLILITER)
        assertThat(item.unitPrice).isEqualTo(UnitPrice(BigDecimal("125.26"), UnitPrice.Unit.LITER))
    }

    @Test
    fun numbersInNameDoNotOverrideEnteredAmount() {
        val item = useCase(
            "id",
            input(
                name = "Молоко 1 л",
                amount = "950",
                unit = ManualUnit.MILLILITER,
                price = "119",
            ),
        )

        assertThat(item).isNotNull()
        assertThat(item!!.name).isEqualTo("Молоко 1 л")
        assertThat(item.packageAmount).isEqualTo(BigDecimal("950"))
        assertThat(item.unitPrice.value).isEqualTo(BigDecimal("125.26"))
        assertThat(item.unitPrice.displayUnit).isEqualTo(UnitPrice.Unit.LITER)
    }

    @Test
    fun cardPriceIsUsedForComparisonAndKeptSeparately() {
        val item = useCase(
            "id",
            input(name = "Печенье", amount = "200", price = "270", priceByCard = "243"),
        )

        assertThat(item).isNotNull()
        assertThat(item!!.price).isEqualTo(BigDecimal("270"))
        assertThat(item.priceByCard).isEqualTo(BigDecimal("243"))
        assertThat(item.unitPrice.value).isEqualTo(BigDecimal("1215.00"))
    }

    @Test
    fun onlyCardPriceBecomesRegularPrice() {
        val item = useCase("id", input(name = "Печенье", amount = "200", priceByCard = "243"))

        assertThat(item).isNotNull()
        assertThat(item!!.price).isEqualTo(BigDecimal("243"))
        assertThat(item.priceByCard).isNull()
        assertThat(item.unitPrice.value).isEqualTo(BigDecimal("1215.00"))
    }

    @Test
    fun rangeLabelIsPreservedAndUpperBoundIsUsed() {
        val item = useCase("id", input(name = "Шпроты", amount = "230-240", price = "139,99"))

        assertThat(item).isNotNull()
        assertThat(item!!.packageAmountLabel).isEqualTo("230-240 г")
        assertThat(item.packageAmount).isEqualTo(BigDecimal("240"))
        assertThat(item.unitPrice.value).isEqualTo(BigDecimal("583.29"))
    }

    @Test
    fun decimalCommaIsSupported() {
        val item = useCase(
            "id",
            input(name = "Сок", amount = "0,95", unit = ManualUnit.LITER, price = "119,00"),
        )

        assertThat(item).isNotNull()
        assertThat(item!!.packageAmount?.compareTo(BigDecimal("950"))).isEqualTo(0)
        assertThat(item.unitPrice.value).isEqualTo(BigDecimal("125.26"))
    }

    @Test
    fun zeroKilogramsAndLitersAreRejected() {
        assertThat(
            useCase("id", input(amount = "0", unit = ManualUnit.KILOGRAM, price = "240")),
        ).isNull()
        assertThat(
            useCase("id", input(amount = "0", unit = ManualUnit.LITER, price = "240")),
        ).isNull()
    }

    @Test
    fun oneKilogramKeepsPricePerKilogram() {
        val item = useCase("id", input(amount = "1", unit = ManualUnit.KILOGRAM, price = "240"))

        assertThat(item).isNotNull()
        assertThat(item!!.packageAmount?.compareTo(BigDecimal("1000"))).isEqualTo(0)
        assertThat(item.unitPrice).isEqualTo(UnitPrice(BigDecimal("240.00"), UnitPrice.Unit.KILOGRAM))
    }

    @Test
    fun blankNameIsAllowed() {
        val item = useCase("id", input(name = "  ", amount = "350", price = "240"))

        assertThat(item).isNotNull()
        assertThat(item!!.name).isEmpty()
    }

    @Test
    fun missingAmountIsRejected() {
        assertThat(useCase("id", input(name = "Творог", amount = "", price = "240"))).isNull()
    }

    @Test
    fun amountWithoutNumberIsRejected() {
        assertThat(useCase("id", input(name = "Творог", amount = "г", price = "240"))).isNull()
    }

    @Test
    fun missingPriceIsRejected() {
        assertThat(useCase("id", input(name = "Творог", amount = "350"))).isNull()
    }

    @ParameterizedTest
    @ValueSource(strings = ["0", "-10", "-0,01", "abc", ".", "240$", "1 000"])
    fun nonPositiveAndMalformedPricesAreRejected(price: String) {
        assertThat(useCase("id", input(amount = "350", price = price))).isNull()
    }

    @ParameterizedTest
    @ValueSource(strings = ["0", "-10", "-0,01", "abc", "."])
    fun invalidCardPriceIsRejectedEvenWhenRegularPriceIsValid(priceByCard: String) {
        assertThat(
            useCase("id", input(amount = "350", price = "240", priceByCard = priceByCard)),
        ).isNull()
    }

    @Test
    fun tinyPriceIsRoundedToTwoDecimals() {
        val item = useCase("id", input(amount = "350", price = "0,01"))

        assertThat(item).isNotNull()
        assertThat(item!!.unitPrice.value).isEqualTo(BigDecimal("0.03"))
    }

    @Test
    fun veryLargePriceIsCalculatedWithoutOverflow() {
        val item = useCase("id", input(amount = "350", price = "1000000000"))

        assertThat(item).isNotNull()
        assertThat(item!!.price).isEqualTo(BigDecimal("1000000000"))
        assertThat(item.unitPrice.value.compareTo(BigDecimal("2857142857.14"))).isEqualTo(0)
    }

    private fun input(
        name: String = "Творог",
        amount: String = "350",
        unit: ManualUnit = ManualUnit.GRAM,
        price: String = "",
        priceByCard: String = "",
    ) = ManualEntryInput(name, amount, unit, price, priceByCard)
}
