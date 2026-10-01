package fan.superai.overlay

import fan.superai.Echo
import fan.superai.engine.EngineState
import fan.superai.engine.P

/** Yalnızca gösterim verisi: meclisleri/hakemi eğitmez veya değiştirmez. */
internal data class CouncilPrediction(
    val number: String = "--",
    val numberConfidence: String = "--",
    val side: String = "--",
    val sideConfidence: String = "--"
)

/**
 * Eski detay satırındaki gibi meclisin en olası iki rakamı; açıkça "tek" seçilmişse
 * yalnızca ilki. Otomatik/konformal karar ve kalibrasyon hakeme aittir; meclis
 * satırlarına hakemin tahmini veya güveni kopyalanmaz. Buradaki yüzdeler hamdır.
 * Yan: 1/3 = tek, 3/4 = büyük; güven iki yan bileşenin ortalamasıdır.
 */
internal fun councilPrediction(probs: DoubleArray?, single: Boolean = false): CouncilPrediction {
    if (probs == null) return CouncilPrediction()
    val order = P.order(probs)
    val first = order[0]
    val second = order[1]
    val odd = P.oddProb(probs)
    val big = P.bigProb(probs)
    fun pct(p: Double) = "%${(p * 100).toInt()}"
    return CouncilPrediction(
        number = if (single) "${first + 1}" else "${first + 1}/${second + 1}",
        numberConfidence = pct(probs[first] + if (single) 0.0 else probs[second]),
        side = (if (odd >= .5) "T" else "Ç") + "•" + (if (big >= .5) "B" else "K"),
        sideConfidence = pct((maxOf(odd, 1 - odd) + maxOf(big, 1 - big)) / 2)
    )
}

/**
 * En yeni veri solda. Echo'nun bir bölümü motor durumuna geçmiş olabilir:
 * yalnızca henüz işlenmemiş kuyruğu ekleyerek aynı sayıyı iki kere göstermeyiz.
 * Motorun ve uygulamanın diğer ekranlarının kronolojik sırası değiştirilmez.
 */
internal fun overlayRecent(st: EngineState?, echo: Echo): List<Int> {
    val unprocessed = (echo.base + echo.pendingCount - (st?.count ?: 0))
        .coerceIn(0, echo.values.size)
    return ((st?.recent ?: emptyList()) + echo.values.takeLast(unprocessed))
        .takeLast(6).asReversed()
}
