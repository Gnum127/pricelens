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
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

/**
 * Сырые токены сняты с реальных кадров 1.jpg–7.jpg: координаты RapidOCR взяты из его boxPoint,
 * слова и копейки — из word-level вывода Tesseract rus. Фотографии в репозиторий не кладутся.
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
    ).map { case -> DynamicTest.dynamicTest(case.id) { assertRealTag(case) } }

    private fun assertRealTag(case: Case) {
        val text = merger.merge(case.frame)
        assertWithMessage("${case.id}: сводка пустая").that(text.isBlank()).isFalse()

        val parsed = parser.parse(text)
        assertWithMessage("${case.id}: парсер не разобрал «$text»").that(parsed).isNotNull()
        parsed!!.let {
            assertWithMessage("${case.id}: название").that(it.name).isEqualTo(case.name)
            assertDecimal("${case.id}: вес", it.packageAmount, case.amount)
            assertWithMessage("${case.id}: подпись веса").that(it.packageAmountLabel).isEqualTo(case.label)
            assertWithMessage("${case.id}: единица").that(it.packageUnit).isEqualTo(case.unit)
            assertDecimal("${case.id}: цена", it.price, case.price)
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
                rapid("49", 400, 300, 600, 450, 0.9f),
            ),
        )

        val text = merger.merge(frame)
        assertWithMessage("сводка на границе confidence: $text").that(parser.parse(text)).isNotNull()
        assertDecimal("цена на границе confidence", parser.parse(text)!!.price, "49.99")
    }

    @Test
    fun rublesBelowConfidenceThresholdAreIgnored() {
        val frame = OcrFrame(
            width = 1000,
            height = 600,
            tokens = listOf(
                tess("Паштет", 380, 250, 550, 290),
                tess("70 г", 450, 275, 560, 296),
                tess("99", 602, 320, 680, 390),
                rapid("49", 400, 300, 600, 450, 0.89f),
            ),
        )

        val text = merger.merge(frame)

        // Если порог случайно снизить, якорь 49 соберётся с копейками 99 в цену 49,99 ₽.
        assertWithMessage("низкий confidence не должен дать цену 49,99: $text")
            .that(text).doesNotContain("49,99")
        assertWithMessage("без якоря валютная строка не строится: $text")
            .that(text).doesNotContain("₽")
        assertWithMessage("fallback всё равно разбирается: $text").that(parser.parse(text)).isNotNull()
        assertWithMessage("цена из fallback не равна 49,99: $text")
            .that(parser.parse(text)?.price?.compareTo(BigDecimal("49.99"))).isNotEqualTo(0)
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
