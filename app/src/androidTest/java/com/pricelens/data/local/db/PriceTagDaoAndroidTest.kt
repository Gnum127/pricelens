package com.pricelens.data.local.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pricelens.domain.model.UnitPrice
import com.pricelens.domain.model.UnitPriceSource
import com.pricelens.domain.model.WeightUnit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.util.UUID

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
    fun preservesWeightRangeLabel() = runBlocking {
        dao.upsertSession(session(100L, 100L))
        val entity = entity(id = "range", position = 0).copy(packageAmountLabel = "230-240 г")

        dao.upsertItems(listOf(entity))

        assertEquals(entity, dao.getItems(CURRENT_SESSION_ID).single())
    }

    @Test
    fun preservesCardPrice() = runBlocking {
        dao.upsertSession(session(100L, 100L))
        val entity = entity(id = "card", position = 0).copy(priceByCard = BigDecimal("243"))

        dao.upsertItems(listOf(entity))

        assertEquals(entity, dao.getItems(CURRENT_SESSION_ID).single())
    }

    @Test
    fun migrationFromVersionOneAddsNullableLabelAndKeepsOldRows() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "pricelens-migration-${UUID.randomUUID()}"
        context.deleteDatabase(dbName)
        try {
            createVersionOneDatabase(context, dbName)
            val migrated = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
                .allowMainThreadQueries()
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
            try {
                val legacy = migrated.priceTagDao().getItems(CURRENT_SESSION_ID).single()
                assertEquals("legacy", legacy.id)
                assertNull(legacy.packageAmountLabel)
                assertNull(legacy.priceByCard)
                assertEquals(UnitPriceSource.PACKAGE, legacy.unitPriceSource)
                assertEquals(BigDecimal("350"), legacy.packageAmount)

                migrated.priceTagDao().upsertItems(
                    listOf(entity(id = "range", position = 1).copy(packageAmountLabel = "230-240 г")),
                )
                assertEquals(
                    "230-240 г",
                    migrated.priceTagDao().getItems(CURRENT_SESSION_ID)
                        .first { it.id == "range" }.packageAmountLabel,
                )
            } finally {
                migrated.close()
            }
        } finally {
            context.deleteDatabase(dbName)
        }
    }

    private fun createVersionOneDatabase(context: Context, name: String) {
        val helper = object : SQLiteOpenHelper(context, name, null, 1) {
            override fun onCreate(db: SQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE comparison_session (id TEXT NOT NULL, createdAt INTEGER NOT NULL, " +
                        "updatedAt INTEGER NOT NULL, PRIMARY KEY(id))",
                )
                db.execSQL(
                    "CREATE TABLE product_item (id TEXT NOT NULL, sessionId TEXT NOT NULL, " +
                        "position INTEGER NOT NULL, name TEXT NOT NULL, packageAmount TEXT, " +
                        "packageUnit TEXT, price TEXT, unitPriceValue TEXT NOT NULL, " +
                        "unitPriceUnit TEXT NOT NULL, sourceText TEXT, PRIMARY KEY(id), " +
                        "FOREIGN KEY(sessionId) REFERENCES comparison_session(id) ON DELETE CASCADE)",
                )
                db.execSQL("CREATE INDEX index_product_item_sessionId ON product_item (sessionId)")
            }

            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        helper.writableDatabase.use { db ->
            db.execSQL(
                "INSERT INTO comparison_session (id, createdAt, updatedAt) VALUES ('current', 100, 100)",
            )
            db.execSQL(
                "INSERT INTO product_item (id, sessionId, position, name, packageAmount, " +
                    "packageUnit, price, unitPriceValue, unitPriceUnit, sourceText) " +
                    "VALUES ('legacy', 'current', 0, 'Творог', '350', 'GRAM', '240.00', " +
                    "'685.71', 'KILOGRAM', '350 г 240 ₽')",
            )
        }
        helper.close()
    }

    @Test
    fun garbageUnitPriceSourceFallsBackToPackage() = runBlocking {
        dao.upsertSession(session(100L, 100L))
        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO product_item (id, sessionId, position, name, packageAmount, " +
                "packageAmountLabel, packageUnit, price, priceByCard, unitPriceValue, " +
                "unitPriceUnit, unitPriceSource, sourceText) " +
                "VALUES ('bogus', 'current', 0, 'Творог', '350', NULL, 'GRAM', '240.00', " +
                "NULL, '685.71', 'KILOGRAM', 'BOGUS', NULL)",
        )

        assertEquals(UnitPriceSource.PACKAGE, dao.getItems(CURRENT_SESSION_ID).single().unitPriceSource)
    }

    @Test
    fun migrationFromVersionTwoAddsNullableCardPriceAndKeepsOldRows() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "pricelens-migration-v3-${UUID.randomUUID()}"
        context.deleteDatabase(dbName)
        try {
            createVersionTwoDatabase(context, dbName)
            val migrated = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
                .allowMainThreadQueries()
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
            try {
                val legacy = migrated.priceTagDao().getItems(CURRENT_SESSION_ID).single()
                assertEquals("legacy", legacy.id)
                assertNull(legacy.priceByCard)
                assertEquals(UnitPriceSource.PACKAGE, legacy.unitPriceSource)
                assertEquals("230-240 г", legacy.packageAmountLabel)
                assertEquals(BigDecimal("240.00"), legacy.price)

                migrated.priceTagDao().upsertItems(
                    listOf(entity(id = "card", position = 1).copy(priceByCard = BigDecimal("243"))),
                )
                assertEquals(
                    BigDecimal("243"),
                    migrated.priceTagDao().getItems(CURRENT_SESSION_ID)
                        .first { it.id == "card" }.priceByCard,
                )
            } finally {
                migrated.close()
            }
        } finally {
            context.deleteDatabase(dbName)
        }
    }

    private fun createVersionTwoDatabase(context: Context, name: String) {
        val helper = object : SQLiteOpenHelper(context, name, null, 2) {
            override fun onCreate(db: SQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE comparison_session (id TEXT NOT NULL, createdAt INTEGER NOT NULL, " +
                        "updatedAt INTEGER NOT NULL, PRIMARY KEY(id))",
                )
                db.execSQL(
                    "CREATE TABLE product_item (id TEXT NOT NULL, sessionId TEXT NOT NULL, " +
                        "position INTEGER NOT NULL, name TEXT NOT NULL, packageAmount TEXT, " +
                        "packageAmountLabel TEXT, packageUnit TEXT, price TEXT, " +
                        "unitPriceValue TEXT NOT NULL, unitPriceUnit TEXT NOT NULL, " +
                        "sourceText TEXT, PRIMARY KEY(id), " +
                        "FOREIGN KEY(sessionId) REFERENCES comparison_session(id) ON DELETE CASCADE)",
                )
                db.execSQL("CREATE INDEX index_product_item_sessionId ON product_item (sessionId)")
            }

            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        helper.writableDatabase.use { db ->
            db.execSQL(
                "INSERT INTO comparison_session (id, createdAt, updatedAt) VALUES ('current', 100, 100)",
            )
            db.execSQL(
                "INSERT INTO product_item (id, sessionId, position, name, packageAmount, " +
                    "packageAmountLabel, packageUnit, price, unitPriceValue, unitPriceUnit, sourceText) " +
                    "VALUES ('legacy', 'current', 0, 'Творог', '350', '230-240 г', 'GRAM', " +
                    "'240.00', '685.71', 'KILOGRAM', '350 г 240 ₽')",
            )
        }
        helper.close()
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
