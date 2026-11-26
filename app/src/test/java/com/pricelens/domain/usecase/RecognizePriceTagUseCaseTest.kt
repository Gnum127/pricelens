package com.pricelens.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.pricelens.domain.model.OcrResult
import com.pricelens.domain.model.RawTextResult
import com.pricelens.domain.model.UnitPrice
import com.pricelens.domain.parser.OcrTokenMerger
import com.pricelens.domain.parser.PriceTagParser
import com.pricelens.domain.pricing.UnitPriceCalculator
import com.pricelens.domain.repository.ImageTextProvider
import com.pricelens.testing.singleLineFrame
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.util.concurrent.CancellationException

class RecognizePriceTagUseCaseTest {
    @Test
    fun mapsSuccessfulFrameToOcrSuccess() = runBlocking {
        val result = useCase(RawTextResult.Success(singleLineFrame("350 г 240 ₽")))()

        assertThat(result).isInstanceOf(OcrResult.Success::class.java)
        val success = result as OcrResult.Success
        assertDecimal(success.parsed.price, "240")
        assertDecimal(success.unitPrice.value, "685.71")
        assertThat(success.unitPrice.displayUnit).isEqualTo(UnitPrice.Unit.KILOGRAM)
    }

    @Test
    fun mapsExplicitOnlyPriceToOcrSuccess() = runBlocking {
        val result = useCase(
            RawTextResult.Success(singleLineFrame("Цена за 1 кг: 685,71 ₽")),
        )()

        assertThat(result).isInstanceOf(OcrResult.Success::class.java)
        val success = result as OcrResult.Success
        assertThat(success.parsed.packageAmount).isNull()
        assertDecimal(success.parsed.explicitUnitPrice, "685.71")
        assertDecimal(success.unitPrice.value, "685.71")
    }

    @Test
    fun mapsUnparseableFrame() = runBlocking {
        val result = useCase(RawTextResult.Success(singleLineFrame("240 ₽")))()

        assertThat(result).isInstanceOf(OcrResult.Unparseable::class.java)
        assertThat((result as OcrResult.Unparseable).rawText).isEqualTo("240 ₽")
    }

    @Test
    fun mapsFrameWithBlankTokensToEmptyText() = runBlocking {
        assertThat(
            useCase(RawTextResult.Success(singleLineFrame("   ")))(),
        ).isEqualTo(OcrResult.EmptyText)
    }

    @Test
    fun mapsEmptyFrameToEmptyText() = runBlocking {
        val emptyFrame = singleLineFrame("350 г 240 ₽").copy(tokens = emptyList())
        assertThat(useCase(RawTextResult.Success(emptyFrame))()).isEqualTo(OcrResult.EmptyText)
    }

    @Test
    fun mapsRawEmptyText() = runBlocking {
        assertThat(useCase(RawTextResult.EmptyText)()).isEqualTo(OcrResult.EmptyText)
    }

    @Test
    fun mapsCameraError() = runBlocking {
        assertThat(useCase(RawTextResult.CameraError)()).isEqualTo(OcrResult.CameraError)
    }

    @Test
    fun mapsPermissionDenied() = runBlocking {
        assertThat(useCase(RawTextResult.PermissionDenied)()).isEqualTo(OcrResult.PermissionDenied)
    }

    @Test
    fun mapsOcrEngineError() = runBlocking {
        assertThat(useCase(RawTextResult.OcrEngineError)()).isEqualTo(OcrResult.OcrEngineError)
    }

    @Test
    fun mapsProviderExceptionToCameraError() = runBlocking {
        val useCase = throwingUseCase(IllegalStateException("camera unavailable"))

        assertThat(useCase()).isEqualTo(OcrResult.CameraError)
    }

    @Test
    fun rethrowsCancellation() {
        val useCase = throwingUseCase(CancellationException("cancelled"))

        assertThrows<CancellationException> {
            runBlocking { useCase() }
        }
    }

    private fun useCase(result: RawTextResult): RecognizePriceTagUseCase = RecognizePriceTagUseCase(
        imageTextProvider = ImageTextProvider { result },
        merger = OcrTokenMerger(),
        parser = PriceTagParser(),
        calculator = UnitPriceCalculator(),
    )

    private fun throwingUseCase(throwable: Throwable): RecognizePriceTagUseCase =
        RecognizePriceTagUseCase(
            imageTextProvider = ImageTextProvider { throw throwable },
            merger = OcrTokenMerger(),
            parser = PriceTagParser(),
            calculator = UnitPriceCalculator(),
        )

    private fun assertDecimal(actual: BigDecimal?, expected: String) {
        assertThat(actual).isNotNull()
        assertThat(actual!!.compareTo(BigDecimal(expected))).isEqualTo(0)
    }
}
