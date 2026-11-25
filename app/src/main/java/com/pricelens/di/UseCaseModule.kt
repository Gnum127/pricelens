package com.pricelens.di

import com.pricelens.domain.parser.PriceTagParser
import com.pricelens.domain.pricing.UnitPriceCalculator
import com.pricelens.domain.repository.ImageTextProvider
import com.pricelens.domain.usecase.AddItemUseCase
import com.pricelens.domain.usecase.CompareItemsUseCase
import com.pricelens.domain.usecase.RecognizePriceTagUseCase
import com.pricelens.domain.usecase.RemoveItemUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {
    @Provides
    @Singleton
    fun provideParser(): PriceTagParser = PriceTagParser()

    @Provides
    @Singleton
    fun provideCalculator(): UnitPriceCalculator = UnitPriceCalculator()

    @Provides
    @Singleton
    fun provideRecognizePriceTag(
        imageTextProvider: ImageTextProvider,
        parser: PriceTagParser,
        calculator: UnitPriceCalculator,
    ): RecognizePriceTagUseCase = RecognizePriceTagUseCase(imageTextProvider, parser, calculator)

    @Provides
    @Singleton
    fun provideAddItem(): AddItemUseCase = AddItemUseCase()

    @Provides
    @Singleton
    fun provideRemoveItem(): RemoveItemUseCase = RemoveItemUseCase()

    @Provides
    @Singleton
    fun provideCompareItems(): CompareItemsUseCase = CompareItemsUseCase()
}
