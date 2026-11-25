package com.pricelens.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.pricelens.domain.model.OcrResult
import com.pricelens.domain.model.RawTextResult
import com.pricelens.domain.model.UnitPrice
import com.pricelens.domain.parser.PriceTagParser
import com.pricelens.domain.pricing.UnitPriceCalculator
import com.pricelens.domain.repository.ImageTextProvider
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.util.concurrent.CancellationException

class RecognizePriceTagUseCaseTest {
    @Test
    fun mapsSuccessfulTextToOcrSuccess() = runBlocking {
        val result = useCase(RawTextResult.Success("350 г 240 ₽"))()

        assertThat(result).isInstanceOf(OcrResult.Success::class.java)
        val success = result as OcrResult.Success
        assertDecimal(success.parsed.price, "240")
        assertDecimal(success.unitPrice.value, "685.71")
        assertThat(success.unitPrice.displayUnit).isEqualTo(UnitPrice.Unit.KILOGRAM)
    }

    @Test
    fun mapsExplicitOnlyPriceToOcrSuccess() = runBlocking {
        val result = useCase(RawTextResult.Success("Цена за 1 кг: 685,71 ₽"))()

        assertThat(result).isInstanceOf(OcrResult.Success::class.java)
        val success = result as OcrResult.Success
        assertThat(success.parsed.packageAmount).isNull()
        assertDecimal(success.explicitUnitPriceValue(), "685.71")
        assertDecimal(success.unitPrice.value, "685.71")
    }

    @Test
    fun mapsUnparseableText() = runBlocking {
        val result = useCase(RawTextResult.Success("240 ₽"))()

        assertThat(result).isInstanceOf(OcrResult.Unparseable::class.java)
        assertThat((result as OcrResult.Unparseable).rawText).isEqualTo("240 ₽")
    }

    @Test
    fun mapsBlankTextToEmptyText() = runBlocking {
        assertThat(useCase(RawTextResult.Success("   "))()).isEqualTo(OcrResult.EmptyText)
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
        parser = PriceTagParser(),
        calculator = UnitPriceCalculator(),
    )

    private fun throwingUseCase(throwable: Throwable): RecognizePriceTagUseCase =
        RecognizePriceTagUseCase(
            imageTextProvider = ImageTextProvider { throw throwable },
            parser = PriceTagParser(),
            calculator = UnitPriceCalculator(),
        )

    private fun OcrResult.Success.explicitUnitPriceValue(): BigDecimal? = parsed.explicitUnitPrice

    private fun assertDecimal(actual: BigDecimal?, expected: String) {
        assertThat(actual).isNotNull()
        assertThat(actual!!.compareTo(BigDecimal(expected))).isEqualTo(0)
    }
}
