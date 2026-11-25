package com.pricelens.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.pricelens.domain.model.ProductItem
import com.pricelens.domain.model.UnitPrice
import com.pricelens.domain.parser.PriceTagParser
import com.pricelens.domain.pricing.UnitPriceCalculator
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class CompareItemsUseCaseTest {
    private val compareItems = CompareItemsUseCase()
    private val parser = PriceTagParser()
    private val calculator = UnitPriceCalculator()

    @Test
    fun ranksByUnitPriceAndBreaksTiesById() {
        val items = listOf(
            item("c", "100"),
            item("b", "200"),
            item("a", "100"),
        )

        val result = compareItems(items)

        assertThat(result.ranked.map { it.id }).containsExactly("a", "c", "b").inOrder()
        assertThat(result.cheapestId).isEqualTo("a")
    }

    @Test
    fun comparesRealPriceTagsFromOcrNotes() {
        val first = product("first", "Творог 5% 350 г 240,00 ₽")
        val second = product("second", "Творог 320 г 212 р")

        val result = compareItems(listOf(first, second))

        assertDecimal(first.unitPrice.value, "685.71")
        assertThat(first.unitPrice.displayUnit).isEqualTo(UnitPrice.Unit.KILOGRAM)
        assertDecimal(second.unitPrice.value, "662.50")
        assertThat(second.unitPrice.displayUnit).isEqualTo(UnitPrice.Unit.KILOGRAM)
        assertThat(result.cheapestId).isEqualTo("second")
        assertThat(result.ranked.map { it.id }).containsExactly("second", "first").inOrder()
    }

    @Test
    fun emptyListHasNoCheapestItem() {
        val result = compareItems(emptyList())

        assertThat(result.ranked).isEmpty()
        assertThat(result.cheapestId).isNull()
    }

    @Test
    fun comparisonUsesNumericValueNotFormattedString() {
        val items = listOf(
            item("expensive", "100.10"),
            item("cheap", "99.90"),
        )

        val result = compareItems(items)

        assertThat(result.cheapestId).isEqualTo("cheap")
    }

    private fun item(id: String, value: String): ProductItem = ProductItem(
        id = id,
        name = "Product $id",
        packageAmount = null,
        packageUnit = null,
        price = null,
        unitPrice = UnitPrice(BigDecimal(value), UnitPrice.Unit.KILOGRAM),
    )

    private fun product(id: String, text: String): ProductItem {
        val parsed = parser.parse(text) ?: throw AssertionError("unparseable: $text")
        val unitPrice = calculator.calculate(parsed) ?: throw AssertionError("no unit price: $text")
        return ProductItem(
            id = id,
            name = parsed.name ?: id,
            packageAmount = parsed.packageAmount,
            packageUnit = parsed.packageUnit,
            price = parsed.price,
            unitPrice = unitPrice,
            sourceText = text,
        )
    }

    private fun assertDecimal(actual: BigDecimal, expected: String) {
        assertThat(actual.compareTo(BigDecimal(expected))).isEqualTo(0)
    }
}
