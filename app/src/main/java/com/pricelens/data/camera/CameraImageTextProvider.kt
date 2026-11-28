package com.pricelens.data.camera

import com.pricelens.data.ocr.OcrEngineUnavailableException
import com.pricelens.data.ocr.TextRecognizer
import com.pricelens.domain.model.RawTextResult
import com.pricelens.domain.repository.ImageTextProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class CameraImageTextProvider(
    private val cacheDir: File,
    private val cameraController: CameraController,
    private val textRecognizer: TextRecognizer,
    private val permissionChecker: CameraPermissionChecker,
) : ImageTextProvider {
    override suspend fun readText(): RawTextResult {
        if (!permissionChecker.isGranted()) return RawTextResult.PermissionDenied

        val photo = File(cacheDir, "price-tag-${UUID.randomUUID()}.jpg")
        var cameraReleased = false
        fun releaseCamera() {
            if (cameraReleased) return
            cameraReleased = true
            cameraController.unbind()
        }
        return try {
            if (!cameraController.captureToFile(photo)) return RawTextResult.CameraError
            // Камера освобождается сразу после снимка: держать телефон в рамке во время OCR не нужно.
            releaseCamera()
            val frame = withContext(Dispatchers.IO) { textRecognizer.recognize(photo) }
            if (frame == null || frame.tokens.isEmpty()) {
                RawTextResult.EmptyText
            } else {
                RawTextResult.Success(frame)
            }
        } catch (unavailable: OcrEngineUnavailableException) {
            RawTextResult.OcrEngineError
        } finally {
            // Освобождаем камеру и файл и при ошибке съёмки, и при ошибке OCR.
            releaseCamera()
            photo.delete()
        }
    }
}
