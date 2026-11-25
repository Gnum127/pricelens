package com.pricelens.data.camera

import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner

interface CameraController {
    suspend fun bind(lifecycleOwner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider)
    suspend fun takePicture(): ImageProxy?
    fun unbind()
}
