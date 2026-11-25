package com.pricelens.di

import android.content.Context
import com.pricelens.data.camera.AndroidCameraPermissionChecker
import com.pricelens.data.camera.CameraController
import com.pricelens.data.camera.CameraImageTextProvider
import com.pricelens.data.camera.CameraPermissionChecker
import com.pricelens.data.camera.CameraXCameraController
import com.pricelens.data.ocr.TextRecognizer
import com.pricelens.domain.repository.ImageTextProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CameraModule {
    @Provides
    @Singleton
    fun provideCameraController(@ApplicationContext context: Context): CameraController =
        CameraXCameraController(context)

    @Provides
    @Singleton
    fun providePermissionChecker(@ApplicationContext context: Context): CameraPermissionChecker =
        AndroidCameraPermissionChecker(context)

    @Provides
    @Singleton
    fun provideImageTextProvider(
        cameraController: CameraController,
        textRecognizer: TextRecognizer,
        permissionChecker: CameraPermissionChecker,
    ): ImageTextProvider = CameraImageTextProvider(cameraController, textRecognizer, permissionChecker)
}
