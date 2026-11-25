package com.pricelens.di

import android.content.Context
import androidx.room.Room
import com.pricelens.data.local.db.AppDatabase
import com.pricelens.data.local.db.PriceTagDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "pricelens.db").build()

    @Provides
    fun provideDao(database: AppDatabase): PriceTagDao = database.priceTagDao()
}
