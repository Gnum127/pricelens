package com.pricelens.domain.parser

import com.pricelens.domain.model.OcrBox
import com.pricelens.domain.model.OcrFrame
import com.pricelens.domain.model.OcrSource
import com.pricelens.domain.model.OcrToken

/**
 * Сводит токены двух движков в один блок текста для парсера.
 *
 * Крупные рубли читает RapidOCR, мелкие копейки и слова — Tesseract: у Tesseract
 * крупный шрифт теряется, у RapidOCR — кириллица и мелкие цифры. Токены приходят
 * в координатах одного кадра, поэтому их можно сопоставить по положению и выбрать
 * ценник ближе к центру кадра. Остальные токены распределяются между найденными
 * центрами рублистых box-ов: соседний ценник не должен подписывать выбранный.
 */
class OcrTokenMerger {
    fun merge(frame: OcrFrame): String {
        val tokens = frame.tokens.filter { it.text.isNotBlank() }
        if (tokens.isEmpty()) return ""

        val rapid = tokens.filter { it.source == OcrSource.RAPID_OCR }
        val tesseract = tokens.filter { it.source == OcrSource.TESSERACT }
        val anchors = findAnchors(rapid)
        val anchor = findAnchor(frame, anchors) ?: return fallbackText(tesseract, rapid)

        val weightToken = tesseract.firstNotNullOfOrNull { token ->
            token
                .takeIf { inTagBand(it, anchor) && inNameBand(it, anchor) && belongsToAnchor(it, anchor, anchors) }
                ?.let(::asWeight)
        }
        val weight = weightToken?.let { weightRegex.find(it.text.replace('\u00A0', ' ')) }
        val kopecks = findKopecks(anchor, anchors, tesseract, rapid)
        val priceLine = buildPriceLine(anchor, anchors, kopecks, rapid)
        val name = buildName(tesseract, anchor, anchors, weightToken, weight)

        return listOfNotNull(name, weight?.let(::normalizeWeight), priceLine)
            .joinToString("\n")
    }

    /** Крупные цифры ценника заметно выше копеек; по ним находят все ценники в кадре. */
    private fun findAnchors(rapid: List<OcrToken>): List<OcrToken> {
        val numeric = rapid.filter {
            it.text.matches(rublesRegex) && it.confidence >= MIN_RUBLES_CONFIDENCE
        }
        if (numeric.isEmpty()) return emptyList()
        val maxHeight = numeric.maxOf { it.box.height }
        return numeric.filter { it.box.height >= maxHeight * MIN_TAG_HEIGHT_RATIO }
    }

    private fun findAnchor(frame: OcrFrame, anchors: List<OcrToken>): OcrToken? {
        if (anchors.isEmpty()) return null
        val maxHeight = anchors.maxOf { it.box.height }
        return anchors
            .filter { it.box.height >= maxHeight * MIN_RUBLES_HEIGHT_RATIO }
            .minWithOrNull(
                compareBy<OcrToken> { distanceToFrameCenter(it.box, frame) }
                    .thenByDescending { it.box.height }
                    .thenByDescending { it.confidence },
            )
    }

    private fun distanceToFrameCenter(box: OcrBox, frame: OcrFrame): Float {
        val dx = box.centerX - frame.width / 2f
        val dy = box.centerY - frame.height / 2f
        return dx * dx + dy * dy
    }

    private fun centerDistance(first: OcrBox, second: OcrBox): Float {
        val dx = first.centerX - second.centerX
        val dy = first.centerY - second.centerY
        return dx * dx + dy * dy
    }

    /** Каждый токен принадлежит ближайшему центру рублей; при равенстве выигрывает выбранный якорь. */
    private fun belongsToAnchor(token: OcrToken, anchor: OcrToken, anchors: List<OcrToken>): Boolean {
        val distance = centerDistance(token.box, anchor.box)
        return anchors.all { other -> other === anchor || distance <= centerDistance(token.box, other.box) }
    }

    /** Все токены одного ценника лежат в горизонтальной полосе его рублевого блока. */
    private fun inTagBand(token: OcrToken, anchor: OcrToken): Boolean {
        val left = anchor.box.left - anchor.box.width * HORIZONTAL_BAND
        val right = anchor.box.right + anchor.box.width * HORIZONTAL_BAND
        return token.box.right >= left && token.box.left <= right
    }

    private fun inNameBand(token: OcrToken, anchor: OcrToken): Boolean =
        token.box.bottom >= anchor.box.top - anchor.box.height * NAME_ABOVE &&
            token.box.bottom <= anchor.box.top + anchor.box.height * NAME_BAND

    private fun findKopecks(
        anchor: OcrToken,
        anchors: List<OcrToken>,
        tesseract: List<OcrToken>,
        rapid: List<OcrToken>,
    ): OcrToken? {
        val bandTop = anchor.box.top - anchor.box.height * VERTICAL_BAND
        val bandBottom = anchor.box.bottom + anchor.box.height * VERTICAL_BAND
        val maxRight = anchor.box.right + anchor.box.width * KOPECK_RIGHT_BAND

        fun isCandidate(token: OcrToken): Boolean =
            token.text.matches(kopecksRegex) &&
                token !in anchors &&
                token.box.centerX >= anchor.box.left &&
                token.box.right <= maxRight &&
                token.box.centerY in bandTop..bandBottom &&
                token.box.height < anchor.box.height * MAX_KOPECK_HEIGHT &&
                inTagBand(token, anchor) &&
                belongsToAnchor(token, anchor, anchors)

        // Tesseract вернее на мелком шрифте; RapidOCR остаётся запасным источником.
        return (tesseract + rapid)
            .filter(::isCandidate)
            .sortedWith(
                compareByDescending<OcrToken> { it.source == OcrSource.TESSERACT }
                    .thenByDescending { it.confidence },
            )
            .firstOrNull()
    }

    private fun buildPriceLine(
        anchor: OcrToken,
        anchors: List<OcrToken>,
        kopecks: OcrToken?,
        rapid: List<OcrToken>,
    ): String {
        val kopecksText = kopecks?.text?.trim()
        val price = if (kopecksText != null) "${anchor.text.trim()},$kopecksText" else anchor.text.trim()
        val unit = rapid.firstOrNull { token ->
            inTagBand(token, anchor) &&
                belongsToAnchor(token, anchor, anchors) &&
                token.box.top < anchor.box.bottom &&
                token.box.bottom > anchor.box.top &&
                token.text.matches(unitRegex)
        }?.text?.lowercase().orEmpty()

        val suffix = when {
            "kr" in unit || "кг" in unit -> "₽/кг"
            unit.isNotBlank() -> "₽/шт"
            else -> "₽"
        }
        return "$price $suffix"
    }

    private fun buildName(
        tesseract: List<OcrToken>,
        anchor: OcrToken,
        anchors: List<OcrToken>,
        weightToken: OcrToken?,
        weight: MatchResult?,
    ): String? {
        val name = tesseract
            .filter {
                inTagBand(it, anchor) && inNameBand(it, anchor) && belongsToAnchor(it, anchor, anchors)
            }
            .filter { it.text.any(Char::isLetter) }
            .map { token ->
                val text = if (token === weightToken && weight != null) {
                    token.text.replaceRange(weight.range, " ")
                } else {
                    token.text
                }
                text.trim()
            }
            .filter { it.isNotBlank() && !isNoise(it) }
            .joinToString(" ")
            .replace(quotedTextRegex) { "«${it.groupValues[1]}»" }
            .trim()

        return name.takeIf { it.isNotBlank() }
    }

    private fun asWeight(token: OcrToken): OcrToken? =
        weightRegex.find(token.text.replace('\u00A0', ' '))?.let { token }

    private fun normalizeWeight(weight: MatchResult): String {
        val amount = weight.groupValues[1].replace(rangeDashRegex, "-")
        val unit = canonicalUnit(weight.groupValues[2].lowercase())
        return "$amount $unit"
    }

    private fun canonicalUnit(unit: String): String = when (unit) {
        "kg" -> "кг"
        "g", "r" -> "г"
        "ml" -> "мл"
        "l" -> "л"
        else -> unit
    }

    private fun isNoise(text: String): Boolean {
        val value = text.trim()
        return dateRegex.matches(value) ||
            longDigitRegex.matches(value) ||
            rubleUnitRegex.containsMatchIn(value) ||
            value.lowercase() in noiseWords
    }

    /** Без якоря нет надёжной привязки к ценнику: сводка строится по порядку строк. */
    private fun fallbackText(tesseract: List<OcrToken>, rapid: List<OcrToken>): String =
        (tesseract + rapid.filter { it.text.matches(digitsRegex) || it.text.matches(unitRegex) })
            .sortedWith(compareBy({ it.box.top }, { it.box.left }))
            .joinToString(" ") { it.text.trim() }
            .replace(quotedTextRegex) { "«${it.groupValues[1]}»" }

    private companion object {
        const val MIN_RUBLES_CONFIDENCE = 0.9f
        const val MIN_TAG_HEIGHT_RATIO = 0.5f
        const val MIN_RUBLES_HEIGHT_RATIO = 0.6f
        const val VERTICAL_BAND = 0.35f
        const val HORIZONTAL_BAND = 0.6f
        const val KOPECK_RIGHT_BAND = 0.8f
        const val NAME_ABOVE = 0.6f
        const val NAME_BAND = 0.1f
        const val MAX_KOPECK_HEIGHT = 0.8f

        val rublesRegex = Regex("""\d{2,3}""")
        val kopecksRegex = Regex("""\d{2}""")
        val digitsRegex = Regex("""\d+(?:[.,]\d+)?""")
        val unitRegex = Regex("""(?iu)(?:руб|py6|р)\s*[./\s]*(?:шт|kr|kg|кг|wt|ut|l|л)(?!\p{L})""")
        val weightRegex = Regex(
            """(?iu)(\d+(?:[.,]\d+)?(?:\s*[-–—]\s*\d+(?:[.,]\d+)?)?)\s*(кг|kg|г|g|r|мл|ml|л|l)(?!\p{L})""",
        )
        val rangeDashRegex = Regex("""\s*[-–—]\s*""")
        val dateRegex = Regex("""\d{1,2}[./]\d{1,2}[./]\d{2,4}""")
        val longDigitRegex = Regex("""\d{6,}""")
        val rubleUnitRegex = Regex("""(?iu)(?<![\p{L}\d])(?:руб|py6|шт|wt|ut)(?![\p{L}\d])""")
        val quotedTextRegex = Regex(""""([^"]+)"""")
        val noiseWords = setOf(
            "цена", "вес", "объём", "объем", "акция", "скидка", "стакан",
        )
    }
}
