package com.pricelens.data.camera

import com.pricelens.data.ocr.TextRecognizer
import com.pricelens.domain.model.RawTextResult
import com.pricelens.domain.repository.ImageTextProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CameraImageTextProvider(
    private val cameraController: CameraController,
    private val textRecognizer: TextRecognizer,
    private val permissionChecker: CameraPermissionChecker,
) : ImageTextProvider {
    override suspend fun readText(): RawTextResult {
        if (!permissionChecker.isGranted()) return RawTextResult.PermissionDenied

        val image = cameraController.takePicture() ?: return RawTextResult.CameraError
        try {
            val text = withContext(Dispatchers.IO) { textRecognizer.recognize(image) }
            return if (text.isNullOrBlank()) RawTextResult.EmptyText else RawTextResult.Success(text)
        } finally {
            image.close()
        }
    }
}
