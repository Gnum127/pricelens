package com.pricelens.data.local.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pricelens.domain.model.UnitPrice
import com.pricelens.domain.model.WeightUnit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

@RunWith(AndroidJUnit4::class)
class PriceTagDaoAndroidTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: PriceTagDao

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.priceTagDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun writesAndReadsEveryEntityFieldThroughConverters() = runBlocking {
        dao.upsertSession(session(100L, 100L))
        val entity = entity(id = "item", position = 0)

        dao.upsertItems(listOf(entity))

        assertEquals(entity, dao.getItems(CURRENT_SESSION_ID).single())
    }

    @Test
    fun preservesExplicitOnlyItemWithNullableFields() = runBlocking {
        dao.upsertSession(session(100L, 100L))
        val entity = entity(id = "explicit", position = 0).copy(
            name = "Цена",
            packageAmount = null,
            packageUnit = null,
            price = null,
            unitPriceValue = BigDecimal("685.71"),
            sourceText = "Цена за 1 кг: 685,71 ₽",
        )

        dao.upsertItems(listOf(entity))

        assertEquals(entity, dao.getItems(CURRENT_SESSION_ID).single())
    }

    @Test
    fun replaceCurrentItemsRemovesOldRowsAndUpdatesSession() = runBlocking {
        dao.upsertSession(session(100L, 100L))
        dao.upsertItems(listOf(entity(id = "old", position = 0)))

        dao.replaceCurrentItems(
            session(100L, 200L),
            listOf(entity(id = "first", position = 0), entity(id = "second", position = 1)),
        )

        assertEquals(session(100L, 200L), dao.getSession(CURRENT_SESSION_ID))
        assertEquals(
            listOf("first", "second"),
            dao.getItems(CURRENT_SESSION_ID).map { it.id },
        )
        assertEquals(listOf(0, 1), dao.getItems(CURRENT_SESSION_ID).map { it.position })
    }

    @Test
    fun getItemsOrdersByPositionRegardlessOfInsertionOrder() = runBlocking {
        dao.upsertSession(session(100L, 100L))
        dao.upsertItems(
            listOf(
                entity(id = "c", position = 2),
                entity(id = "a", position = 0),
                entity(id = "b", position = 1),
            ),
        )

        assertEquals(
            listOf("a", "b", "c"),
            dao.getItems(CURRENT_SESSION_ID).map { it.id },
        )
    }

    @Test
    fun brokenEnumValuesAreConvertedToNull() = runBlocking {
        dao.upsertSession(session(100L, 100L))
        database.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO product_item
            (id, sessionId, position, name, packageAmount, packageUnit, price, unitPriceValue, unitPriceUnit, sourceText)
            VALUES ('broken', '${CURRENT_SESSION_ID}', 0, 'Бит', NULL, 'BOGUS', NULL, '1.00', 'KILOGRAM', NULL)
            """.trimIndent(),
        )

        val stored = dao.getItems(CURRENT_SESSION_ID).single()

        assertNull(stored.packageAmount)
        assertNull(stored.packageUnit)
        assertNull(stored.price)
        assertEquals(UnitPrice.Unit.KILOGRAM, stored.unitPriceUnit)
        assertNull(stored.sourceText)
    }

    private fun session(createdAt: Long, updatedAt: Long) =
        ComparisonSessionEntity(CURRENT_SESSION_ID, createdAt, updatedAt)

    private fun entity(id: String, position: Int) = ProductItemEntity(
        id = id,
        sessionId = CURRENT_SESSION_ID,
        position = position,
        name = "Творог",
        packageAmount = BigDecimal("350"),
        packageUnit = WeightUnit.GRAM,
        price = BigDecimal("240.00"),
        unitPriceValue = BigDecimal("685.71"),
        unitPriceUnit = UnitPrice.Unit.KILOGRAM,
        sourceText = "350 г 240 ₽",
    )
}
