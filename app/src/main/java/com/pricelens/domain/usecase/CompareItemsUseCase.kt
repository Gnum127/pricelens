package com.pricelens.domain.usecase

import com.pricelens.domain.model.ProductItem

data class ComparisonResult(
    val ranked: List<ProductItem>,
    val cheapestId: String?,
)

class CompareItemsUseCase {
    operator fun invoke(items: List<ProductItem>): ComparisonResult {
        if (items.isEmpty()) return ComparisonResult(emptyList(), null)

        val ranked = items.sortedWith(
            compareBy<ProductItem> { it.unitPrice.value }.thenBy { it.id },
        )
        return ComparisonResult(ranked, ranked.first().id)
    }
}
