package com.pricelens.di

import com.pricelens.data.local.db.PriceTagDao
import com.pricelens.data.repository.PriceTagRepositoryImpl
import com.pricelens.domain.repository.PriceTagRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    @Provides
    @Singleton
    fun providePriceTagRepository(dao: PriceTagDao): PriceTagRepository = PriceTagRepositoryImpl(dao)
}
