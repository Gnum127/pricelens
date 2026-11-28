package com.pricelens.data.repository

import com.pricelens.data.local.db.CURRENT_SESSION_ID
import com.pricelens.data.local.db.ProductItemEntity
import com.pricelens.domain.model.ProductItem
import com.pricelens.domain.model.UnitPrice
import com.pricelens.domain.model.UnitPriceSource

internal fun ProductItem.toEntity(
    position: Int,
    sessionId: String = CURRENT_SESSION_ID,
): ProductItemEntity = ProductItemEntity(
    id = id,
    sessionId = sessionId,
    position = position,
    name = name,
    packageAmount = packageAmount,
    packageAmountLabel = packageAmountLabel,
    packageUnit = packageUnit,
    price = price,
    priceByCard = priceByCard,
    unitPriceValue = unitPrice.value,
    unitPriceUnit = unitPrice.displayUnit,
    unitPriceSource = unitPriceSource,
    sourceText = sourceText,
)

internal fun ProductItemEntity.toDomain(): ProductItem = ProductItem(
    id = id,
    name = name,
    packageAmount = packageAmount,
    packageAmountLabel = packageAmountLabel,
    packageUnit = packageUnit,
    price = price,
    priceByCard = priceByCard,
    unitPrice = UnitPrice(unitPriceValue, unitPriceUnit),
    unitPriceSource = unitPriceSource ?: UnitPriceSource.PACKAGE,
    sourceText = sourceText,
)
