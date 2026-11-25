package com.pricelens.domain.usecase

import com.pricelens.domain.model.ProductItem

class RemoveItemUseCase {
    operator fun invoke(items: List<ProductItem>, id: String): List<ProductItem> =
        items.filterNot { it.id == id }
}
