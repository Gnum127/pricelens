package com.pricelens.testing

import com.pricelens.domain.model.OcrBox
import com.pricelens.domain.model.OcrFrame
import com.pricelens.domain.model.OcrSource
import com.pricelens.domain.model.OcrToken

/** Кадр с одной строкой текста — удобно для тестов, которым не нужна геометрия ценника. */
fun singleLineFrame(text: String): OcrFrame = OcrFrame(
    width = 640,
    height = 480,
    tokens = listOf(
        OcrToken(text, OcrBox(0, 0, 640, 480), 1f, OcrSource.TESSERACT),
    ),
)
