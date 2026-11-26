package com.pricelens.di

import android.content.Context
import com.pricelens.data.ocr.RapidOcr
import com.pricelens.data.ocr.TesseractOcr
import com.pricelens.data.ocr.TextRecognizer
import com.pricelens.data.ocr.TwoLayerTextRecognizer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object OcrModule {
    @Provides
    @Singleton
    fun provideTesseractOcr(@ApplicationContext context: Context): TesseractOcr = TesseractOcr(context)

    @Provides
    @Singleton
    fun provideRapidOcr(@ApplicationContext context: Context): RapidOcr = RapidOcr(context)

    @Provides
    @Singleton
    fun provideTextRecognizer(
        tesseract: TesseractOcr,
        rapidOcr: RapidOcr,
    ): TextRecognizer = TwoLayerTextRecognizer(tesseract, rapidOcr)
}
