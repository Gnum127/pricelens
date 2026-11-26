package com.pricelens.data.ocr

import android.content.Context
import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import com.pricelens.domain.model.OcrBox
import com.pricelens.domain.model.OcrSource
import com.pricelens.domain.model.OcrToken
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest

/** Слова и копейки: Tesseract читает кириллицу и мелкий шрифт, но теряет крупные цифры. */
class TesseractOcr(context: Context) {
    private val api = TessBaseAPI()
    private val dataPath = context.filesDir.absolutePath
    private var failure: Throwable? = null

    init {
        failure = try {
            prepareTrainedData(context)
            if (api.init(dataPath, LANGUAGE, TessBaseAPI.OEM_LSTM_ONLY)) {
                null
            } else {
                val problem = IllegalStateException("Tesseract не инициализировал rus.traineddata")
                trainedDataFile().delete()
                problem
            }
        } catch (problem: Throwable) {
            // Битый файл модели удаляем: следующая инициализация скопирует его заново.
            trainedDataFile().delete()
            problem
        }
    }

    @Synchronized
    fun recognize(bitmap: Bitmap): List<OcrToken> {
        failure?.let { throw OcrEngineUnavailableException("OCR-модель rus недоступна", it) }
        api.setImage(bitmap)
        api.getUTF8Text()
        val iterator = api.getResultIterator()
            ?: throw OcrEngineUnavailableException("Tesseract не вернул результат распознавания")
        try {
            return buildList {
                do {
                    val text = iterator.getUTF8Text(WORD)?.trim().orEmpty()
                    if (text.isNotBlank()) {
                        val box = iterator.getBoundingRect(WORD)
                        add(
                            OcrToken(
                                text = text,
                                box = OcrBox(box.left, box.top, box.right, box.bottom),
                                confidence = iterator.confidence(WORD) / CONFIDENCE_SCALE,
                                source = OcrSource.TESSERACT,
                            ),
                        )
                    }
                } while (iterator.next(WORD))
            }
        } finally {
            iterator.delete()
            api.clear()
        }
    }

    private fun prepareTrainedData(context: Context) {
        val folder = File(dataPath, "tessdata")
        if (!folder.exists() && !folder.mkdirs()) {
            error("Не удалось создать каталог tessdata")
        }
        val target = trainedDataFile()
        if (target.exists() && target.length() == EXPECTED_SIZE && sha256(target) == EXPECTED_SHA256) {
            return
        }

        val temp = File(folder, "$LANGUAGE.traineddata.tmp")
        try {
            context.assets.open("tessdata/$LANGUAGE.traineddata").use { input ->
                FileOutputStream(temp).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                }
            }
            check(sha256(temp) == EXPECTED_SHA256) { "Неверная контрольная сумма rus.traineddata" }
            if (target.exists() && !target.delete()) {
                error("Не удалось заменить rus.traineddata")
            }
            check(temp.renameTo(target)) { "Не удалось сохранить rus.traineddata" }
        } catch (problem: Throwable) {
            temp.delete()
            throw problem
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02X".format(it) }
    }

    private fun trainedDataFile(): File = File(File(dataPath, "tessdata"), "$LANGUAGE.traineddata")

    private companion object {
        const val LANGUAGE = "rus"
        const val CONFIDENCE_SCALE = 100f
        const val BUFFER_SIZE = 8 * 1024
        const val EXPECTED_SIZE = 3_861_738L
        const val EXPECTED_SHA256 = "E16E5E036CCE1D9EC2B00063CF8B54472625B9E14D893A169E2B0DEDEB4DF225"
        val WORD = TessBaseAPI.PageIteratorLevel.RIL_WORD
    }
}
