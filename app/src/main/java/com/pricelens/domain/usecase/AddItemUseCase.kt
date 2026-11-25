package com.pricelens.domain.usecase

import com.pricelens.domain.model.ProductItem

class AddItemUseCase {
    operator fun invoke(items: List<ProductItem>, item: ProductItem): List<ProductItem>? {
        if (items.size >= MAX_ITEMS) return null
        if (items.any { it.id == item.id }) return null
        return items + item
    }

    companion object {
        const val MAX_ITEMS = 5
    }
}
