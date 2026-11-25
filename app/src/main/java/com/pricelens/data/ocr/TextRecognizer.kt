package com.pricelens.data.ocr

import androidx.camera.core.ImageProxy

fun interface TextRecognizer {
    fun recognize(image: ImageProxy): String?
}
