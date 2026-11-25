package com.pricelens.data.repository

import com.pricelens.data.local.db.ComparisonSessionEntity
import com.pricelens.data.local.db.CURRENT_SESSION_ID
import com.pricelens.data.local.db.PriceTagDao
import com.pricelens.domain.model.ProductItem
import com.pricelens.domain.repository.PriceTagRepository

class PriceTagRepositoryImpl(
    private val dao: PriceTagDao,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : PriceTagRepository {
    override suspend fun saveCurrent(items: List<ProductItem>) {
        val now = clock()
        val createdAt = dao.getSession(CURRENT_SESSION_ID)?.createdAt ?: now
        val session = ComparisonSessionEntity(CURRENT_SESSION_ID, createdAt, now)
        dao.replaceCurrentItems(session, items.mapIndexed { index, item -> item.toEntity(index) })
    }

    override suspend fun loadCurrent(): List<ProductItem> =
        dao.getItems(CURRENT_SESSION_ID).map { it.toDomain() }
}
