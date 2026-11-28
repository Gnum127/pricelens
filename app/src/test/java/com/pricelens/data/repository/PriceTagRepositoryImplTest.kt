package com.pricelens.data.repository

import com.google.common.truth.Truth.assertThat
import com.pricelens.data.local.db.ComparisonSessionEntity
import com.pricelens.data.local.db.CURRENT_SESSION_ID
import com.pricelens.data.local.db.PriceTagDao
import com.pricelens.data.local.db.ProductItemEntity
import com.pricelens.domain.model.ProductItem
import com.pricelens.domain.model.UnitPrice
import com.pricelens.domain.model.WeightUnit
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.concurrent.atomic.AtomicInteger

class PriceTagRepositoryImplTest {
    private val dao = FakePriceTagDao()

    @Test
    fun saveThenLoadPreservesDomainItems() = runBlocking {
        val repository = PriceTagRepositoryImpl(dao, clock = { 1000L })
        val items = listOf(
            item("first"),
            item("second", "Молоко", "129.90"),
            explicitOnlyItem(),
        )

        repository.saveCurrent(items)

        assertThat(repository.loadCurrent()).isEqualTo(items)
        assertThat(dao.replaceCalls).isEqualTo(1)
    }

    @Test
    fun saveThenLoadPreservesCardPrice() = runBlocking {
        val repository = PriceTagRepositoryImpl(dao, clock = { 1000L })
        val items = listOf(item("card").copy(priceByCard = BigDecimal("243")))

        repository.saveCurrent(items)

        assertThat(repository.loadCurrent()).isEqualTo(items)
    }

    @Test
    fun firstSaveCreatesSessionWithBothTimestampsFromSingleClockRead() = runBlocking {
        val reads = AtomicInteger()
        val repository = PriceTagRepositoryImpl(dao, clock = { 1000L + reads.getAndIncrement() })

        repository.saveCurrent(listOf(item("first")))

        assertThat(dao.getSession(CURRENT_SESSION_ID)).isEqualTo(
            ComparisonSessionEntity(CURRENT_SESSION_ID, 1000L, 1000L),
        )
        assertThat(reads.get()).isEqualTo(1)
    }

    @Test
    fun saveReplacesPreviousItemsAndRefreshesSession() = runBlocking {
        val repository = PriceTagRepositoryImpl(dao, clock = { 1000L })
        repository.saveCurrent(listOf(item("old")))

        val laterRepository = PriceTagRepositoryImpl(dao, clock = { 2000L })
        laterRepository.saveCurrent(listOf(item("new")))

        assertThat(dao.getItems(CURRENT_SESSION_ID).map { it.id }).containsExactly("new")
        assertThat(dao.getSession(CURRENT_SESSION_ID)).isEqualTo(
            ComparisonSessionEntity(CURRENT_SESSION_ID, 1000L, 2000L),
        )
    }

    @Test
    fun saveEmptyListClearsPreviouslyStoredItems() = runBlocking {
        val repository = PriceTagRepositoryImpl(dao, clock = { 1000L })
        repository.saveCurrent(listOf(item("old")))

        PriceTagRepositoryImpl(dao, clock = { 2000L }).saveCurrent(emptyList())

        assertThat(repository.loadCurrent()).isEmpty()
        assertThat(dao.getSession(CURRENT_SESSION_ID)).isEqualTo(
            ComparisonSessionEntity(CURRENT_SESSION_ID, 1000L, 2000L),
        )
    }

    @Test
    fun loadReturnsEmptyListBeforeFirstSave() = runBlocking {
        assertThat(PriceTagRepositoryImpl(dao).loadCurrent()).isEmpty()
    }

    @Test
    fun saveAssignsSequentialPositionsAndLoadKeepsOrdering() = runBlocking {
        val repository = PriceTagRepositoryImpl(dao, clock = { 1000L })
        repository.saveCurrent(listOf(item("a"), item("b"), item("c")))

        assertThat(dao.getItems(CURRENT_SESSION_ID).map { it.position }).containsExactly(0, 1, 2).inOrder()
        assertThat(repository.loadCurrent().map { it.id }).containsExactly("a", "b", "c").inOrder()
    }

    private fun item(id: String, name: String = "Творог", value: String = "685.71"): ProductItem =
        ProductItem(
            id = id,
            name = name,
            packageAmount = BigDecimal("350"),
            packageUnit = WeightUnit.GRAM,
            price = BigDecimal("240.00"),
            unitPrice = UnitPrice(BigDecimal(value), UnitPrice.Unit.KILOGRAM),
            sourceText = "raw $id",
        )

    private fun explicitOnlyItem(): ProductItem = ProductItem(
        id = "explicit",
        name = "Цена",
        packageAmount = null,
        packageUnit = null,
        price = null,
        unitPrice = UnitPrice(BigDecimal("685.71"), UnitPrice.Unit.KILOGRAM),
        sourceText = "Цена за 1 кг: 685,71 ₽",
    )

    private class FakePriceTagDao : PriceTagDao() {
        var replaceCalls: Int = 0
        private var session: ComparisonSessionEntity? = null
        private val stored = mutableListOf<ProductItemEntity>()

        override suspend fun getSession(id: String): ComparisonSessionEntity? =
            session?.takeIf { it.id == id }

        override suspend fun upsertSession(session: ComparisonSessionEntity) {
            this.session = session
        }

        override suspend fun getItems(sessionId: String): List<ProductItemEntity> =
            stored.filter { it.sessionId == sessionId }.sortedBy { it.position }

        override suspend fun deleteItems(sessionId: String) {
            stored.removeAll { it.sessionId == sessionId }
        }

        override suspend fun upsertItems(items: List<ProductItemEntity>) {
            val ids = items.map { it.id }.toSet()
            stored.removeAll { it.id in ids }
            stored.addAll(items)
        }

        override suspend fun replaceCurrentItems(
            session: ComparisonSessionEntity,
            items: List<ProductItemEntity>,
        ) {
            replaceCalls++
            super.replaceCurrentItems(session, items)
        }
    }
}
