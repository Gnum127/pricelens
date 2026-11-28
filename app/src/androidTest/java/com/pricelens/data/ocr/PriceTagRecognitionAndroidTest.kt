package com.pricelens.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pricelens.data.camera.CameraController
import com.pricelens.data.camera.CameraImageTextProvider
import com.pricelens.data.camera.CameraPermissionChecker
import com.pricelens.domain.model.OcrFrame
import com.pricelens.domain.model.OcrResult
import com.pricelens.domain.model.ParsedPriceTag
import com.pricelens.domain.model.RawTextResult
import com.pricelens.domain.model.UnitPrice
import com.pricelens.domain.parser.OcrTokenMerger
import com.pricelens.domain.parser.PriceTagParser
import com.pricelens.domain.pricing.UnitPriceCalculator
import com.pricelens.domain.repository.ImageTextProvider
import com.pricelens.domain.usecase.RecognizePriceTagUseCase
import com.pricelens.testing.RealPriceTagCase
import com.pricelens.testing.RealPriceTagCases
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.math.BigDecimal

/**
 * Сквозная проверка распознавания на настоящих JPEG, а не на синтетических токенах.
 * Файлы 1.jpg–13.jpg подтверждены пользователем и лежат в androidTest/assets/price-tags.
 * Тест требует устройство или эмулятор: обычный `test` его не запускает.
 *
 * Числовые поля сверяются точно. Название и подпись веса нормализуются: вывод OCR зависит
 * от ABI/рантайма, а точные строки уже зафиксированы в JVM-тесте на сырых токенах.
 */
@RunWith(AndroidJUnit4::class)
class PriceTagRecognitionAndroidTest {
    private val merger = OcrTokenMerger()
    private val parser = PriceTagParser()
    private val calculator = UnitPriceCalculator()

    @Test
    fun photo01_noodleNameAndCentralPrice() = assertTag(case("1.jpg"))

    @Test
    fun photo02_kilogramPackageStaysKilogram() = assertTag(case("2.jpg"))

    @Test
    fun photo03_centerTagWinsAndRangeLabelIsKept() = assertTag(case("3.jpg"))

    @Test
    fun photo04_stickerNotCanPrint() = assertTag(case("4.jpg"))

    @Test
    fun photo05_tesseractKopecksBeatRapidOcr() = assertTag(case("5.jpg"))

    @Test
    fun photo06_secondBrandSamePrice() = assertTag(case("6.jpg"))

    @Test
    fun photo07_pateFromTurkey() = assertTag(case("7.jpg"))

    @Test
    fun photo08_cookieNameAndWeight() = assertTag(case("8.jpg"))

    @Test
    fun photo09_superzak() = assertTag(case("9.jpg"))

    @Test
    fun photo10_mixedCaseAndQuotesKept() = assertTag(case("10.jpg"))

    @Test
    fun photo11_neighboringPriceIgnored() = assertTag(case("11.jpg"))

    @Test
    fun photo12_plumMarshmallow() = assertTag(case("12.jpg"))

    @Test
    fun photo13_cardPriceIsUsedForUnitPrice() = assertTag(case("13.jpg"))

    /** Один кейс идёт через production use case, а не через вручную собранный пайплайн. */
    @Test
    fun photo13ThroughRecognizeUseCase() = runBlocking {
        val case = case("13.jpg")
        val photo = copyAsset(case.id)
        try {
            var info = "[${case.id}]"
            val provider = ImageTextProvider {
                val frame = recognizer.recognize(photo)
                if (frame == null || frame.tokens.isEmpty()) {
                    RawTextResult.EmptyText
                } else {
                    info = "[${case.id}] ${frame.width}×${frame.height} " +
                        "text=[${merger.merge(frame)}] tokens=[${frame.describe()}]"
                    RawTextResult.Success(frame)
                }
            }
            val result = RecognizePriceTagUseCase(provider, merger, parser, calculator)()

            assertTrue("$info: use case вернул $result", result is OcrResult.Success)
            val success = result as OcrResult.Success
            assertParsed(case, success.parsed, success.unitPrice, info)
        } finally {
            photo.delete()
        }
    }

    /** Настоящий провайдер: снимок подменяется ассетом, но файл живёт и удаляется по контракту. */
    @Test
    fun assetThroughProductionCameraImageTextProvider() = runBlocking {
        val case = case("13.jpg")
        val before = context.cacheDir.listFiles().orEmpty().toSet()
        var info = "[${case.id}] via CameraImageTextProvider"
        val provider = CameraImageTextProvider(
            cacheDir = context.cacheDir,
            cameraController = object : CameraController {
                override suspend fun bind(lifecycleOwner: LifecycleOwner, previewView: PreviewView) = Unit

                override suspend fun captureToFile(file: File): Boolean {
                    context.assets.open("$ASSET_FOLDER/${case.id}").use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    }
                    return true
                }

                override fun unbind() = Unit
            },
            textRecognizer = TextRecognizer { photo ->
                val frame = recognizer.recognize(photo)
                if (frame != null) {
                    info = "[${case.id}] ${frame.width}×${frame.height} " +
                        "text=[${merger.merge(frame)}] tokens=[${frame.describe()}] via CameraImageTextProvider"
                }
                frame
            },
            permissionChecker = CameraPermissionChecker { true },
        )

        val result = RecognizePriceTagUseCase(provider, merger, parser, calculator)()

        assertTrue("${case.id}: провайдер вернул $result", result is OcrResult.Success)
        val after = context.cacheDir.listFiles().orEmpty().toSet()
        assertEquals("${case.id}: временный JPEG должен быть удалён", before, after)
        val success = result as OcrResult.Success
        assertParsed(case, success.parsed, success.unitPrice, info)
    }

    /** Негатив на реальном изображении: пустой кадр не должен превратиться в товар. */
    @Test
    fun blankPhotoThroughUseCaseIsNotRecognizedAsTag() = runBlocking {
        val photo = createBlankPhoto()
        try {
            val provider = ImageTextProvider { rawFrame(photo) }
            val result = RecognizePriceTagUseCase(provider, merger, parser, calculator)()

            assertTrue(
                "Однотонный кадр не должен дать товар: $result",
                result is OcrResult.EmptyText || result is OcrResult.Unparseable,
            )
        } finally {
            assertTrue("Временный пустой JPEG должен быть удалён", photo.delete())
        }
    }

    /** Если положить снимок с монитора в ассеты, тест покажет, даёт ли он вообще токены. */
    @Test
    fun monitorPhotoProducesTokensWhenAssetIsPresent() {
        val file = "monitor.jpg"
        Assume.assumeTrue(
            "Положите снимок с монитора в app/src/androidTest/assets/price-tags/monitor.jpg",
            context.assets.list(ASSET_FOLDER)?.contains(file) == true,
        )

        val photo = copyAsset(file)
        try {
            val frame = recognizer.recognize(photo)
            assertNotNull("monitor.jpg: OCR вернул null", frame)
            assertTrue("monitor.jpg: нет токенов. ${frame!!.describe()}", frame.tokens.isNotEmpty())
        } finally {
            photo.delete()
        }
    }

    private fun case(id: String) = RealPriceTagCases.all.single { it.id == id }

    private fun rawFrame(photo: File): RawTextResult {
        val frame = recognizer.recognize(photo)
        return if (frame == null || frame.tokens.isEmpty()) {
            RawTextResult.EmptyText
        } else {
            RawTextResult.Success(frame)
        }
    }

    private fun assertTag(case: RealPriceTagCase) {
        val photo = copyAsset(case.id)
        try {
            val frame = recognizer.recognize(photo)
            assertNotNull("${case.id}: OCR вернул null", frame)
            frame!!
            assertTrue("${case.id}: нет токенов. ${frame.describe()}", frame.tokens.isNotEmpty())

            val text = merger.merge(frame)
            assertTrue("${case.id}: пустая сводка. ${frame.describe()}", text.isNotBlank())

            val parsed = parser.parse(text)
            val info = "[${case.id}] ${frame.width}×${frame.height} " +
                "text=[$text] tokens=[${frame.describe()}]"
            assertNotNull("$info: парсер не разобрал ценник", parsed)
            val unitPrice = calculator.calculate(parsed!!)
            assertNotNull("$info: цена за единицу", unitPrice)
            assertParsed(case, parsed, unitPrice!!, info)
        } finally {
            photo.delete()
        }
    }

    private fun assertParsed(
        case: RealPriceTagCase,
        parsed: ParsedPriceTag,
        unitPrice: UnitPrice,
        info: String,
    ) {
        val actual = "$info parsed=[$parsed] unitPrice=[$unitPrice]"
        assertEquals("$actual: название", normalizeName(case.name), normalizeName(parsed.name))
        assertDecimal("$actual: вес", parsed.packageAmount, case.amount)
        assertEquals(
            "$actual: подпись веса",
            normalizeLabel(case.amountLabel),
            normalizeLabel(parsed.packageAmountLabel),
        )
        assertEquals("$actual: единица упаковки", case.packageUnit, parsed.packageUnit)
        assertDecimal("$actual: цена", parsed.price, case.price)
        if (case.priceByCard == null) {
            assertNull("$actual: второй цены быть не должно", parsed.priceByCard)
        } else {
            assertDecimal("$actual: цена по карте", parsed.priceByCard, case.priceByCard)
        }
        assertDecimal("$actual: цена за кг", unitPrice.value, case.unitPrice)
        assertEquals("$actual: единица цены", case.unitPriceUnit, unitPrice.displayUnit)
    }

    /** OCR может различать кавычки, регистр и пробелы в зависимости от ABI/рантайма. */
    private fun normalizeName(value: String?): String = value.orEmpty()
        .lowercase()
        .replace('ё', 'е')
        .replace('«', '"')
        .replace('»', '"')
        .replace('–', '-')
        .replace('\u00A0', ' ')
        .replace(Regex("\\p{Z}+"), " ")
        .trim()

    private fun normalizeLabel(value: String?): String = normalizeName(value)
        .replace(Regex("\\s*-\\s*"), "-")
        .replace(" ", "")

    private fun copyAsset(file: String): File {
        val target = File.createTempFile("price-tag-", ".jpg", context.cacheDir)
        try {
            context.assets.open("$ASSET_FOLDER/$file").use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            return target
        } catch (failure: Throwable) {
            target.delete()
            throw failure
        }
    }

    private fun createBlankPhoto(): File {
        val target = File.createTempFile("blank-tag-", ".jpg", context.cacheDir)
        val bitmap = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.WHITE)
            target.outputStream().use { out ->
                assertTrue("Не удалось записать пустой JPEG", bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out))
            }
            return target
        } catch (failure: Throwable) {
            target.delete()
            throw failure
        } finally {
            bitmap.recycle()
        }
    }

    private fun assertDecimal(message: String, actual: BigDecimal?, expected: String) {
        assertNotNull(message, actual)
        assertEquals(message, 0, actual!!.compareTo(BigDecimal(expected)))
    }

    private fun OcrFrame.describe(): String = tokens.joinToString(" | ") {
        "${it.source}:'${it.text}' conf=${it.confidence} box=${it.box}"
    }

    companion object {
        private const val ASSET_FOLDER = "price-tags"

        // Один движок на процесс: у Tesseract/RapidOCR нет публичного close, JUnit4 выполняет тесты последовательно.
        private lateinit var context: Context
        private lateinit var recognizer: TwoLayerTextRecognizer

        @BeforeClass
        @JvmStatic
        fun setUpClass() {
            context = ApplicationProvider.getApplicationContext()
            recognizer = TwoLayerTextRecognizer(TesseractOcr(context), RapidOcr(context))
        }
    }
}
