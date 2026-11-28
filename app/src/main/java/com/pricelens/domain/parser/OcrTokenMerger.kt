package com.pricelens.domain.parser

import com.pricelens.domain.model.OcrBox
import com.pricelens.domain.model.OcrFrame
import com.pricelens.domain.model.OcrSource
import com.pricelens.domain.model.OcrToken

/**
 * Сводит токены двух движков в один блок текста для парсера.
 *
 * Tesseract даёт чистые слова и копейки, но крупные цифры теряет. RapidOCR читает крупные
 * цифры, но bundled-модель возвращает кириллицу латиницей (`NEYEHbE`, `py6/WT`, `Bec:200r`).
 * Перед сопоставлением такой текст приводится к русским словам и единицам. Токены приходят
 * в координатах одного кадра, поэтому их можно сопоставить по положению и выбрать ценник
 * ближе к центру кадра.
 */
class OcrTokenMerger {
    fun merge(frame: OcrFrame): String {
        val tokens = frame.tokens
            .filter { it.text.isNotBlank() }
            .map { token ->
                if (token.source == OcrSource.RAPID_OCR) {
                    token.copy(text = normalizeRapidOcr(token.text))
                } else {
                    token.copy(text = normalizeQuotes(token.text))
                }
            }
        if (tokens.isEmpty()) return ""

        val rapid = tokens.filter { it.source == OcrSource.RAPID_OCR }
        val tesseract = tokens.filter { it.source == OcrSource.TESSERACT }
        val anchors = findAnchors(rapid, tokens)
        val anchor = findAnchor(frame, anchors) ?: return fallbackText(tesseract, rapid)

        // Все цены одного ценника объединяются в кластер: вторая цена не берётся от соседа.
        val cluster = anchors.filter { it === anchor || isSameTag(it, anchor) }
        val cardLabel = tokens.firstOrNull { token ->
            cardLabelRegex.containsMatchIn(token.text) &&
                cluster.any { inTagBand(token, it) && inWeightBand(token, it) }
        }
        // Подпись «по карте» указывает свою цену по горизонтали; вторая цена — обычная.
        val cardAnchor = cardLabel?.let { label ->
            cluster.minByOrNull { horizontalDistance(label.box, it.box) }
        }
        val primaryAnchor = cardAnchor?.let { card ->
            cluster.firstOrNull { it !== card } ?: card
        } ?: anchor

        val weightToken = tokens
            .filter { token ->
                cluster.any { inTagBand(token, it) && inWeightBand(token, it) } &&
                    belongsToCluster(token, cluster, anchors)
            }
            .mapNotNull(::asWeight)
            .sortedWith(
                compareByDescending<OcrToken> { it.confidence }
                    .thenByDescending { it.source == OcrSource.TESSERACT },
            )
            .firstOrNull()
        val weight = weightToken?.let { weightRegex.find(it.text.replace('\u00A0', ' ')) }
        val name = buildName(tokens, cluster, anchors, cardLabel)
        val priceLine = buildPriceLine(
            primaryAnchor,
            anchors,
            findKopecks(primaryAnchor, anchors, tokens),
            rapid,
        )
        val cardLine = cardAnchor
            ?.takeIf { it !== primaryAnchor }
            ?.let { card ->
                val cardPrice = buildPriceLine(
                    card,
                    anchors,
                    findKopecks(card, anchors, tokens),
                    rapid,
                    includeUnit = false,
                )
                "$cardPrice по карте"
            }

        return listOfNotNull(name, weight?.let(::normalizeWeight), priceLine, cardLine)
            .joinToString("\n")
    }

    /** Крупные цифры ценника заметно выше копеек; по ним находят все ценники в кадре. */
    private fun findAnchors(rapid: List<OcrToken>, allTokens: List<OcrToken>): List<OcrToken> {
        val numeric = rapid.filter { token ->
            token.text.matches(rublesRegex) &&
                (token.text.length == 3 && token.confidence >= MIN_THREE_DIGIT_CONFIDENCE ||
                    token.text.length == 2 && token.confidence >= MIN_RUBLES_CONFIDENCE)
        }
        // Младшая пара цифр справа от крупного числа — копейки, а не отдельный ценник.
        val rubles = numeric.filterNot { candidate ->
            numeric.any { other -> other !== candidate && isKopecksOf(candidate, other) }
        }
        // Если у «копеек» есть свои копейки правее, это отдельный ценник, а не дробная часть.
        val separateTags = numeric.filter { it !in rubles && hasOwnKopecks(it, allTokens) }
        val candidates = rubles + separateTags
        // Мелкие цифры на упаковке не должны становиться ценниками. Высота измеряется
        // среди крупных чисел, после того как копейки уже отфильтрованы по геометрии.
        return candidates.filter { hasSufficientHeight(it, candidates, MIN_TAG_HEIGHT_RATIO) }
    }

    /**
     * Цифра достаточно крупная либо сама по себе, либо относительно другой цены того же
     * ценника. Второе важно для скидочной цены: её box может быть заметно меньше обычной,
     * а соседний ценник в кадре — крупнее обоих.
     */
    private fun hasSufficientHeight(token: OcrToken, all: List<OcrToken>, ratio: Float): Boolean {
        val globalMaxHeight = all.maxOfOrNull { it.box.height } ?: return false
        if (token.box.height >= globalMaxHeight * ratio) return true
        return all.any { other ->
            other !== token &&
                isSameTag(other, token) &&
                other.box.height > token.box.height &&
                token.box.height >= other.box.height * ratio
        }
    }

    private fun hasOwnKopecks(candidate: OcrToken, tokens: List<OcrToken>): Boolean =
        tokens.any { other ->
            other !== candidate &&
                other.text.matches(kopecksRegex) &&
                other.box.left >= candidate.box.right - candidate.box.width * KOPECK_OVERLAP &&
                other.box.top >= candidate.box.top - candidate.box.height * VERTICAL_BAND &&
                other.box.bottom <= candidate.box.bottom + candidate.box.height * VERTICAL_BAND
        }

    private fun isKopecksOf(candidate: OcrToken, rubles: OcrToken): Boolean =
        candidate.box.width < rubles.box.width &&
            candidate.box.height <= rubles.box.height * MAX_KOPECK_HEIGHT &&
            candidate.box.left >= rubles.box.right - rubles.box.width * KOPECK_OVERLAP &&
            candidate.box.top >= rubles.box.top - rubles.box.height * VERTICAL_BAND &&
            candidate.box.bottom <= rubles.box.bottom + rubles.box.height * VERTICAL_BAND

    private fun findAnchor(frame: OcrFrame, anchors: List<OcrToken>): OcrToken? = anchors
        .filter { hasSufficientHeight(it, anchors, MIN_RUBLES_HEIGHT_RATIO) }
        .minWithOrNull(
            compareBy<OcrToken> { distanceToFrameCenter(it.box, frame) }
                .thenByDescending { it.box.height }
                .thenByDescending { it.confidence },
        )

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

    private fun horizontalDistance(first: OcrBox, second: OcrBox): Float {
        val dx = first.centerX - second.centerX
        return dx * dx
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

    /**
     * Название печатается над ценой. Нижняя граница жёстко привязана к box-у рублей,
     * верхняя — с запасом на пиксели: на некоторых ценниках название уходит на 100+ px вверх.
     */
    private fun inNameBand(token: OcrToken, anchor: OcrToken): Boolean =
        token.box.bottom >= anchor.box.top - maxOf(anchor.box.height * NAME_ABOVE, NAME_ABOVE_PX) &&
            token.box.bottom <= anchor.box.top + anchor.box.height * NAME_BAND

    /** Вес печатается под ценой или над ней, на старых ценниках — в той же строке. */
    private fun inWeightBand(token: OcrToken, anchor: OcrToken): Boolean =
        token.box.bottom >= anchor.box.top - maxOf(anchor.box.height * NAME_ABOVE, NAME_ABOVE_PX) &&
            token.box.bottom <= anchor.box.bottom + maxOf(anchor.box.height * WEIGHT_BELOW, WEIGHT_BELOW_PX)

    /** Токен относится к кластеру ценника, даже если он ближе к скидочной цене. */
    private fun belongsToCluster(
        token: OcrToken,
        cluster: List<OcrToken>,
        allAnchors: List<OcrToken>,
    ): Boolean {
        val nearest = cluster.minByOrNull { centerDistance(token.box, it.box) } ?: return false
        val best = centerDistance(token.box, nearest.box)
        return allAnchors.all { other ->
            cluster.any { it === other } || best <= centerDistance(token.box, other.box)
        }
    }

    /** Две цены одного ценника находятся рядом по обеим осям, но их box могут не пересекаться. */
    private fun isSameTag(first: OcrToken, second: OcrToken): Boolean {
        val maxHeight = maxOf(first.box.height, second.box.height)
        return centerDistance(first.box, second.box) <= maxHeight * maxHeight * SAME_TAG_DISTANCE
    }

    private fun findKopecks(
        anchor: OcrToken,
        anchors: List<OcrToken>,
        tokens: List<OcrToken>,
    ): OcrToken? {
        val bandTop = anchor.box.top - anchor.box.height * VERTICAL_BAND
        val bandBottom = anchor.box.bottom + anchor.box.height * VERTICAL_BAND
        val minLeft = anchor.box.right - anchor.box.width * KOPECK_OVERLAP
        val maxRight = anchor.box.right + anchor.box.width * KOPECK_RIGHT_BAND

        fun isCandidate(token: OcrToken): Boolean =
            token.text.matches(kopecksRegex) &&
                token !in anchors &&
                token.box.left >= minLeft &&
                token.box.right <= maxRight &&
                token.box.centerY in bandTop..bandBottom &&
                token.box.height < anchor.box.height * MAX_KOPECK_HEIGHT &&
                inTagBand(token, anchor) &&
                belongsToAnchor(token, anchor, anchors)

        // Tesseract вернее на мелком шрифте; RapidOCR остаётся запасным источником.
        return tokens
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
        includeUnit: Boolean = true,
    ): String {
        val kopecksText = kopecks?.text?.trim()
        val price = if (kopecksText != null) "${anchor.text.trim()},$kopecksText" else anchor.text.trim()
        val unit = if (includeUnit) {
            rapid.firstOrNull { token ->
                inTagBand(token, anchor) &&
                    belongsToAnchor(token, anchor, anchors) &&
                    token.box.top < anchor.box.bottom &&
                    token.box.bottom > anchor.box.top &&
                    token.text.matches(unitRegex)
            }?.text?.lowercase().orEmpty()
        } else {
            ""
        }

        val suffix = when {
            !includeUnit -> "₽"
            "kr" in unit || "кг" in unit -> "₽/кг"
            unit.isNotBlank() -> "₽/шт"
            else -> "₽"
        }
        return "$price $suffix"
    }

    private fun buildName(
        tokens: List<OcrToken>,
        cluster: List<OcrToken>,
        anchors: List<OcrToken>,
        cardLabel: OcrToken?,
    ): String? {
        fun candidates(source: OcrSource): List<OcrToken> = tokens
            .filter { it.source == source }
            .filter { it.confidence >= MIN_NAME_CONFIDENCE }
            .filter { it !== cardLabel && it !in anchors }
            .filterNot { cardLabelRegex.containsMatchIn(it.text) }
            .mapNotNull { token ->
                // Вес часто печатается в той же строке: убираем только число с единицей.
                val cleaned = token.text.replace(weightRegex, " ").replace('\u00A0', ' ').trim()
                if (cleaned.any(Char::isLetter) && !cleaned.first().isDigit()) {
                    token.copy(text = cleaned)
                } else {
                    null
                }
            }
            .filterNot(::isNoise)
            .filter {
                cluster.any { candidate -> inTagBand(it, candidate) && inNameBand(it, candidate) } &&
                    belongsToCluster(it, cluster, anchors)
            }
            .sortedWith(compareBy({ it.box.top }, { it.box.left }))

        val byTesseract = candidates(OcrSource.TESSERACT)
        val byRapid = candidates(OcrSource.RAPID_OCR)
        // У Tesseract слова чище, но на размытых ценниках RapidOCR даёт связную строку
        // и кавычки, которые Tesseract теряет. Берём один источник, чтобы не смешивать
        // дублирующие варианты одного и того же названия.
        val tessScore = nameScore(byTesseract)
        val rapidScore = nameScore(byRapid)
        val tesseractIsReadable = byTesseract.size >= MIN_TESS_WORDS && tessScore >= MIN_TESS_SCORE
        val rapidAddsQuotes = hasQuotes(byRapid) && !hasQuotes(byTesseract)
        val preferRapid = byRapid.isNotEmpty() &&
            (!tesseractIsReadable || rapidAddsQuotes && rapidScore >= tessScore)
        val chosen = if (preferRapid) byRapid else byTesseract

        return chosen
            .joinToString(" ") { it.text.trim().replace(trailingDigitsRegex, "") }
            .let(::normalizeQuotes)
            .trim()
            .takeIf { it.isNotBlank() }
    }

    private fun nameScore(tokens: List<OcrToken>): Float = tokens.sumOf { token ->
        token.text.count(Char::isLetter) * token.confidence.toDouble()
    }.toFloat()

    private fun hasQuotes(tokens: List<OcrToken>): Boolean =
        tokens.any { it.text.contains('"') || it.text.contains('«') || it.text.contains('»') }

    private fun asWeight(token: OcrToken): OcrToken? {
        val text = token.text.replace('\u00A0', ' ')
        val match = weightRegex.find(text) ?: return null
        val range = match.groupValues[1]
        if ('-' in range || '–' in range || '—' in range) {
            // Запятая — допустимый разделитель дробной части; парсер нормализует её позже.
            val parts = range.split('-', '–', '—').mapNotNull { it.trim().replace(',', '.').toDoubleOrNull() }
            if (parts.size != 2 || parts[0] >= parts[1]) return null
        }
        return token
    }

    private fun normalizeWeight(weight: MatchResult): String {
        val amount = weight.groupValues[1].replace(rangeDashRegex, "-")
        val unit = canonicalUnit(weight.groupValues[2].lowercase())
        return "$amount $unit"
    }

    private fun canonicalUnit(unit: String): String = when (unit) {
        "kg", "кг" -> "кг"
        "g", "r", "г" -> "г"
        "ml", "мл" -> "мл"
        "l", "л" -> "л"
        else -> unit
    }

    private fun isNoise(token: OcrToken): Boolean {
        val value = token.text.trim()
        return dateRegex.matches(value) ||
            longDigitRegex.matches(value) ||
            rubleUnitRegex.containsMatchIn(value) ||
            noiseRegexes.any { it.containsMatchIn(value) }
    }

    /** Без якоря нет надёжной привязки к ценнику: сводка строится по порядку строк. */
    private fun fallbackText(tesseract: List<OcrToken>, rapid: List<OcrToken>): String =
        (tesseract + rapid.filter { it.text.matches(digitsRegex) || it.text.matches(unitRegex) })
            .sortedWith(compareBy({ it.box.top }, { it.box.left }))
            .joinToString(" ") { it.text.trim() }
            .let(::normalizeQuotes)

    /** Разные движки и шрифты выдают кавычки четырьмя разными символами. */
    private fun normalizeQuotes(text: String): String = text
        .replace(Regex("""(?<=\p{L})(["“])(?=\p{L})"""), " «")
        .replace(quotedTextRegex) { "«${it.groupValues[2]}»" }
        .replace(Regex("""(^|\s)(["“])(?=\S)"""), "$1«")
        .replace(Regex("""(?<=\S)(["”])(?=\s|$)"""), "»")

    /**
     * RapidOCR на bundled-модели возвращает кириллицу латиницей и единицы в латинской
     * раскладке. Таблица откалибрована на 13 эталонных JPEG; русскоязычный вывод Tesseract
     * она не трогает.
     */
    private fun normalizeRapidOcr(raw: String): String {
        var text = raw
        phrases.forEach { (from, to) ->
            text = text.replace(from, to, ignoreCase = true)
        }
        text = text.map { letterMap[it] ?: it }.joinToString("")
        return text.replace(trailingRublesRegex) { it.groupValues[1] }
    }

    private companion object {
        const val MIN_RUBLES_CONFIDENCE = 0.7f
        // Трёхзначные рубли однозначнее двузначных: на реальном JPEG 119 распознан с 0.695.
        const val MIN_THREE_DIGIT_CONFIDENCE = 0.65f
        const val MIN_TAG_HEIGHT_RATIO = 0.5f
        const val MIN_RUBLES_HEIGHT_RATIO = 0.6f
        const val MIN_NAME_CONFIDENCE = 0.6f
        const val MIN_TESS_WORDS = 3
        const val MIN_TESS_SCORE = 12f
        const val KOPECK_OVERLAP = 0.5f
        const val KOPECK_RIGHT_BAND = 0.8f
        const val VERTICAL_BAND = 0.35f
        const val HORIZONTAL_BAND = 0.6f
        const val NAME_ABOVE = 0.6f
        const val NAME_ABOVE_PX = 180f
        const val NAME_BAND = 0.25f
        const val WEIGHT_BELOW = 0.6f
        const val WEIGHT_BELOW_PX = 40f
        const val SAME_TAG_DISTANCE = 6f
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
        val rubleUnitRegex = Regex("""(?iu)(?<![\p{L}\d])(?:руб|py6|шт|wt|ut)(?![\p{L}\d])(?:\s*[./]\s*\S+)?""")
        val quotedTextRegex = Regex("""([«"“])([^"»“”]+)([»"”])""")
        val trailingDigitsRegex = Regex("""[,\s]*\d+[A-Za-zА-Яа-я]?$""")
        val cardLabelRegex = Regex("""(?iu)цена[ \t]+по[ \t]+карте|по[ \t]+карте""")
        val trailingRublesRegex = Regex("""(\d{2,3})\s*[PpРр₽](?!\p{L})""")

        val phrases = listOf(
            "py6" to "руб",
            "Bec:" to "вес:",
            "Beal" to "вес",
            "Beci" to "вес",
            "LeHa nO KAPTe" to "цена по карте",
            "noKapTe" to "по карте",
            "CYEP3AK" to "СУПЕРЗЛАК",
            "NEYEHbE" to "ПЕЧЕНЬЕ",
            "OBCAHOEC" to "ОВСЯНОЕ С",
            "OBCAHOE" to "ОВСЯНОЕ",
            "YEPHOCAMBOM" to "ЧЕРНОСЛИВОМ",
            "AHaHac" to "Ананас",
            "\"MMEWPHOE\"" to "\"ИМБИРНОЕ\"",
            "3AT9KHOE" to "ЗАТЯЖНОЕ",
            "BAHWbHbIM" to "ВАНИЛЬНЫЙ",
            "BaHHJbHbIY" to "ВАНИЛЬНЫЙ",
            "3EΦWPCNMBOBbIM" to "ЗЕФИР СЛИВОВЫЙ",
            "3EΦP" to "ЗЕФИР",
            "3EФP" to "ЗЕФИР",
            "KNIOKBOM" to "КЛЮКВОЙ,",
            "EPYCHMKOMM" to "БРУСНИКОЙ И",
            "CEMEHAMM" to "СЕМЕНАМИ",
            "TblKBbl\"_Pn" to "ТЫКВЫ\"",
            "CTakaH" to "стакан",
            "kr" to "кг",
            "ut" to "шт",
            "wt" to "шт",
            "k/6" to "кг",
        )

        val letterMap = mapOf(
            'A' to 'А', 'B' to 'В', 'C' to 'С', 'E' to 'Е', 'H' to 'Н', 'K' to 'К',
            'M' to 'М', 'O' to 'О', 'P' to 'Р', 'T' to 'Т', 'W' to 'Ш', 'X' to 'Х',
            'Y' to 'У', 'Φ' to 'Ф', 'Ф' to 'Ф',
            'a' to 'а', 'b' to 'ь', 'c' to 'с', 'e' to 'е', 'h' to 'н', 'i' to 'и',
            'k' to 'к', 'm' to 'м', 'n' to 'п', 'o' to 'о', 'p' to 'р', 'r' to 'г',
            's' to 'с', 't' to 'т', 'w' to 'ш', 'x' to 'х', 'y' to 'у',
        )

        val noiseWords = setOf(
            "цена", "вес", "объём", "объем", "акция", "скидка", "стакан",
            "балтийская", "шпрот", "неразделанная", "срок", "годности",
            "томатном", "соусе", "масса нетто", "б/п", "филе", "тунца",
            "полосатого", "мороженого", "сырья",
        )
        // RapidOCR читает подписи «постное/остное» прямо над ценой; это не часть названия.
        val noiseRegexes = noiseWords.map { Regex("""(?iu)\b${Regex.escape(it)}\b""") } +
            listOf(Regex("""(?iu)^.?постное$"""), Regex("""(?iu)^.?остно(?:е)?$"""))
    }
}
