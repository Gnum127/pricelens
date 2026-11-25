package com.pricelens.domain.usecase

import com.pricelens.domain.model.OcrResult
import com.pricelens.domain.model.RawTextResult
import com.pricelens.domain.parser.PriceTagParser
import com.pricelens.domain.pricing.UnitPriceCalculator
import com.pricelens.domain.repository.ImageTextProvider
import java.util.concurrent.CancellationException

class RecognizePriceTagUseCase(
    private val imageTextProvider: ImageTextProvider,
    private val parser: PriceTagParser,
    private val calculator: UnitPriceCalculator,
) {
    suspend operator fun invoke(): OcrResult = when (val raw = readText()) {
        is RawTextResult.Success -> {
            if (raw.text.isBlank()) {
                OcrResult.EmptyText
            } else {
                val parsed = parser.parse(raw.text)
                val unitPrice = parsed?.let(calculator::calculate)
                if (parsed != null && unitPrice != null) {
                    OcrResult.Success(parsed, unitPrice)
                } else {
                    OcrResult.Unparseable(raw.text)
                }
            }
        }
        RawTextResult.EmptyText -> OcrResult.EmptyText
        RawTextResult.CameraError -> OcrResult.CameraError
        RawTextResult.PermissionDenied -> OcrResult.PermissionDenied
    }

    private suspend fun readText(): RawTextResult = try {
        imageTextProvider.readText()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Throwable) {
        RawTextResult.CameraError
    }
}
