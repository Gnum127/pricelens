package com.pricelens.di

import androidx.camera.core.ExperimentalGetImage
import com.pricelens.data.ocr.MlKitTextRecognizer
import com.pricelens.data.ocr.TextRecognizer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object OcrModule {
    @Provides
    @Singleton
    @ExperimentalGetImage
    fun provideTextRecognizer(): TextRecognizer = MlKitTextRecognizer()
}
