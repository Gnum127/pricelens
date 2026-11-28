package com.pricelens.domain.parser

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.pricelens.domain.model.OcrBox
import com.pricelens.domain.model.OcrFrame
import com.pricelens.domain.model.OcrSource
import com.pricelens.domain.model.OcrToken
import com.pricelens.domain.model.UnitPrice
import com.pricelens.domain.model.WeightUnit
import com.pricelens.domain.pricing.UnitPriceCalculator
import com.pricelens.testing.RealPriceTagCases
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

/**
 * Сырые токены сняты с реальных кадров 1.jpg–7.jpg: координаты RapidOCR взяты из его boxPoint,
 * слова и копейки — из word-level вывода Tesseract rus. Фикстуры 8.jpg–13.jpg фиксируют
 * подтверждённые пользователем эталоны (две цены и шум соседнего ценника). Сами JPEG лежат
 * в `androidTest/assets/price-tags/` и прогоняются сквозным тестом
 * `PriceTagRecognitionAndroidTest`; в этом юнит-тесте используется только сырой текст.
 */
class OcrTokenMergerTest {
    private val merger = OcrTokenMerger()
    private val parser = PriceTagParser()
    private val calculator = UnitPriceCalculator()

    @TestFactory
    fun realPriceTags(): List<DynamicTest> = listOf(
        case1(),
        case2(),
        case3(),
        case4(),
        case5(),
        case6(),
        case7(),
        case8(),
        case9(),
        case10(),
        case11(),
        case12(),
        case13(),
    ).map { case -> DynamicTest.dynamicTest(case.id) { assertRealTag(case) } }

    private fun assertRealTag(case: Case) {
        val text = merger.merge(case.frame)
        assertWithMessage("${case.id}: сводка пустая").that(text.isBlank()).isFalse()
        if (case.id == "11.jpg") {
            assertWithMessage("${case.id}: цена соседнего ценника не должна попасть в сводку: $text")
                .that(text).doesNotContain("262")
        }

        val parsed = parser.parse(text)
        assertWithMessage("${case.id}: парсер не разобрал «$text»").that(parsed).isNotNull()
        parsed!!.let {
            assertWithMessage("${case.id}: название").that(it.name).isEqualTo(case.name)
            assertDecimal("${case.id}: вес", it.packageAmount, case.amount)
            assertWithMessage("${case.id}: подпись веса").that(it.packageAmountLabel).isEqualTo(case.label)
            assertWithMessage("${case.id}: единица").that(it.packageUnit).isEqualTo(case.unit)
            assertDecimal("${case.id}: цена", it.price, case.price)
            if (case.priceByCard == null) {
                assertWithMessage("${case.id}: второй цены быть не должно")
                    .that(it.priceByCard).isNull()
            } else {
                assertDecimal("${case.id}: цена по карте", it.priceByCard, case.priceByCard)
            }
        }

        val unitPrice = calculator.calculate(parsed)
        assertWithMessage("${case.id}: цена за единицу").that(unitPrice).isNotNull()
        assertWithMessage("${case.id}: цена за единицу ${unitPrice!!.value} для «$text»")
            .that(unitPrice!!.value.compareTo(BigDecimal(case.unitPrice)))
            .isEqualTo(0)
        assertWithMessage("${case.id}: единица цены").that(unitPrice.displayUnit).isEqualTo(case.unitPriceUnit)
    }

    private fun assertDecimal(message: String, actual: BigDecimal?, expected: String) {
        assertWithMessage(message).that(actual).isNotNull()
        assertWithMessage(message).that(actual!!.compareTo(BigDecimal(expected))).isEqualTo(0)
    }

    private data class Case(
        val id: String,
        val frame: OcrFrame,
        val name: String,
        val amount: String,
        val price: String,
        val priceByCard: String? = null,
        val unitPrice: String,
        val label: String? = null,
        val unit: WeightUnit = WeightUnit.GRAM,
        val unitPriceUnit: UnitPrice.Unit = UnitPrice.Unit.KILOGRAM,
    )

    // 1.jpg — «Лапша «Биг Ланч»…», 65 г, 49,99 ₽/шт. Рядом другой ценник с «90».
    private fun case1() = Case(
        id = "1.jpg",
        frame = OcrFrame(
            width = 1080,
            height = 720,
            tokens = listOf(
                tess("Лапша", 460, 275, 540, 300),
                tess("\"Биг Ланч\"", 545, 275, 720, 300),
                tess("бургиньон с говядиной", 480, 299, 700, 330),
                tess("и лесными грибами", 700, 299, 788, 330),
                tess("стакан 65г", 575, 323, 674, 347, 0.92f),
                tess("99", 731, 349, 791, 398),
                tess("руб/шт", 725, 393, 794, 422),
                tess("86665", 728, 421, 778, 441),
                tess("21.09.26", 459, 446, 524, 467),
                rapid("\"Биг Ланч\"", 480, 279, 771, 305, 0.71f),
                rapid("49", 593, 340, 719, 441),
                rapid("99", 731, 349, 791, 398),
                rapid("py6/ut", 725, 393, 794, 422, 0.76f),
                rapid("86665", 728, 421, 778, 441),
                rapid("21.09.26", 459, 446, 524, 467),
                rapid("90", 440, 656, 478, 683),
            ),
        ),
        name = "Лапша «Биг Ланч» бургиньон с говядиной и лесными грибами",
        amount = "65",
        price = "49.99",
        unitPrice = "769.08",
    )

    // 2.jpg — «Ананас», 1 кг, 199,99 ₽/кг; рубли читает только RapidOCR.
    private fun case2() = Case(
        id = "2.jpg",
        frame = OcrFrame(
            width = 1080,
            height = 800,
            tokens = listOf(
                tess("Ананас 1кг", 264, 581, 475, 655),
                tess("99", 493, 658, 560, 719),
                tess("руб./кг", 518, 635, 564, 662),
                tess("38329", 375, 528, 418, 554),
                tess("13.11.26", 471, 511, 525, 538),
                rapid("Ананас 1kr", 264, 581, 475, 655, 0.85f),
                rapid("py6./kr", 518, 635, 564, 662, 0.82f),
                rapid("99", 493, 658, 560, 719),
                rapid("199", 326, 665, 497, 790),
            ),
        ),
        name = "Ананас",
        amount = "1000",
        price = "199.99",
        unitPrice = "199.99",
    )

    // 3.jpg — центр кадра: 139,99; боковые ценники с 99 игнорируются; вес — диапазон.
    private fun case3() = Case(
        id = "3.jpg",
        frame = OcrFrame(
            width = 1080,
            height = 720,
            tokens = listOf(
                tess("Консервы рыбные шпроты в", 494, 216, 875, 255),
                tess("масле 230-240г", 562, 247, 805, 276, 0.94f),
                // Единственные копейки Tesseract относятся к центральному ценнику.
                tess("99", 731, 349, 791, 398),
                tess("руб/шт", 811, 367, 891, 394),
                tess("54407", 79, 395, 137, 418),
                tess("10.10.26", 463, 410, 546, 436),
                rapid("139", 576, 297, 795, 413),
                rapid("99", 63, 309, 143, 370),
                rapid("99", 819, 311, 897, 370),
                rapid("py6/ut", 725, 393, 794, 422, 0.79f),
                rapid("py6/uT", 811, 367, 891, 394, 0.79f),
                rapid("54407", 79, 395, 137, 418),
                rapid("4600335001176", 681, 429, 873, 455),
            ),
        ),
        name = "Консервы рыбные шпроты в масле",
        amount = "240",
        label = "230-240 г",
        price = "139.99",
        unitPrice = "583.29",
    )

    // 4.jpg — данные только с ценника-наклейки; текст на банке выше не должен попасть в название.
    private fun case4() = Case(
        id = "4.jpg",
        frame = OcrFrame(
            width = 1280,
            height = 720,
            tokens = listOf(
                tess("БАЛТИЙСКАЯ (ШПРОТ) НЕРАЗДЕЛАННАЯ", 909, 0, 1238, 89, 0.82f),
                tess("СРОК ГОДНОСТИ 24 МЕСЯЦА", 487, 80, 758, 142, 0.74f),
                tess("Консервы рыбные тунец", 283, 207, 710, 260),
                tess("\"За Родину\" с киноа и травами, ж/б", 277, 236, 722, 291),
                tess("160г", 476, 279, 539, 303),
                tess("99", 663, 302, 752, 369),
                tess("руб/шт", 660, 361, 748, 396),
                tess("88646", 662, 393, 727, 417),
                tess("14.09.26", 282, 437, 373, 466),
                rapid("119", 416, 292, 643, 434, 0.96f),
                rapid("99", 663, 302, 752, 369),
                rapid("py6/wt", 660, 361, 748, 396, 0.77f),
                rapid("160r", 476, 279, 539, 303),
                rapid("88646", 662, 393, 727, 417),
                rapid("4605463008004", 528, 431, 738, 467),
            ),
        ),
        name = "Консервы рыбные тунец «За Родину» с киноа и травами, ж/б",
        amount = "160",
        price = "119.99",
        unitPrice = "749.94",
    )

    // 5.jpg — RapidOCR даёт копейки 66, верные 99 читает Tesseract; окончание не исправляем.
    private fun case5() = Case(
        id = "5.jpg",
        frame = OcrFrame(
            width = 1080,
            height = 630,
            tokens = listOf(
                tess("Паштет", 339, 106, 450, 145, 0.95f),
                tess("\"В Три Приёма\" со вкусом", 455, 106, 860, 150),
                tess("копчёная индейка 70г", 436, 148, 769, 187),
                tess("99", 767, 233, 860, 304),
                tess("руб/шт", 757, 307, 852, 337),
                tess("89080", 755, 339, 825, 366),
                tess("23.11.26", 345, 352, 443, 384),
                rapid("24", 550, 208, 742, 366),
                rapid("66", 767, 233, 860, 304),
                rapid("py6/wt", 757, 307, 852, 337, 0.75f),
                rapid("89080", 755, 339, 825, 366),
                rapid("4610154920152", 605, 382, 831, 415),
            ),
        ),
        name = "Паштет «В Три Приёма» со вкусом копчёная индейка",
        amount = "70",
        price = "24.99",
        unitPrice = "357.00",
    )

    // 6.jpg — тот же бренд, цена 24,99; копейки 66 из RapidOCR игнорируются.
    private fun case6() = Case(
        id = "6.jpg",
        frame = OcrFrame(
            width = 1080,
            height = 640,
            tokens = listOf(
                tess("Паштет \"В Три Приёма\" со вкусом", 171, 252, 894, 316),
                tess("бекона 70г", 427, 306, 654, 351),
                tess("99", 763, 411, 883, 500),
                tess("руб/шт", 751, 500, 871, 539),
                tess("89079", 748, 543, 836, 574),
                rapid("24", 468, 382, 725, 576),
                rapid("66", 763, 411, 883, 500),
                rapid("py6/wt", 751, 500, 871, 539, 0.73f),
                rapid("89079", 748, 543, 836, 574),
            ),
        ),
        name = "Паштет «В Три Приёма» со вкусом бекона",
        amount = "70",
        price = "24.99",
        unitPrice = "357.00",
    )

    // 7.jpg — «Хангров», 90 г, 49,99 ₽/шт.
    private fun case7() = Case(
        id = "7.jpg",
        frame = OcrFrame(
            width = 1080,
            height = 560,
            tokens = listOf(
                tess("Паштет \"Хангров\" нежный из", 427, 226, 897, 278),
                tess("индюшачьего мяса 90г", 487, 262, 846, 312),
                tess("99", 830, 330, 927, 407),
                tess("руб/шт", 821, 396, 918, 440),
                tess("59659", 822, 433, 890, 464),
                rapid("49", 607, 321, 817, 482),
                rapid("99", 830, 330, 927, 407),
                rapid("py6/wt", 821, 396, 918, 440, 0.77f),
                rapid("59659", 822, 433, 890, 464),
            ),
        ),
        name = "Паштет «Хангров» нежный из индюшачьего мяса",
        amount = "90",
        price = "49.99",
        unitPrice = "555.44",
    )

    // 8.jpg — затяжное печенье «Имбирное», 190 г, 144 ₽.
    private fun case8() = Case(
        id = "8.jpg",
        frame = OcrFrame(
            width = 1080,
            height = 720,
            tokens = listOf(
                tess("ПЕЧЕНЬЕ ЗАТЯЖНОЕ", 300, 250, 780, 290),
                tess("\"ИМБИРНОЕ\"", 430, 288, 650, 325),
                tess("190 г", 500, 325, 620, 352),
                rapid("144", 470, 360, 700, 490),
            ),
        ),
        name = "ПЕЧЕНЬЕ ЗАТЯЖНОЕ «ИМБИРНОЕ»",
        amount = "190",
        price = "144",
        unitPrice = "757.89",
    )

    // 9.jpg — «Суперзлак», 230 г, 126 ₽.
    private fun case9() = Case(
        id = "9.jpg",
        frame = OcrFrame(
            width = 1080,
            height = 720,
            tokens = listOf(
                tess("ПЕЧЕНЬЕ ОВСЯНОЕ", 300, 250, 780, 290),
                tess("\"СУПЕРЗЛАК\"", 420, 288, 660, 325),
                tess("230 г", 500, 325, 620, 352),
                rapid("126", 470, 360, 700, 490),
            ),
        ),
        name = "ПЕЧЕНЬЕ ОВСЯНОЕ «СУПЕРЗЛАК»",
        amount = "230",
        price = "126",
        unitPrice = "547.83",
    )

    // 10.jpg — кавычки и регистр внутри названия сохраняются как на ценнике.
    private fun case10() = Case(
        id = "10.jpg",
        frame = OcrFrame(
            width = 1080,
            height = 720,
            tokens = listOf(
                tess("ПЕЧЕНЬЕ \"ОВСЯНОЕ С клюквой,", 250, 240, 800, 285),
                tess("БРУСНИКОЙ И СЕМЕНАМИ ТЫКВЫ\"", 250, 282, 830, 325),
                tess("200 г", 500, 325, 620, 352),
                rapid("262", 470, 360, 700, 490),
            ),
        ),
        name = "ПЕЧЕНЬЕ «ОВСЯНОЕ С клюквой, БРУСНИКОЙ И СЕМЕНАМИ ТЫКВЫ»",
        amount = "200",
        price = "262",
        unitPrice = "1310.00",
    )

    // 11.jpg — цена 262 ₽ слева относится к соседнему ценнику, второй ценой не становится.
    private fun case11() = Case(
        id = "11.jpg",
        frame = OcrFrame(
            width = 1080,
            height = 720,
            tokens = listOf(
                tess("ЗЕФИР ВАНИЛЬНЫЙ", 300, 250, 780, 300),
                tess("80 г", 500, 325, 620, 352),
                rapid("64", 500, 360, 680, 490),
                rapid("262", 40, 360, 220, 490),
            ),
        ),
        name = "ЗЕФИР ВАНИЛЬНЫЙ",
        amount = "80",
        price = "64",
        unitPrice = "800.00",
    )

    // 12.jpg — сливовый зефир, 150 г, 155 ₽.
    private fun case12() = Case(
        id = "12.jpg",
        frame = OcrFrame(
            width = 1080,
            height = 720,
            tokens = listOf(
                tess("ЗЕФИР СЛИВОВЫЙ", 350, 250, 730, 295),
                tess("150 г", 500, 325, 620, 352),
                rapid("155", 470, 360, 700, 490),
            ),
        ),
        name = "ЗЕФИР СЛИВОВЫЙ",
        amount = "150",
        price = "155",
        unitPrice = "1033.33",
    )

    // 13.jpg — две цены одного ценника: 270 ₽ и 243 ₽ по карте; сравнение идёт от 243 ₽.
    private fun case13() = Case(
        id = "13.jpg",
        frame = OcrFrame(
            width = 1080,
            height = 720,
            tokens = listOf(
                tess("ПЕЧЕНЬЕ ОВСЯНОЕ С ЧЕРНОСЛИВОМ", 260, 240, 820, 295),
                tess("200 г", 500, 325, 620, 352),
                tess("цена по карте до 29 ноября", 640, 480, 900, 510),
                rapid("270", 430, 360, 650, 490),
                rapid("243", 620, 360, 830, 490),
            ),
        ),
        name = "ПЕЧЕНЬЕ ОВСЯНОЕ С ЧЕРНОСЛИВОМ",
        amount = "200",
        price = "270",
        priceByCard = "243",
        unitPrice = "1215.00",
    )

    @Test
    fun cardLabelDeterminesRoleEvenWhenItStandsNextToCentralPrice() {
        val frame = OcrFrame(
            width = 1080,
            height = 720,
            tokens = listOf(
                tess("ПЕЧЕНЬЕ ОВСЯНОЕ С ЧЕРНОСЛИВОМ", 260, 240, 820, 295),
                tess("200 г", 500, 325, 620, 352),
                tess("цена по карте", 480, 300, 620, 330),
                rapid("270", 430, 360, 650, 490),
                rapid("243", 660, 360, 880, 490),
            ),
        )

        val parsed = parser.parse(merger.merge(frame))

        assertThat(parsed).isNotNull()
        assertWithMessage("подпись у 270 должна делать её ценой по карте: ${merger.merge(frame)}")
            .that(parsed!!.name).isEqualTo("ПЕЧЕНЬЕ ОВСЯНОЕ С ЧЕРНОСЛИВОМ")
        assertDecimal("обычная цена", parsed.price, "243")
        assertDecimal("цена по карте", parsed.priceByCard, "270")
        assertDecimal("цена за кг по меньшей цене", calculator.calculate(parsed)!!.value, "1215.00")
    }

    @Test
    fun neighboringPriceBoxesWithoutOverlapAreStillMergedAsOneTag() {
        val frame = OcrFrame(
            width = 1080,
            height = 720,
            tokens = listOf(
                tess("ПЕЧЕНЬЕ ОВСЯНОЕ С ЧЕРНОСЛИВОМ", 260, 240, 820, 295),
                tess("200 г", 500, 325, 620, 352),
                tess("цена по карте", 700, 300, 840, 330),
                rapid("270", 430, 360, 650, 490),
                rapid("243", 660, 360, 880, 490),
            ),
        )

        val parsed = parser.parse(merger.merge(frame))

        assertThat(parsed).isNotNull()
        assertDecimal("обычная цена", parsed!!.price, "270")
        assertDecimal("цена по карте", parsed.priceByCard, "243")
    }

    @Test
    fun twoPricesWithoutCardLabelDoNotProduceDiscount() {
        val frame = OcrFrame(
            width = 1080,
            height = 720,
            tokens = listOf(
                tess("ПЕЧЕНЬЕ ОВСЯНОЕ С ЧЕРНОСЛИВОМ", 300, 210, 800, 255),
                tess("200 г", 500, 390, 620, 420),
                rapid("270", 430, 300, 650, 380),
                rapid("243", 620, 300, 840, 380),
            ),
        )

        val text = merger.merge(frame)
        val parsed = parser.parse(text)

        assertWithMessage("без подписи вторая цена не создаётся: $text").that(text).doesNotContain("по карте")
        assertThat(parsed).isNotNull()
        assertDecimal("обычная цена", parsed!!.price, "270")
        assertThat(parsed.priceByCard).isNull()
    }

    @Test
    fun cardLabelFromNeighboringTagDoesNotCreateDiscount() {
        val frame = OcrFrame(
            width = 1080,
            height = 720,
            tokens = listOf(
                tess("ПЕЧЕНЬЕ ОВСЯНОЕ С ЧЕРНОСЛИВОМ", 300, 210, 800, 255),
                tess("200 г", 500, 390, 620, 420),
                tess("цена по карте", 40, 300, 240, 330),
                rapid("270", 430, 300, 650, 380),
                rapid("262", 40, 300, 220, 380),
            ),
        )

        val text = merger.merge(frame)
        val parsed = parser.parse(text)

        assertWithMessage("подпись соседнего ценника не должна дать вторую цену: $text")
            .that(text).doesNotContain("по карте")
        assertThat(parsed).isNotNull()
        assertDecimal("обычная цена", parsed!!.price, "270")
        assertThat(parsed.priceByCard).isNull()
    }

    @Test
    fun cardLabelOfThirdTagIsIgnoredEvenWithTwoPricesOnSelectedTag() {
        val frame = OcrFrame(
            width = 1080,
            height = 720,
            tokens = listOf(
                tess("ПЕЧЕНЬЕ ОВСЯНОЕ С ЧЕРНОСЛИВОМ", 300, 210, 800, 255),
                tess("200 г", 500, 390, 620, 420),
                tess("цена по карте", 40, 300, 240, 330),
                rapid("270", 430, 300, 650, 380),
                rapid("243", 620, 300, 840, 380),
                rapid("262", 40, 300, 220, 380),
            ),
        )

        val text = merger.merge(frame)
        val parsed = parser.parse(text)

        assertWithMessage("подпись третьего ценника не должна дать скидку: $text")
            .that(text).doesNotContain("по карте")
        assertWithMessage("цена третьего ценника не должна попасть в сводку: $text")
            .that(text).doesNotContain("262")
        assertThat(parsed).isNotNull()
        assertDecimal("обычная цена", parsed!!.price, "270")
        assertThat(parsed.priceByCard).isNull()
    }

    @Test
    fun weightBelowPriceIsRecognizedWhenCardLabelIsPresent() {
        val frame = OcrFrame(
            width = 1080,
            height = 720,
            tokens = listOf(
                tess("ПЕЧЕНЬЕ ОВСЯНОЕ", 300, 210, 800, 255),
                tess("200 г", 500, 390, 620, 420),
                tess("цена по карте", 600, 300, 800, 330),
                rapid("270", 430, 300, 650, 380),
                rapid("243", 620, 300, 840, 380),
            ),
        )

        val text = merger.merge(frame)
        val parsed = parser.parse(text)

        assertWithMessage("вес под ценой должен попасть в сводку: $text").that(text).contains("200 г")
        assertThat(parsed).isNotNull()
        assertThat(parsed!!.name).isEqualTo("ПЕЧЕНЬЕ ОВСЯНОЕ")
        assertDecimal("вес", parsed.packageAmount, "200")
        assertDecimal("цена по карте", parsed.priceByCard, "243")
    }

    @Test
    fun nameAndWeightAreKeptWhenTheyStandCloserToCardPrice() {
        val frame = OcrFrame(
            width = 1080,
            height = 720,
            tokens = listOf(
                tess("ПЕЧЕНЬЕ ОВСЯНОЕ", 700, 210, 900, 255),
                tess("200 г", 700, 390, 820, 420),
                tess("цена по карте", 820, 300, 940, 330),
                rapid("270", 430, 300, 650, 380),
                rapid("243", 620, 300, 840, 380),
            ),
        )

        val text = merger.merge(frame)
        val parsed = parser.parse(text)

        assertThat(parsed).isNotNull()
        assertThat(parsed!!.name).isEqualTo("ПЕЧЕНЬЕ ОВСЯНОЕ")
        assertDecimal("вес", parsed.packageAmount, "200")
        assertDecimal("обычная цена", parsed.price, "270")
        assertDecimal("цена по карте", parsed.priceByCard, "243")
    }

    @Test
    fun cardPriceGetsItsOwnKopecksAndNoUnit() {
        val frame = OcrFrame(
            width = 1080,
            height = 720,
            tokens = listOf(
                tess("ПЕЧЕНЬЕ ОВСЯНОЕ", 300, 210, 800, 255),
                tess("200 г", 500, 390, 620, 420),
                tess("99", 600, 340, 640, 380),
                tess("50", 800, 340, 840, 380),
                tess("цена по карте", 600, 300, 800, 330),
                rapid("270", 430, 300, 650, 380),
                rapid("243", 620, 300, 840, 380),
                rapid("py6/wt", 520, 300, 620, 330),
                rapid("py6/kr", 800, 300, 880, 330),
            ),
        )

        val text = merger.merge(frame)
        val parsed = parser.parse(text)

        assertWithMessage("копейки обеих цен: $text").that(text).contains("270,99")
        assertWithMessage("копейки цены по карте: $text").that(text).contains("243,50")
        assertWithMessage(" unit соседней цены не должен попасть в карточную: $text")
            .that(text).doesNotContain("243,50 ₽/кг")
        assertThat(parsed).isNotNull()
        assertDecimal("обычная цена", parsed!!.price, "270.99")
        assertDecimal("цена по карте", parsed.priceByCard, "243.50")
        assertDecimal("цена за кг по карте", calculator.calculate(parsed)!!.value, "1217.50")
    }

    @Test
    fun kopecksFallbackToRapidOcrWhenTesseractMissesThem() {
        val frame = OcrFrame(
            width = 1080,
            height = 630,
            tokens = listOf(
                tess("Паштет \"В Три Приёма\" со вкусом", 400, 230, 900, 287),
                tess("бекона 70г", 450, 265, 700, 296),
                rapid("24", 550, 300, 742, 458),
                rapid("66", 767, 325, 860, 396),
                rapid("py6/wt", 757, 399, 852, 429, 0.75f),
            ),
        )

        val text = merger.merge(frame)
        assertWithMessage("сводка без копеек Tesseract: $text").that(parser.parse(text)).isNotNull()
        assertThat(parser.parse(text)!!.name).isEqualTo("Паштет «В Три Приёма» со вкусом бекона")
        assertDecimal("цена из запасного источника", parser.parse(text)!!.price, "24.66")
    }

    @Test
    fun threeDigitRublesAtLowerConfidenceThresholdAreUsed() {
        val frame = OcrFrame(
            width = 1000,
            height = 600,
            tokens = listOf(
                tess("Молоко", 380, 250, 550, 290),
                tess("1 кг", 480, 275, 590, 295),
                tess("99", 602, 320, 680, 390),
                rapid("119", 400, 300, 600, 450, 0.65f),
            ),
        )

        val text = merger.merge(frame)
        val parsed = parser.parse(text)

        assertWithMessage("сводка на границе 3-значного confidence: $text").that(parsed).isNotNull()
        assertDecimal("цена 119 на границе порога", parsed!!.price, "119.99")
    }

    @Test
    fun threeDigitRublesBelowLowerConfidenceThresholdAreIgnored() {
        val frame = OcrFrame(
            width = 1000,
            height = 600,
            tokens = listOf(
                tess("Молоко", 380, 250, 550, 290),
                tess("1 кг", 480, 275, 590, 295),
                tess("99", 602, 320, 680, 390),
                rapid("119", 400, 300, 600, 450, 0.64f),
            ),
        )

        val text = merger.merge(frame)
        val parsed = parser.parse(text)

        assertWithMessage("порог 0.65 не должен дать цену 119,99: $text")
            .that(text).doesNotContain("119,99")
        assertWithMessage("fallback не должен строить валютную строку: $text")
            .that(text).doesNotContain("₽")
        assertDecimal("цена из fallback", parsed!!.price, "99")
    }

    @Test
    fun rapidCardLabelSelectsItsPriceByHorizontalPosition() {
        val frame = OcrFrame(
            width = 1000,
            height = 600,
            tokens = listOf(
                tess("ПЕЧЕНЬЕ ОВСЯНОЕ", 300, 200, 700, 240),
                rapid("LeHa nO KAPTe", 650, 150, 750, 190, 0.77f),
                // Подпись ближе к 243 по горизонтали, но по 2D — к 270.
                rapid("270", 430, 260, 650, 330),
                rapid("243", 620, 340, 840, 430),
                tess("99", 600, 270, 640, 310),
                tess("50", 780, 360, 820, 400),
                rapid("Bec:200r", 300, 330, 390, 360, 0.9f),
            ),
        )

        val text = merger.merge(frame)
        val parsed = parser.parse(text)

        assertWithMessage("подпись RapidOCR должна выбрать свою цену: $text").that(parsed).isNotNull()
        assertDecimal("обычная цена", parsed!!.price, "270.99")
        assertDecimal("цена по карте", parsed.priceByCard, "243.50")
        assertDecimal("цена за кг по карте", calculator.calculate(parsed)!!.value, "1217.50")
    }

    @Test
    fun smallDiscountPriceIsKeptRelativeToMainAnchor() {
        val frame = OcrFrame(
            width = 1100,
            height = 600,
            tokens = listOf(
                tess("ПЕЧЕНЬЕ ОВСЯНОЕ", 300, 200, 700, 240),
                rapid("LeHa nO KAPTe", 650, 190, 750, 230, 0.77f),
                // 110 px — меньше глобального порога из-за крупной цифры соседнего ценника,
                // но ≥60% от основной цены 170 px того же ценника.
                rapid("270", 430, 260, 650, 430),
                rapid("243", 620, 300, 840, 410),
                rapid("27", 1400, 250, 1500, 500),
                tess("99", 600, 300, 640, 340),
                tess("50", 790, 320, 830, 360),
                rapid("Bec:200r", 300, 440, 390, 470, 0.9f),
            ),
        )

        val text = merger.merge(frame)
        val parsed = parser.parse(text)

        assertWithMessage("скидочная цена меньшего размера не должна теряться: $text")
            .that(parsed).isNotNull()
        assertDecimal("обычная цена", parsed!!.price, "270.99")
        assertDecimal("цена по карте", parsed.priceByCard, "243.50")
        assertDecimal("цена за кг по карте", calculator.calculate(parsed)!!.value, "1217.50")
    }

    @Test
    fun rapidQuotedNameWinsOverShortTesseractName() {
        val frame = OcrFrame(
            width = 1000,
            height = 600,
            tokens = listOf(
                tess("ПЕЧЕНЬЕ", 300, 220, 500, 250, 0.65f),
                rapid("NEYEHbE \"OBCAHOE\"", 300, 215, 700, 250, 0.9f),
                tess("200 г", 500, 260, 620, 290),
                rapid("270", 400, 300, 620, 450),
                tess("99", 630, 320, 700, 380),
                rapid("py6/wt", 630, 390, 740, 420, 0.8f),
            ),
        )

        val text = merger.merge(frame)
        val parsed = parser.parse(text)

        assertWithMessage("транслитерированное имя с кавычками должно выиграть: $text")
            .that(parsed).isNotNull()
        assertWithMessage("имя из RapidOCR").that(parsed!!.name).isEqualTo("ПЕЧЕНЬЕ «ОВСЯНОЕ»")
        assertDecimal("цена", parsed.price, "270.99")
    }

    @Test
    fun lowConfidenceRapidNameIsIgnored() {
        val frame = OcrFrame(
            width = 1000,
            height = 600,
            tokens = listOf(
                tess("ПЕЧЕНЬЕ", 300, 220, 500, 250, 0.65f),
                rapid("NEYEHbE \"OBCAHOE\"", 300, 215, 700, 250, 0.55f),
                tess("200 г", 500, 260, 620, 290),
                rapid("270", 400, 300, 620, 450),
                tess("99", 630, 320, 700, 380),
                rapid("py6/wt", 630, 390, 740, 420, 0.8f),
            ),
        )

        val text = merger.merge(frame)
        val parsed = parser.parse(text)

        assertWithMessage("имя с низким confidence не должно попасть в сводку: $text")
            .that(text).doesNotContain("ОВСЯНОЕ")
        assertWithMessage("сводка без низкоуверенного имени разбирается: $text")
            .that(parsed).isNotNull()
        assertWithMessage("имя из Tesseract").that(parsed!!.name).isEqualTo("ПЕЧЕНЬЕ")
    }

    @Test
    fun rapidOnlyWeightWithCyrillicUnitIsUsed() {
        val frame = OcrFrame(
            width = 1000,
            height = 600,
            tokens = listOf(
                tess("ПЕЧЕНЬЕ", 300, 220, 700, 250),
                rapid("Bec:200r", 300, 260, 390, 290, 0.9f),
                rapid("270", 400, 300, 620, 450),
                tess("99", 630, 320, 700, 380),
                rapid("py6/wt", 630, 390, 740, 420, 0.8f),
            ),
        )

        val text = merger.merge(frame)
        val parsed = parser.parse(text)

        assertWithMessage("вес из RapidOCR должен разобраться: $text").that(parsed).isNotNull()
        assertDecimal("вес из RapidOCR", parsed!!.packageAmount, "200")
        assertDecimal("цена", parsed.price, "270.99")
    }

    @Test
    fun reversedDecimalWeightRangeIsNotUsed() {
        val base = case3().frame
        val frame = base.copy(
            tokens = base.tokens.map { token ->
                if ("230-240г" in token.text) {
                    token.copy(text = token.text.replace("230-240г", "240,5-230,5г"))
                } else {
                    token
                }
            },
        )

        val text = merger.merge(frame)

        assertWithMessage("перевёрнутый диапазон не должен стать весом: $text")
            .that(parser.parse(text)).isNull()
    }

    @Test
    fun postnoeAndSauceNoiseAreNotIncludedInName() {
        val base = case1().frame
        // Оставляем одно короткое слово Tesseract, чтобы источником имени стал RapidOCR
        // с кавычками: иначе шум отфильтровался бы вместе со всем Rapid-именем.
        val tokens = base.tokens.filterNot { token ->
            token.source == OcrSource.TESSERACT &&
                ("Биг Ланч" in token.text || "бургиньон" in token.text || "лесными" in token.text)
        } + listOf(
            rapid("постное", 480, 300, 570, 330, 0.9f),
            rapid("томатном соусе", 700, 300, 900, 330, 0.9f),
        )
        val frame = base.copy(tokens = tokens)

        val text = merger.merge(frame)
        val parsed = parser.parse(text)

        assertWithMessage("шум в полосе названия: $text").that(parsed).isNotNull()
        assertWithMessage("имя из RapidOCR без шума: $text")
            .that(parsed!!.name).isEqualTo("«Биг Ланч»")
        assertWithMessage("постное не должно попасть в сводку: $text")
            .that(text).doesNotContain("постное")
        assertWithMessage("соус не должен попасть в сводку: $text")
            .that(text).doesNotContain("томатном")
    }

    @Test
    fun emptyFrameProducesEmptyText() {
        assertThat(merger.merge(OcrFrame(1080, 720, emptyList()))).isEmpty()
    }

    @Test
    fun frameWithOnlyNoiseProducesNoPriceTag() {
        val frame = OcrFrame(
            width = 1080,
            height = 720,
            tokens = listOf(
                tess("21.09.26", 100, 100, 200, 130),
                tess("4600335001176", 100, 140, 300, 170),
                rapid("86665", 100, 180, 200, 210, 0.8f),
            ),
        )

        val text = merger.merge(frame)

        assertWithMessage("шум не должен давать ценник: $text").that(parser.parse(text)).isNull()
    }

    @Test
    fun rublesAtExactConfidenceThresholdAreUsed() {
        val frame = OcrFrame(
            width = 1000,
            height = 600,
            tokens = listOf(
                tess("Молоко", 380, 250, 550, 290),
                tess("1 кг", 480, 275, 590, 295),
                tess("99", 602, 320, 680, 390),
                rapid("49", 400, 300, 600, 450, 0.7f),
            ),
        )

        val text = merger.merge(frame)
        assertWithMessage("сводка на границе confidence: $text").that(parser.parse(text)).isNotNull()
        assertDecimal("цена на границе confidence", parser.parse(text)!!.price, "49.99")
    }

    @Test
    fun rublesBelowCalibratedThresholdAreIgnored() {
        val frame = OcrFrame(
            width = 1000,
            height = 600,
            tokens = listOf(
                tess("Паштет", 380, 250, 550, 290),
                tess("70 г", 450, 275, 560, 296),
                tess("99", 602, 320, 680, 390),
                rapid("49", 400, 300, 600, 450, 0.69f),
            ),
        )

        val text = merger.merge(frame)

        // Порог откалиброван на реальных JPEG: якорь 0.69 не должен собраться с копейками 99.
        assertWithMessage("низкий confidence не должен дать цену 49,99: $text")
            .that(text).doesNotContain("49,99")
        assertWithMessage("без якоря валютная строка не строится: $text")
            .that(text).doesNotContain("₽")
        assertWithMessage("fallback всё равно разбирается: $text").that(parser.parse(text)).isNotNull()
        assertDecimal("цена из fallback", parser.parse(text)!!.price, "99")
    }

    @Test
    fun smallDigitsNearFrameCenterAreNotTreatedAsPriceAnchor() {
        val frame = OcrFrame(
            width = 1000,
            height = 600,
            tokens = listOf(
                tess("Молоко", 400, 200, 600, 230),
                tess("1 кг", 430, 230, 560, 250),
                // Мелкая цифра ближе к центру кадра, чем настоящая цена.
                rapid("12", 480, 290, 540, 320),
                rapid("139", 450, 300, 650, 450, 0.95f),
                tess("99", 660, 320, 740, 390),
                rapid("py6/wt", 650, 400, 760, 430, 0.8f),
            ),
        )

        val text = merger.merge(frame)
        val parsed = parser.parse(text)

        assertWithMessage("мелкая цифра не должна стать ценой: $text").that(parsed).isNotNull()
        assertDecimal("цена крупного ценника", parsed!!.price, "139.99")
    }

    @Test
    fun decimalWeightRangeWithCommaIsKept() {
        val base = case3().frame
        val frame = base.copy(
            tokens = base.tokens.map { token ->
                if ("230-240г" in token.text) {
                    token.copy(text = token.text.replace("230-240г", "230,5-240,5г"))
                } else {
                    token
                }
            },
        )

        val text = merger.merge(frame)
        val parsed = parser.parse(text)

        assertWithMessage("диапазон с запятой должен попасть в сводку: $text").that(parsed).isNotNull()
        assertDecimal("верхняя граница диапазона", parsed!!.packageAmount, "240.5")
        assertWithMessage("подпись десятичного диапазона").that(parsed.packageAmountLabel)
            .isEqualTo("230,5-240,5 г")
    }

    @Test
    fun noiseInsideAnchorBandIsNotIncludedInName() {
        val base = case1().frame
        val frame = base.copy(
            tokens = base.tokens + listOf(
                tess("Акция", 500, 320, 570, 345),
                tess("Стакан", 500, 322, 580, 347),
            ),
        )

        val text = merger.merge(frame)
        assertWithMessage("сводка с шумом внутри ценника: $text").that(parser.parse(text)).isNotNull()
        assertThat(parser.parse(text)!!.name)
            .isEqualTo("Лапша «Биг Ланч» бургиньон с говядиной и лесными грибами")
    }

    @Test
    fun sideTagKopecksAndUnitAreNotBorrowed() {
        val frame = OcrFrame(
            width = 1080,
            height = 720,
            tokens = listOf(
                tess("Консервы рыбные шпроты в", 494, 216, 875, 255),
                tess("масле 230-240г", 562, 247, 805, 276, 0.94f),
                // Центральный ценник: копейки 99 и шт.
                tess("99", 731, 349, 791, 398),
                rapid("139", 576, 297, 795, 413),
                rapid("py6/ut", 725, 393, 794, 422, 0.79f),
                // Соседний ценник: копейки 88 и кг не должны попасть в центральную сводку.
                rapid("99", 819, 311, 897, 370),
                tess("88", 899, 311, 960, 370),
                rapid("py6./kr", 899, 367, 980, 394, 0.79f),
            ),
        )

        val text = merger.merge(frame)
        assertWithMessage("сводка с соседним ценником: $text").that(text).contains("139,99")
        assertWithMessage("единица центрального ценника: $text").that(text).contains("₽/шт")
        assertWithMessage("копейки соседа не должны попасть в цену: $text").that(text).doesNotContain("139,88")
        assertWithMessage("единица соседа не должна попасть в цену: $text").that(text).doesNotContain("₽/кг")
        assertDecimal("цена центрального ценника", parser.parse(text)!!.price, "139.99")
    }

    @Test
    fun anchorIsNearestToFrameCenterEvenWhenSideTagHasHigherConfidence() {
        val frame = OcrFrame(
            width = 1000,
            height = 600,
            tokens = listOf(
                // Боковой ценник идёт первым и имеет confidence выше центрального.
                rapid("99", 50, 270, 200, 400, 0.99f),
                tess("99", 63, 309, 143, 370),
                rapid("py6/ut", 55, 405, 180, 435, 0.8f),
                rapid("139", 450, 270, 600, 400, 0.91f),
                tess("Консервы рыбные шпроты в", 430, 200, 700, 245),
                tess("масле 230-240г", 480, 240, 620, 275, 0.94f),
                tess("99", 602, 280, 680, 340),
                rapid("py6/uT", 611, 385, 691, 412, 0.79f),
            ),
        )

        val text = merger.merge(frame)
        assertWithMessage("сводка с двумя ценниками: $text").that(parser.parse(text)).isNotNull()
        val parsed = parser.parse(text)!!
        assertThat(parsed.name).isEqualTo("Консервы рыбные шпроты в масле")
        assertDecimal("цена центрального ценника", parsed.price, "139.99")
    }

    @Test
    fun localRealTagFixturesMatchSharedImageTestCases() {
        val local = listOf(
            case1(), case2(), case3(), case4(), case5(), case6(), case7(),
            case8(), case9(), case10(), case11(), case12(), case13(),
        ).associateBy { it.id }

        assertThat(local.keys).containsExactlyElementsIn(RealPriceTagCases.all.map { it.id })
        RealPriceTagCases.all.forEach { shared ->
            val case = local[shared.id] ?: error("Нет локального кейса ${shared.id}")
            assertThat(case.name).isEqualTo(shared.name)
            assertThat(case.amount).isEqualTo(shared.amount)
            assertThat(case.label).isEqualTo(shared.amountLabel)
            assertThat(case.unit).isEqualTo(shared.packageUnit)
            assertThat(case.price).isEqualTo(shared.price)
            assertThat(case.priceByCard).isEqualTo(shared.priceByCard)
            assertThat(case.unitPrice).isEqualTo(shared.unitPrice)
            assertThat(case.unitPriceUnit).isEqualTo(shared.unitPriceUnit)
        }
    }

    private fun tess(
        text: String,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        confidence: Float = 0.9f,
    ) = OcrToken(text, OcrBox(left, top, right, bottom), confidence, OcrSource.TESSERACT)

    private fun rapid(
        text: String,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        confidence: Float = 1f,
    ) = OcrToken(text, OcrBox(left, top, right, bottom), confidence, OcrSource.RAPID_OCR)
}
