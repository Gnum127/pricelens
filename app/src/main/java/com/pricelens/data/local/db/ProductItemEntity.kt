package com.pricelens.data.local.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.pricelens.domain.model.UnitPrice
import com.pricelens.domain.model.UnitPriceSource
import com.pricelens.domain.model.WeightUnit
import java.math.BigDecimal

@Entity(
    tableName = "product_item",
    foreignKeys = [
        ForeignKey(
            entity = ComparisonSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class ProductItemEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val position: Int,
    val name: String,
    val packageAmount: BigDecimal?,
    val packageAmountLabel: String? = null,
    val packageUnit: WeightUnit?,
    val price: BigDecimal?,
    val priceByCard: BigDecimal? = null,
    val unitPriceValue: BigDecimal,
    val unitPriceUnit: UnitPrice.Unit,
    // Nullable намеренно: MIGRATION_2_3 создаёт колонку без NOT NULL, а MIGRATION_3_4
    // пересоздаёт таблицу под эту схему. Конвертер подставляет PACKAGE для null.
    val unitPriceSource: UnitPriceSource? = UnitPriceSource.PACKAGE,
    val sourceText: String?,
)
