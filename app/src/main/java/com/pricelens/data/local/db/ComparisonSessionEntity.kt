package com.pricelens.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

const val CURRENT_SESSION_ID = "current"

@Entity(tableName = "comparison_session")
data class ComparisonSessionEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val updatedAt: Long,
)
