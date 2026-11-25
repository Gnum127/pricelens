package com.pricelens.data.local.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

@Dao
abstract class PriceTagDao {
    @Query("SELECT * FROM comparison_session WHERE id = :id")
    abstract suspend fun getSession(id: String): ComparisonSessionEntity?

    @Upsert
    abstract suspend fun upsertSession(session: ComparisonSessionEntity)

    @Query("SELECT * FROM product_item WHERE sessionId = :sessionId ORDER BY position")
    abstract suspend fun getItems(sessionId: String): List<ProductItemEntity>

    @Query("DELETE FROM product_item WHERE sessionId = :sessionId")
    abstract suspend fun deleteItems(sessionId: String)

    @Upsert
    abstract suspend fun upsertItems(items: List<ProductItemEntity>)

    @Transaction
    open suspend fun replaceCurrentItems(
        session: ComparisonSessionEntity,
        items: List<ProductItemEntity>,
    ) {
        upsertSession(session)
        deleteItems(session.id)
        upsertItems(items)
    }
}
