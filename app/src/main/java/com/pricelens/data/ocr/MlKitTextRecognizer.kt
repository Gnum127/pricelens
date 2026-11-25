package com.pricelens.data.ocr

import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

@ExperimentalGetImage
class MlKitTextRecognizer : TextRecognizer {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override fun recognize(image: ImageProxy): String? {
        val mediaImage = image.image ?: return null
        val inputImage = InputImage.fromMediaImage(mediaImage, image.imageInfo.rotationDegrees)
        return try {
            Tasks.await(recognizer.process(inputImage)).text.takeIf { it.isNotBlank() }
        } catch (failure: Exception) {
            null
        }
    }
}
