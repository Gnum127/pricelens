package com.pricelens.domain.repository

import com.pricelens.domain.model.RawTextResult

fun interface ImageTextProvider {
    suspend fun readText(): RawTextResult
}
