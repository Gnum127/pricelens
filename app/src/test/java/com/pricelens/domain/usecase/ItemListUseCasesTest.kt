package com.pricelens.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.pricelens.domain.model.ProductItem
import com.pricelens.domain.model.UnitPrice
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class ItemListUseCasesTest {
    private val addItem = AddItemUseCase()
    private val removeItem = RemoveItemUseCase()

    @Test
    fun addsItemWhenFifthSlotIsFree() {
        val items = (1..4).map(::item)

        val result = addItem(items, item(5))

        assertThat(result).isNotNull()
        assertThat(result!!.map { it.id }).containsExactly("1", "2", "3", "4", "5").inOrder()
    }

    @Test
    fun rejectsSixthItemWithoutChangingInput() {
        val items = (1..5).map(::item)

        val result = addItem(items, item(6))

        assertThat(result).isNull()
        assertThat(items).hasSize(5)
        assertThat(items.map { it.id }).containsExactly("1", "2", "3", "4", "5").inOrder()
    }

    @Test
    fun rejectsDuplicateId() {
        val items = listOf(item("a"), item("b"))

        assertThat(addItem(items, item("a", "999"))).isNull()
    }

    @Test
    fun removesItemById() {
        val items = listOf(item("a"), item("b"), item("c"))

        val result = removeItem(items, "b")

        assertThat(result.map { it.id }).containsExactly("a", "c").inOrder()
    }

    @Test
    fun removingMissingIdKeepsList() {
        val items = listOf(item("a"), item("b"))

        val result = removeItem(items, "missing")

        assertThat(result.map { it.id }).containsExactly("a", "b").inOrder()
    }

    private fun item(number: Int): ProductItem = item(number.toString())

    private fun item(id: String, value: String = "100"): ProductItem = ProductItem(
        id = id,
        name = "Product $id",
        packageAmount = null,
        packageUnit = null,
        price = null,
        unitPrice = UnitPrice(BigDecimal(value), UnitPrice.Unit.KILOGRAM),
    )
}
