package com.pricelens.domain.parser

import com.google.common.truth.Truth.assertThat
import com.pricelens.domain.model.UnitPrice
import com.pricelens.domain.model.WeightUnit
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class PriceTagParserTest {
    private val parser = PriceTagParser()

    @Test
    fun parsesGramsWithCommaPrice() {
        val parsed = parser.parse("Творог 5% 350 г 240,00 ₽")

        assertThat(parsed).isNotNull()
        parsed!!.let {
            assertThat(it.name).isEqualTo("Творог")
            assertDecimal(it.packageAmount, "350")
            assertThat(it.packageUnit).isEqualTo(WeightUnit.GRAM)
            assertDecimal(it.price, "240.00")
            assertThat(it.explicitUnitPrice).isNull()
            assertThat(it.sourceText).isEqualTo("Творог 5% 350 г 240,00 ₽")
        }
    }

    @Test
    fun parsesKilogramsAndConvertsToGrams() {
        val parsed = parser.parse("Сыр 2,49 кг / 399,00 ₽")

        assertThat(parsed).isNotNull()
        parsed!!.let {
            assertDecimal(it.packageAmount, "2490")
            assertThat(it.packageUnit).isEqualTo(WeightUnit.GRAM)
            assertDecimal(it.price, "399.00")
        }
    }

    @Test
    fun parsesLiterWithRubleWord() {
        val parsed = parser.parse("Молоко 1 л / 129,90 руб.")

        assertThat(parsed).isNotNull()
        parsed!!.let {
            assertDecimal(it.packageAmount, "1000")
            assertThat(it.packageUnit).isEqualTo(WeightUnit.MILLILITER)
            assertDecimal(it.price, "129.90")
        }
    }

    @Test
    fun parsesMillilitersWithoutSpace() {
        val parsed = parser.parse("950 мл / 119 ₽")

        assertThat(parsed).isNotNull()
        parsed!!.let {
            assertDecimal(it.packageAmount, "950")
            assertThat(it.packageUnit).isEqualTo(WeightUnit.MILLILITER)
            assertDecimal(it.price, "119")
        }
    }

    @Test
    fun parsesShortRubleFormAndLowercaseUnit() {
        val parsed = parser.parse("130г / 89,50 руб.")

        assertThat(parsed).isNotNull()
        parsed!!.let {
            assertDecimal(it.packageAmount, "130")
            assertThat(it.packageUnit).isEqualTo(WeightUnit.GRAM)
            assertDecimal(it.price, "89.50")
        }
    }

    @Test
    fun parsesPriceWithoutCurrency() {
        val parsed = parser.parse("350 г 240.00")

        assertThat(parsed).isNotNull()
        assertDecimal(parsed!!.price, "240.00")
    }

    @Test
    fun parsesUppercaseCyrillicExplicitUnitPrice() {
        val parsed = parser.parse("ЦЕНА ЗА 1 КГ: 685,71 РУБ.")

        assertThat(parsed).isNotNull()
        parsed!!.let {
            assertDecimal(it.explicitUnitPrice, "685.71")
            assertThat(it.explicitUnitPriceUnit).isEqualTo(UnitPrice.Unit.KILOGRAM)
            assertThat(it.packageAmount).isNull()
            assertThat(it.packageUnit).isNull()
            assertThat(it.price).isNull()
        }
    }

    @Test
    fun keepsExplicitUnitPriceAlongsidePackagePrice() {
        val parsed = parser.parse("Цена за 1 кг: 685,71 ₽\nТворог 350 г\n240 ₽")

        assertThat(parsed).isNotNull()
        parsed!!.let {
            assertThat(it.name).isEqualTo("Творог")
            assertDecimal(it.packageAmount, "350")
            assertDecimal(it.price, "240")
            assertDecimal(it.explicitUnitPrice, "685.71")
            assertThat(it.explicitUnitPriceUnit).isEqualTo(UnitPrice.Unit.KILOGRAM)
        }
    }

    @Test
    fun ignoresUnitPricePhraseWhenDetectingPackageAmount() {
        val parsed = parser.parse("цена за 1 кг\n350 г\n240 ₽")

        assertThat(parsed).isNotNull()
        parsed!!.let {
            assertDecimal(it.packageAmount, "350")
            assertThat(it.packageUnit).isEqualTo(WeightUnit.GRAM)
            assertDecimal(it.price, "240")
            assertThat(it.explicitUnitPrice).isNull()
        }
    }

    @Test
    fun ignoresBarcodeDateAndPercentNoise() {
        val parsed = parser.parse("Молоко 1 л 129,90 460123 01.01.26 2,5%")

        assertThat(parsed).isNotNull()
        parsed!!.let {
            assertDecimal(it.packageAmount, "1000")
            assertDecimal(it.price, "129.90")
        }
    }

    @Test
    fun preservesNameCapitalizationAndLeadingSpaces() {
        val parsed = parser.parse("  Творог 350 г 240 ₽")

        assertThat(parsed).isNotNull()
        assertThat(parsed!!.name).isEqualTo("Творог")
    }

    @Test
    fun treatsNonBreakingSpacesAsRegularSpaces() {
        val parsed = parser.parse("350\u00A0г\u00A0240\u00A0₽")

        assertThat(parsed).isNotNull()
        parsed!!.let {
            assertDecimal(it.packageAmount, "350")
            assertDecimal(it.price, "240")
        }
    }

    @Test
    fun parsesTwoPricesWithCardDiscount() {
        val parsed = parser.parse("ПЕЧЕНЬЕ ОВСЯНОЕ С ЧЕРНОСЛИВОМ\n200 г\n270 ₽\n243 ₽ по карте")

        assertThat(parsed).isNotNull()
        parsed!!.let {
            assertThat(it.name).isEqualTo("ПЕЧЕНЬЕ ОВСЯНОЕ С ЧЕРНОСЛИВОМ")
            assertDecimal(it.packageAmount, "200")
            assertDecimal(it.price, "270")
            assertDecimal(it.priceByCard, "243")
            assertThat(it.explicitUnitPrice).isNull()
        }
    }

    @Test
    fun parsesCardPriceBeforeNumberAndKeepsItOutOfName() {
        val parsed = parser.parse("Печенье\n200 г\nцена по карте 243 ₽\n270 ₽")

        assertThat(parsed).isNotNull()
        parsed!!.let {
            assertThat(it.name).isEqualTo("Печенье")
            assertDecimal(it.price, "270")
            assertDecimal(it.priceByCard, "243")
        }
    }

    @Test
    fun cardPriceIsNotConfusedWithExplicitUnitPrice() {
        val parsed = parser.parse("350 г\n240 ₽\nцена по карте 200 ₽\nцена за 1 кг: 685,71 ₽")

        assertThat(parsed).isNotNull()
        parsed!!.let {
            assertDecimal(it.price, "240")
            assertDecimal(it.priceByCard, "200")
            assertDecimal(it.explicitUnitPrice, "685.71")
        }
    }

    @Test
    fun parsesFractionalCardPriceInBothFormats() {
        val after = parser.parse("Печенье\n200 г\n270 ₽\n243,50 ₽ по карте")
        val before = parser.parse("Печенье\n200 г\n270 ₽\nцена по карте 243,50 руб.")

        assertThat(after).isNotNull()
        assertDecimal(after!!.priceByCard, "243.50")
        assertDecimal(after.price, "270")
        assertThat(before).isNotNull()
        assertDecimal(before!!.priceByCard, "243.50")
        assertDecimal(before.price, "270")
    }

    @Test
    fun zeroCardPriceIsIgnored() {
        val parsed = parser.parse("Печенье\n200 г\n270 ₽\n0 ₽ по карте")

        assertThat(parsed).isNotNull()
        assertDecimal(parsed!!.price, "270")
        assertThat(parsed.priceByCard).isNull()
    }

    @Test
    fun cardPriceWithOnlyExplicitUnitPriceIsKeptOutOfName() {
        val parsed = parser.parse("243 ₽ по карте\nцена за 1 кг: 685,71 ₽")

        assertThat(parsed).isNotNull()
        parsed!!.let {
            assertDecimal(it.explicitUnitPrice, "685.71")
            assertDecimal(it.priceByCard, "243")
            assertThat(it.explicitUnitPriceUnit).isEqualTo(UnitPrice.Unit.KILOGRAM)
            assertThat(it.name.isNullOrEmpty()).isTrue()
        }
    }

    @Test
    fun returnsNullWhenPriceIsMissing() {
        assertThat(parser.parse("Творог 5% 350 г")).isNull()
    }

    @Test
    fun returnsNullWhenAmountIsMissing() {
        assertThat(parser.parse("240 ₽")).isNull()
    }

    @Test
    fun returnsNullForBlankText() {
        assertThat(parser.parse("   ")).isNull()
    }

    @Test
    fun returnsNullForZeroAmount() {
        assertThat(parser.parse("0 г 100 ₽")).isNull()
    }

    @Test
    fun parsesExplicitPricePerLiter() {
        val parsed = parser.parse("Цена за 1 л: 129,90 ₽")

        assertThat(parsed).isNotNull()
        parsed!!.let {
            assertDecimal(it.explicitUnitPrice, "129.90")
            assertThat(it.explicitUnitPriceUnit).isEqualTo(UnitPrice.Unit.LITER)
            assertThat(it.packageAmount).isNull()
            assertThat(it.packageUnit).isNull()
            assertThat(it.price).isNull()
        }
    }

    @Test
    fun parsesZeroPointNinetyFiveLiter() {
        val parsed = parser.parse("Сок 0,95 л 149 ₽")

        assertThat(parsed).isNotNull()
        parsed!!.let {
            assertDecimal(it.packageAmount, "950")
            assertThat(it.packageUnit).isEqualTo(WeightUnit.MILLILITER)
            assertDecimal(it.price, "149")
        }
    }

    @Test
    fun returnsNullForZeroPrice() {
        assertThat(parser.parse("350 г 0 ₽")).isNull()
    }

    @Test
    fun parsesWeightRangeAndKeepsItsLabel() {
        val parsed = parser.parse("Консервы рыбные шпроты в масле\n230-240 г\n139,99 ₽/шт")

        assertThat(parsed).isNotNull()
        parsed!!.let {
            assertThat(it.name).isEqualTo("Консервы рыбные шпроты в масле")
            assertThat(it.packageAmountLabel).isEqualTo("230-240 г")
            assertDecimal(it.packageAmount, "240")
            assertThat(it.packageUnit).isEqualTo(WeightUnit.GRAM)
            assertDecimal(it.price, "139.99")
        }
    }

    @Test
    fun preservesQuotesAndSlashAbbreviationsInName() {
        val parsed = parser.parse(
            "Консервы рыбные тунец «За Родину» с киноа и травами, ж/б\n160 г\n119,99 ₽/шт",
        )

        assertThat(parsed).isNotNull()
        assertThat(parsed!!.name)
            .isEqualTo("Консервы рыбные тунец «За Родину» с киноа и травами, ж/б")
        assertDecimal(parsed.price, "119.99")
    }

    @Test
    fun keepsExactPateNameEndingAndKopecks() {
        val parsed = parser.parse("Паштет «В Три Приёма» со вкусом копчёная индейка\n70 г\n24,99 ₽/шт")

        assertThat(parsed).isNotNull()
        parsed!!.let {
            assertThat(it.name).isEqualTo("Паштет «В Три Приёма» со вкусом копчёная индейка")
            assertDecimal(it.price, "24.99")
        }
    }

    @Test
    fun parsesExactPateKopecks() {
        // Защита от ошибочных копеек 66 живёт на уровне OcrTokenMerger (5.jpg/6.jpg),
        // здесь проверяется, что парсер не теряет копейки в канонической строке.
        val parsed = parser.parse("Паштет «В Три Приёма» со вкусом бекона\n70г\n24,99 ₽/шт")

        assertThat(parsed).isNotNull()
        assertDecimal(parsed!!.price, "24.99")
    }

    @Test
    fun ignoresMilligramsAsPackageAmount() {
        assertThat(parser.parse("Витамины 500 мг 299 ₽")).isNull()
    }

    @Test
    fun pricePerKgWithoutDigitOneIsNotExplicitUnitPrice() {
        assertThat(parser.parse("Цена за кг: 685,71 ₽")).isNull()
    }

    private fun assertDecimal(actual: BigDecimal?, expected: String) {
        assertThat(actual).isNotNull()
        assertThat(actual!!.compareTo(BigDecimal(expected))).isEqualTo(0)
    }
}
