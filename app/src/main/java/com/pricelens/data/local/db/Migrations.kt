package com.pricelens.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Добавлена подпись веса для ценников с диапазоном («230-240 г»). */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE product_item ADD COLUMN packageAmountLabel TEXT")
    }
}

/** Добавлены вторая цена ценника («по карте») и источник цены за единицу; старые строки сохраняются. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE product_item ADD COLUMN priceByCard TEXT")
        db.execSQL("ALTER TABLE product_item ADD COLUMN unitPriceSource TEXT")
    }
}
