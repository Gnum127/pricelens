package com.pricelens.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
import java.security.MessageDigest

/**
 * Сквозная проверка распознавания на настоящих JPEG, а не на синтетических токенах.
 * Файлы 1.jpg–13.jpg подтверждены пользователем и лежат в androidTest/assets/price-tags.
 * Тест требует устройство или эмулятор: обычный `test` его не запускает.
 *
 * Числовые поля сверяются точно. Название сверяется по набору смысловых слов без учёта
 * порядка: на настоящих JPEG движки читают одни и те же слова в разном порядке и иногда
 * теряют часть слова (`говядиной` → `диной`). Все слова эталона обязаны присутствовать,
 * кроме явно перечисленных нечитаемых; лишние слова тоже не допускаются. Точные строки
 * зафиксированы в JVM-тесте на сырых токенах, где порядок детерминирован.
 */
@RunWith(AndroidJUnit4::class)
class PriceTagRecognitionAndroidTest {
    private val merger = OcrTokenMerger()
    private val parser = PriceTagParser()
    private val calculator = UnitPriceCalculator()

    @Test
    fun assetChecksumsMatchOcrNotes() {
        RealPriceTagCases.all.forEach { tagCase ->
            val bytes = testContext.assets.open("$ASSET_FOLDER/${tagCase.id}").use { it.readBytes() }
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02X".format(it.toInt() and 0xFF) }
            assertEquals("${tagCase.id}: ассет перекодирован", assetSha256[tagCase.id], hash)
        }
    }

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
        val before = temporaryJpegs()
        var info = "[${case.id}] via CameraImageTextProvider"
        val provider = CameraImageTextProvider(
            cacheDir = appContext.cacheDir,
            cameraController = object : CameraController {
                override suspend fun bind(lifecycleOwner: LifecycleOwner, previewView: PreviewView) = Unit

                override suspend fun captureToFile(file: File): Boolean {
                    testContext.assets.open("$ASSET_FOLDER/${case.id}").use { input ->
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
        val after = temporaryJpegs()
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
            testContext.assets.list(ASSET_FOLDER)?.contains(file) == true,
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
        assertNameCoversExpected(case.id, case.name, parsed.name, actual)
        if (case.id == "10.jpg") {
            assertTrue("$actual: кавычки в названии должны сохраниться", parsed.name?.contains('«') == true)
        }
        if (case.id == "4.jpg") {
            assertTrue("$actual: ж/б в названии должно сохраниться", parsed.name?.contains("ж/б") == true)
        }
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

    /**
     * Настоящий снимок даёт нестабильный порядок слов и обрывки слов из-за скошенного текста.
     * Требуем все смысловые слова эталона, кроме явно нечитаемых на этом снимке, и запрещаем
     * лишние слова. Обрывок (`диной` для `говядиной`) считается совпадением. Числовые поля
     * выше сверяются точно, а точные строки — в JVM-тесте на фиксированных токенах.
     */
    private fun assertNameCoversExpected(
        fileName: String,
        expected: String,
        actual: String?,
        info: String,
    ) {
        val expectedWords = contentWords(expected)
        val actualWords = contentWords(actual)
        val optional = unreadableWords(fileName)
        val missing = expectedWords.filterNot { word ->
            word in optional || actualWords.any { it.matchesWord(word) }
        }
        assertTrue(
            "$info: название потеряло слова $missing из $expectedWords: [$actual]",
            missing.isEmpty(),
        )
        val unexpected = actualWords.filterNot { word ->
            expectedWords.any { word.matchesWord(it) }
        }
        assertTrue("$info: в названии лишние слова $unexpected: [$actual]", unexpected.isEmpty())
    }

    private fun String.matchesWord(expected: String): Boolean =
        contains(expected) || expected.contains(this) && length >= MIN_FRAGMENT_LENGTH

    /** Слова, которые bundled-движки не прочитали на конкретном эталонном JPEG. */
    private fun unreadableWords(fileName: String): Set<String> = when (fileName) {
        "1.jpg" -> setOf("лапша")
        "4.jpg" -> setOf("родину")
        else -> emptySet()
    }

    private fun contentWords(value: String?): List<String> = normalizeName(value)
        .split(Regex("[^\\p{L}\\p{N}]+"))
        .filter { it.length >= MIN_CONTENT_WORD_LENGTH }

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

    /** Проверяем только наши временные снимки: рантайм может писать в кеш и другие файлы. */
    private fun temporaryJpegs(): Set<File> = appContext.cacheDir.listFiles().orEmpty()
        .filter { it.name.startsWith("price-tag-") }
        .toSet()

    private fun copyAsset(file: String): File {
        val target = File.createTempFile("price-tag-", ".jpg", appContext.cacheDir)
        try {
            testContext.assets.open("$ASSET_FOLDER/$file").use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            return target
        } catch (failure: Throwable) {
            target.delete()
            throw failure
        }
    }

    private fun createBlankPhoto(): File {
        val target = File.createTempFile("blank-tag-", ".jpg", appContext.cacheDir)
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

    private fun OcrFrame.describe(): String = tokens.joinToString("\n  ") {
        "${it.source}:'${it.text}' conf=${it.confidence} box=${it.box}"
    }

    companion object {
        private const val ASSET_FOLDER = "price-tags"
        private const val MIN_CONTENT_WORD_LENGTH = 3
        private const val MIN_FRAGMENT_LENGTH = 5

        /** Дублирует таблицу из docs/ocr-notes.md: защищает эталоны от перекодировки. */
        private val assetSha256 = mapOf(
            "1.jpg" to "4FBD17D4CE62CCB132C37B761F4EE774DBBCE4F6DE104315F5DD60661812765C",
            "2.jpg" to "B85ABBA42157BF5047F862CCB4190E5072AC6067E9F4712DF4D5B26B556C8701",
            "3.jpg" to "AA9DBF5DFE3B544BF737BF7D7119C7FFA15C4B08F0D33339749471164F56A1D5",
            "4.jpg" to "4FBA56232B296C70E56DAAA7DDCA073000467ECF9648BFBB3067952242D584A6",
            "5.jpg" to "FC7D254B51767273C5686E918E697B43172E57DD6F59ADD1771868D0F08BAC8E",
            "6.jpg" to "DD9505E8E9EEE3F3A2DCA2AF481D1D5CB05D55BCA1B35DC03D090E136D56DF63",
            "7.jpg" to "0613F56112EAD909AEA9698C792C5AC77DE9D14A6AFF914F04A4870127B370E0",
            "8.jpg" to "646A5D5B7D405E35183A0677E84A81145678595E8C9F7C40970B65DC8C73B64F",
            "9.jpg" to "E04243B2375DB3D38B71C3687C03D2F93CEF5583277AE0C77577D8E672A8FDFE",
            "10.jpg" to "E8FF4FB8A381C9CAFCC6DF4991E9FFD92BA9BB70A6EA421A8BF1FCACD3E0D66C",
            "11.jpg" to "548B845DD190B4AD497734363E03DE8BC178E859E7337F698EE34A5F8462FE7C",
            "12.jpg" to "19C696AA21DDE05098B2960AB9AD80AA7CCA575EA37ED75370305ABCEFEBF09C",
            "13.jpg" to "6D9A08AD7CAC05F3A1EDE7E8EA399D64393E8928D2C3E97116889CC34EB42993",
        )

        // Один движок на процесс: у Tesseract/RapidOCR нет публичного close, JUnit4 выполняет тесты последовательно.
        private lateinit var appContext: Context
        private lateinit var testContext: Context
        private lateinit var recognizer: TwoLayerTextRecognizer

        @BeforeClass
        @JvmStatic
        fun setUpClass() {
            // Модели и temp-файлы живут в приложении, а JPEG — в assets test APK.
            appContext = ApplicationProvider.getApplicationContext()
            testContext = InstrumentationRegistry.getInstrumentation().context
            recognizer = TwoLayerTextRecognizer(TesseractOcr(appContext), RapidOcr(appContext))
        }
    }
}
