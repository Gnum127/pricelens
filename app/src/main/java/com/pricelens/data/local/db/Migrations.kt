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

/**
 * Пересоздаёт таблицу под nullable-колонку `unitPriceSource`. В БД, свежесозданной старой v3-схемой,
 * колонка была NOT NULL; в БД, обновлённой с v1/v2, — nullable. Room сравнивает nullable-флаг
 * при валидации, поэтому оба варианта приводятся к одной целевой схеме без потери строк.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `product_item_v4` (
                `id` TEXT NOT NULL,
                `sessionId` TEXT NOT NULL,
                `position` INTEGER NOT NULL,
                `name` TEXT NOT NULL,
                `packageAmount` TEXT,
                `packageAmountLabel` TEXT,
                `packageUnit` TEXT,
                `price` TEXT,
                `priceByCard` TEXT,
                `unitPriceValue` TEXT NOT NULL,
                `unitPriceUnit` TEXT NOT NULL,
                `unitPriceSource` TEXT,
                `sourceText` TEXT,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`sessionId`) REFERENCES `comparison_session`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO `product_item_v4` (
                `id`, `sessionId`, `position`, `name`, `packageAmount`, `packageAmountLabel`,
                `packageUnit`, `price`, `priceByCard`, `unitPriceValue`, `unitPriceUnit`,
                `unitPriceSource`, `sourceText`
            )
            SELECT `id`, `sessionId`, `position`, `name`, `packageAmount`, `packageAmountLabel`,
                `packageUnit`, `price`, `priceByCard`, `unitPriceValue`, `unitPriceUnit`,
                CASE WHEN `unitPriceSource` IN ('PACKAGE', 'EXPLICIT')
                    THEN `unitPriceSource` ELSE 'PACKAGE' END,
                `sourceText`
            FROM `product_item`
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE `product_item`")
        db.execSQL("ALTER TABLE `product_item_v4` RENAME TO `product_item`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_product_item_sessionId` ON `product_item` (`sessionId`)")
    }
}
