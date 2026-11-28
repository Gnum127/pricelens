package com.pricelens.data.camera

import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import java.io.File

interface CameraController {
    suspend fun bind(lifecycleOwner: LifecycleOwner, previewView: PreviewView)
    /** Сохраняет один снимок в приватный временный файл; true, если кадр записан. */
    suspend fun captureToFile(file: File): Boolean
    fun unbind()
}
