package com.pricelens.domain.repository

import com.pricelens.domain.model.ProductItem

interface PriceTagRepository {
    suspend fun saveCurrent(items: List<ProductItem>)

    suspend fun loadCurrent(): List<ProductItem>
}
