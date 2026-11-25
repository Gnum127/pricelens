package com.pricelens.data.repository

import com.google.common.truth.Truth.assertThat
import com.pricelens.data.local.db.CURRENT_SESSION_ID
import com.pricelens.domain.model.ProductItem
import com.pricelens.domain.model.UnitPrice
import com.pricelens.domain.model.WeightUnit
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class PriceTagMappingsTest {
    @Test
    fun mapsRegularItemToEntityAndBack() {
        val item = ProductItem(
            id = "regular",
            name = "Сок",
            packageAmount = BigDecimal("950"),
            packageUnit = WeightUnit.MILLILITER,
            price = BigDecimal("119.00"),
            unitPrice = UnitPrice(BigDecimal("125.26"), UnitPrice.Unit.LITER),
            sourceText = "950 мл / 119 ₽",
        )

        val entity = item.toEntity(position = 2)

        assertThat(entity.sessionId).isEqualTo(CURRENT_SESSION_ID)
        assertThat(entity.position).isEqualTo(2)
        assertThat(entity.unitPriceValue.compareTo(BigDecimal("125.26"))).isEqualTo(0)
        assertThat(entity.unitPriceUnit).isEqualTo(UnitPrice.Unit.LITER)
        assertThat(entity.toDomain()).isEqualTo(item)
    }

    @Test
    fun mapsExplicitOnlyItemWithNullablePackageFields() {
        val item = ProductItem(
            id = "explicit",
            name = "Цена",
            packageAmount = null,
            packageUnit = null,
            price = null,
            unitPrice = UnitPrice(BigDecimal("685.71"), UnitPrice.Unit.KILOGRAM),
            sourceText = "Цена за 1 кг: 685,71 ₽",
        )

        val entity = item.toEntity(position = 0)

        assertThat(entity.packageAmount).isNull()
        assertThat(entity.packageUnit).isNull()
        assertThat(entity.price).isNull()
        assertThat(entity.toDomain()).isEqualTo(item)
    }

    @Test
    fun mapsItemWithoutSourceText() {
        val item = item("no-source").copy(sourceText = null)

        val entity = item.toEntity(position = 1)

        assertThat(entity.sourceText).isNull()
        assertThat(entity.toDomain()).isEqualTo(item)
    }

    private fun item(id: String) = ProductItem(
        id = id,
        name = "Творог",
        packageAmount = BigDecimal("350"),
        packageUnit = WeightUnit.GRAM,
        price = BigDecimal("240.00"),
        unitPrice = UnitPrice(BigDecimal("685.71"), UnitPrice.Unit.KILOGRAM),
        sourceText = "350 г 240 ₽",
    )
}
