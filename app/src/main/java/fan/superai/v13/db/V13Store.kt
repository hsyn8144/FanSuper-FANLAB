package fan.superai.v13.db

import fan.superai.v13.CombinedSide
import fan.superai.v13.EvaluationResult
import fan.superai.v13.FanLog
import fan.superai.v13.FanRecord
import fan.superai.v13.FinalPrediction
import fan.superai.v13.PredictionExplanation
import fan.superai.v13.StatePart
import java.util.zip.CRC32

/**
 * Kalıcılık katmanı (Room + SQLite). Her kayıtta DB baştan yazılmaz: yalnızca DEĞİŞEN durum parçaları
 * (içerik özeti farklı olanlar) yazılır; tahmin/değerlendirme tek satır güncellemesidir.
 */
class V13Store(private val db: FanDatabase) {
    private val dao = db.dao()
    private val written = HashMap<String, Long>()

    companion object {
        const val LIVE = "LIVE"
        const val REPLAY = "REPLAY"
        const val UNDO_KEEP = 8
        private const val SEP1 = '\u001F'
        private const val SEP2 = '\u001E'

        fun encode(e: PredictionExplanation): String = listOf(e.numberPositive, e.numberNegative, e.sidePositive, e.sideNegative, e.sideDetail)
            .joinToString(SEP2.toString()) { it.joinToString(SEP1.toString()) }

        fun decode(s: String): PredictionExplanation {
            val parts = s.split(SEP2)
            fun l(i: Int): List<String> = parts.getOrNull(i)?.takeIf { it.isNotEmpty() }?.split(SEP1) ?: emptyList()
            return PredictionExplanation(l(0), l(1), l(2), l(3), l(4))
        }

        fun dist(a: DoubleArray): String = a.joinToString(",") { "%.5f".format(java.util.Locale.US, it) }
        fun parseDist(s: String): DoubleArray = if (s.isEmpty()) DoubleArray(0) else s.split(",").map { it.toDouble() }.toDoubleArray()

        fun toEntity(r: FanRecord) = FanRecordEntity(r.recordId, r.timestamp, r.number, r.bigSmall.name, r.oddEven.name, r.combinedSide.name)
        fun fromEntity(e: FanRecordEntity) = FanRecord.of(e.recordId, e.timestamp, e.number)

        fun toEntity(mode: String, p: FinalPrediction) = PredictionRecordEntity(
            mode = mode, sequence = p.predictionSequence, lockId = p.predictionLockId, lockHash = p.lockHash,
            predictionTimestamp = p.predictionTimestamp, lastKnownRecordId = p.lastKnownRecordId,
            predictedNumber = p.number, bigSmall = p.bigSmall.name, oddEven = p.oddEven.name, sideLabel = p.sideLabel,
            confidence = p.confidence, sideConfidence = p.sideConfidence, entropy = p.entropy, regime = p.regime,
            ensembleVersion = "v" + p.ensembleVersion, numberDist = dist(p.numberDistribution.probabilities),
            sideDist = dist(p.sideDistribution.combined)
        )
    }

    private fun crc(b: ByteArray): Long { val c = CRC32(); c.update(b); return c.value }

    // ------------------------------------------------------------------ ham kayıtlar
    fun records(): List<FanRecord> = dao.allRecords().map { fromEntity(it) }
    fun recordCount(): Int = dao.recordCount()

    /** DB'yi [list] ile eşitler (kuyruk ekleme ya da fazlayı silme; farklıysa baştan yazar). */
    fun syncRecords(list: List<FanRecord>) {
        db.runInTransaction {
            val c = dao.recordCount()
            if (c == list.size) return@runInTransaction
            if (c in 1 until list.size) dao.upsertRecords(list.drop(c).map { toEntity(it) })
            else if (c > list.size && list.isNotEmpty()) dao.deleteRecordsAfter(list.last().recordId)
            else { dao.clearRecords(); dao.upsertRecords(list.map { toEntity(it) }) }
        }
    }

    fun replaceRecords(list: List<FanRecord>) = db.runInTransaction { dao.clearRecords(); dao.upsertRecords(list.map { toEntity(it) }) }

    // ------------------------------------------------------------------ model durumu
    /** Tüm durum parçalarını "tablo/anahtar" → bayt olarak yükler (undo anlık görüntüleri hariç). */
    fun loadParts(): Map<String, ByteArray> {
        val m = HashMap<String, ByteArray>()
        for (e in dao.modelStates()) if (!e.stateKey.startsWith("undo.")) m["model_state/${e.stateKey}"] = e.blob
        for (e in dao.regimeStates()) m["regime_state/${e.stateKey}"] = e.blob
        for (e in dao.calibrationStates()) m["calibration_state/${e.stateKey}"] = e.blob
        for (e in dao.ensembleStates()) m["ensemble_state/${e.stateKey}"] = e.blob
        for (e in dao.performance()) m["model_performance/${e.key}"] = e.blob
        return m
    }

    /** Kayıtlı durumun kaç kayda ait olduğu (model_state satırlarından); yoksa -1. */
    fun savedCount(): Int = dao.modelState("memory")?.recordCount ?: -1

    fun saveParts(parts: List<StatePart>, recordCount: Int) {
        val now = System.currentTimeMillis()
        try {
            var changed = 0
            db.runInTransaction {
                val ms = ArrayList<ModelStateEntity>(); val rg = ArrayList<RegimeStateEntity>()
                val cb = ArrayList<CalibrationStateEntity>(); val en = ArrayList<EnsembleStateEntity>()
                val pf = ArrayList<ModelPerformanceEntity>()
                for (p in parts) {
                    val h = crc(p.bytes)
                    // 'memory' her zaman yazılır: recordCount güncel kalsın
                    if (written[p.full] == h && p.full != "model_state/memory") continue
                    changed++
                    when (p.table) {
                        "model_state" -> ms += ModelStateEntity(p.key, p.bytes, now, recordCount)
                        "regime_state" -> rg += RegimeStateEntity(p.key, p.bytes, now)
                        "calibration_state" -> cb += CalibrationStateEntity(p.key, p.bytes, now)
                        "ensemble_state" -> en += EnsembleStateEntity(p.key, p.bytes, now)
                        "model_performance" -> {
                            val r = p.perf
                            if (r != null) pf += ModelPerformanceEntity(p.key, r.axis, r.modelId, r.name, r.group, r.n, r.top1, r.top2,
                                r.logLoss, r.brier, r.entropy, r.recent, r.calGap, r.weight, r.gain, p.bytes, now)
                        }
                    }
                    written[p.full] = h
                }
                if (ms.isNotEmpty()) dao.upsertModelStates(ms)
                if (rg.isNotEmpty()) dao.upsertRegime(rg)
                if (cb.isNotEmpty()) dao.upsertCalibration(cb)
                if (en.isNotEmpty()) dao.upsertEnsemble(en)
                if (pf.isNotEmpty()) dao.upsertPerformance(pf)
            }
            FanLog.event(FanLog.STATE_PERSISTED, "$changed/${parts.size} parça yazıldı (n=$recordCount)")
        } catch (e: Exception) {
            written.clear(); throw e
        }
    }

    fun clearStates() {
        written.clear()
        db.runInTransaction {
            dao.clearModelState(); dao.clearRegime(); dao.clearCalibration(); dao.clearEnsemble(); dao.clearPerformance()
        }
    }

    fun saveUndo(count: Int, snapshot: ByteArray) {
        dao.upsertModelStates(listOf(ModelStateEntity("undo.$count", snapshot, System.currentTimeMillis(), count)))
        dao.deleteUndoBefore(count - UNDO_KEEP + 1)
    }

    fun loadUndo(count: Int): ByteArray? = dao.modelState("undo.$count")?.blob
    fun dropUndoFrom(count: Int) = dao.deleteUndoFrom(count)

    // ------------------------------------------------------------------ tahminler
    fun savePrediction(mode: String, p: FinalPrediction, withExplanation: Boolean) {
        dao.upsertPrediction(toEntity(mode, p).let { e -> dao.predictionAt(mode, p.predictionSequence)?.let { e.copy(id = it.id) } ?: e })
        if (withExplanation) dao.upsertExplanation(PredictionExplanationEntity(p.predictionSequence, p.predictionLockId, encode(p.explanation)))
    }

    fun savePredictionsBatch(mode: String, list: List<PredictionRecordEntity>) {
        db.runInTransaction { dao.clearPredictions(mode); dao.upsertPredictions(list) }
    }

    fun resolve(mode: String, ev: EvaluationResult) {
        if (!ev.evaluated) return
        val cur = dao.predictionAt(mode, ev.sequence) ?: return
        dao.upsertPrediction(cur.copy(
            actualRecordId = ev.recordId, actualNumber = ev.actualNumber, actualTimestamp = ev.actualTimestamp,
            numberHit = ev.numberHit, top2Hit = ev.top2Hit, bigSmallHit = ev.bigSmallHit, oddEvenHit = ev.oddEvenHit,
            sideHit = ev.sideHit, numberLogLoss = ev.numberLogLoss, sideLogLoss = ev.sideLogLoss
        ))
    }

    fun entityFor(mode: String, p: FinalPrediction, ev: EvaluationResult?): PredictionRecordEntity {
        val e = toEntity(mode, p)
        if (ev == null || !ev.evaluated) return e
        return e.copy(actualRecordId = ev.recordId, actualNumber = ev.actualNumber, actualTimestamp = ev.actualTimestamp,
            numberHit = ev.numberHit, top2Hit = ev.top2Hit, bigSmallHit = ev.bigSmallHit, oddEvenHit = ev.oddEvenHit,
            sideHit = ev.sideHit, numberLogLoss = ev.numberLogLoss, sideLogLoss = ev.sideLogLoss)
    }

    /** Geri alma: [count]'tan sonraki tahminleri sil, [count]. tahminin sonucunu sıfırla. */
    fun rollbackTo(count: Int) = db.runInTransaction {
        dao.deletePredictionsAfter(LIVE, count)
        dao.deleteExplanationsAfter(count)
        dao.clearActual(LIVE, count)
        dao.deleteUndoFrom(count)
    }

    fun clearPredictions() = db.runInTransaction { dao.clearPredictions(LIVE); dao.clearPredictions(REPLAY); dao.clearExplanations() }

    fun page(offset: Int, limit: Int): List<PredictionRecordEntity> = dao.predictionPageAll(limit, offset)
    fun predictionTotal(): Int = dao.predictionCountAll()
    fun explanation(seq: Int): PredictionExplanation? = dao.explanationAt(seq)?.let { decode(it.payload) }

    fun saveReplay(r: ReplayStateEntity): Long = dao.insertReplay(r)
    fun replays(limit: Int = 10): List<ReplayStateEntity> = dao.replays(limit)
    fun performance(): List<ModelPerformanceEntity> = dao.performance()
}
