package com.pricelens.data.repository

import com.pricelens.data.local.db.CURRENT_SESSION_ID
import com.pricelens.data.local.db.ProductItemEntity
import com.pricelens.domain.model.ProductItem
import com.pricelens.domain.model.UnitPrice

internal fun ProductItem.toEntity(
    position: Int,
    sessionId: String = CURRENT_SESSION_ID,
): ProductItemEntity = ProductItemEntity(
    id = id,
    sessionId = sessionId,
    position = position,
    name = name,
    packageAmount = packageAmount,
    packageUnit = packageUnit,
    price = price,
    unitPriceValue = unitPrice.value,
    unitPriceUnit = unitPrice.displayUnit,
    sourceText = sourceText,
)

internal fun ProductItemEntity.toDomain(): ProductItem = ProductItem(
    id = id,
    name = name,
    packageAmount = packageAmount,
    packageUnit = packageUnit,
    price = price,
    unitPrice = UnitPrice(unitPriceValue, unitPriceUnit),
    sourceText = sourceText,
)
