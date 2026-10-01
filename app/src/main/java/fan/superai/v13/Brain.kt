package fan.superai.v13

import java.util.zip.CRC32
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max

/** Leakage guard sayaçları: ihlaller sayılır, açık hata kodlarıyla kaydedilir. */
class LeakageGuard {
    var checks = 0L; private set
    var violations = 0L; private set
    var warnings = 0L; private set
    private val messages = ArrayDeque<String>()

    fun check(ok: Boolean, what: () -> String) {
        checks++
        if (!ok) {
            violations++
            val m = "${ErrorCodes.LEAK_FUTURE} ${what()}"
            messages.addLast(m); while (messages.size > 20) messages.removeFirst()
            FanLog.event(FanLog.LEAKAGE_CHECK, m)
        }
    }

    fun warn(ok: Boolean, what: () -> String) {
        if (!ok) { warnings++; messages.addLast("WARN " + what()); while (messages.size > 20) messages.removeFirst() }
    }

    fun recent(): List<String> = messages.toList()
    fun write(o: Out) { o.l(checks); o.l(violations); o.l(warnings) }
    fun read(i: In) { checks = i.l(); violations = i.l(); warnings = i.l() }
}

/** Tahmin kilidi: değişmez tahmin + tahmin anı bağlamı. Gerçek sonuç bunu DEĞİŞTİREMEZ. */
class OpenLock(val prediction: FinalPrediction, val ctx: PredictionContext) {
    val sequence: Int get() = prediction.predictionSequence
}

class BrainStats {
    var total = 0
    var numberHits = 0; var top2Hits = 0; var bsHits = 0; var oeHits = 0; var sideHits = 0
    var numberLL = 0.0; var sideLL = 0.0
    var baseNumber = 0; var baseBs = 0; var baseOe = 0; var baseSide = 0     // nedensel çoğunluk baseline'ı
    val rollNumber = RollBits(100)
    val rollSide = RollBits(100)
    var blendProduct = 0.5

    fun write(o: Out) {
        o.i(total); o.i(numberHits); o.i(top2Hits); o.i(bsHits); o.i(oeHits); o.i(sideHits)
        o.d(numberLL); o.d(sideLL); o.i(baseNumber); o.i(baseBs); o.i(baseOe); o.i(baseSide)
        rollNumber.write(o); rollSide.write(o); o.d(blendProduct)
    }

    fun read(i: In) {
        total = i.i(); numberHits = i.i(); top2Hits = i.i(); bsHits = i.i(); oeHits = i.i(); sideHits = i.i()
        numberLL = i.d(); sideLL = i.d(); baseNumber = i.i(); baseBs = i.i(); baseOe = i.i(); baseSide = i.i()
        rollNumber.read(i); rollSide.read(i); blendProduct = i.d()
    }
}

/** Kalıcı saklama için bir durum parçası. [table]: Room tablosu, [key]: satır anahtarı. */
class StatePart(val table: String, val key: String, val bytes: ByteArray, val perf: PerfRow? = null) {
    val full: String get() = "$table/$key"
}

class PerfRow(
    val axis: String, val modelId: String, val name: String, val group: String, val n: Int,
    val top1: Int, val top2: Int, val logLoss: Double, val brier: Double, val entropy: Double,
    val recent: Double, val calGap: Double, val weight: Double, val gain: Double
)

/** Karar kuralı: tüm eksen dağılımlarından TEK rakam + TEK yan (BS + OE) seçer. */
class Decision(
    val numberIdx: Int, val bs: Int, val oe: Int,
    val joint: DoubleArray, val marginalBs: DoubleArray, val marginalOe: DoubleArray
) {
    companion object {
        /** fin: eksen başına (temperature uygulanmış) dağılımlar; wp: ürün uzmanının ağırlığı. */
        fun decide(fin: Array<DoubleArray>, wp: Double): Decision {
            val pb = fin[Axis.BS.ordinal]; val po = fin[Axis.OE.ordinal]; val pc = fin[Axis.COMB.ordinal]
            val joint = DoubleArray(4)
            for (b in 0..1) for (o in 0..1) {
                val prod = max(pb[b] * po[o], Mx.FLOOR)
                joint[b * 2 + o] = exp(wp * ln(prod) + (1 - wp) * ln(max(pc[b * 2 + o], Mx.FLOOR)))
            }
            Mx.normalize(joint, 1e-6)
            val mb = doubleArrayOf(joint[0] + joint[1], joint[2] + joint[3])
            val mo = doubleArrayOf(joint[0] + joint[2], joint[1] + joint[3])
            return Decision(Mx.argmax(fin[Axis.NUMBER.ordinal]), Mx.argmax(mb), Mx.argmax(mo), joint, mb, mo)
        }
    }
}

/**
 * FAN SUPER v1.3 beyni. Saf Kotlin: Android ve veritabanından bağımsızdır (kalıcılık [exportParts] /
 * [importParts] ile yapılır).
 *
 * Akış: predict() → (tahmin LOCK) → onActual() → değerlendir → online öğren → rejim/kalibrasyon/ağırlık → sonraki predict().
 */
class FanBrain(val alphabet: Alphabet = Alphabet()) {
    val mem = SequenceMemory(alphabet)
    val regime = RegimeEngine()
    val side = KotlinSideEngine(alphabet)
    val ens: Array<AxisEnsemble> = Array(4) { AxisEnsemble(Axis.values()[it], alphabet.k(Axis.values()[it])) }
    val stats = BrainStats()
    val guard = LeakageGuard()
    var open: OpenLock? = null; private set
    var lastEvaluation: EvaluationResult? = null; private set
    private val recentEvals = ArrayDeque<EvaluationResult>()
    private val undoRing = ArrayDeque<Pair<Int, ByteArray>>()
    var mode: Mode = Mode.LIVE
    /** Replay/sandbox sırasında undo halkası tutulmaz (gereksiz CPU/RAM). */
    var recordUndo = true

    companion object { const val UNDO_DEPTH = 64 }

    val count: Int get() = mem.n
    fun recentEvaluations(): List<EvaluationResult> = recentEvals.toList()
    fun currentPrediction(): FinalPrediction? = open?.prediction

    // ------------------------------------------------------------------ tahmin + LOCK
    /**
     * Mevcut bilinen veriyle TEK nihai tahmin üretir ve KİLİTLER. Kilit açıkken tekrar çağrılırsa aynı
     * tahmin döner (aynı tahmini yeniden üretmeyiz). [inputs]: Kotlin/Python rakam modelleri ve Python yan modelleri.
     */
    fun predict(inputs: List<ModelOutput>, nowSeconds: Long): FinalPrediction {
        open?.let {
            if (it.sequence == mem.n) return it.prediction
            FanLog.event(FanLog.ERROR, "${ErrorCodes.LOCK_STALE} kilit #${it.sequence} ≠ kayıt sayısı ${mem.n}; atıldı")
            open = null
        }
        val all = (inputs + side.outputs(mem, regime.current)).sortedBy { it.id }
        val axes = Array(4) { buildAxis(Axis.values()[it], all) }
        val ctx = PredictionContext(axes, regime.current, stats.blendProduct)
        val pred = compose(ctx, mem.n, mem.lastRecordId, nowSeconds, null)
        open = OpenLock(pred, ctx)
        FanLog.event(FanLog.PREDICTION_CREATED, "#${pred.predictionSequence} → ${pred.number} · ${pred.sideDisplay}")
        FanLog.event(FanLog.PREDICTION_LOCKED, pred.predictionLockId)
        return pred
    }

    private fun buildAxis(axis: Axis, all: List<ModelOutput>): AxisInput {
        val e = ens[axis.ordinal]
        val used = all.filter { it.dist(axis) != null }
        used.forEach { e.register(it.id, it.name, it.group) }
        val ids = used.map { it.id }
        return AxisInput(
            ids.toTypedArray(), used.map { it.name }.toTypedArray(), used.map { it.group }.toTypedArray(),
            Array(used.size) { Mx.normalize(used[it].dist(axis)!!.copyOf()) }, e.weightsFor(ids), e.tau
        )
    }

    private fun axisMix(ctx: PredictionContext, axis: Axis): DoubleArray {
        val a = ctx.axis(axis); val k = alphabet.k(axis)
        return if (a.size == 0) Mx.uniform(k) else Pool.mix(k, a.dists, a.weights)
    }

    /** Bağlamdan nihai tahmini deterministik üretir (kilit geri yüklenirken de aynı kod kullanılır). */
    private fun compose(ctx: PredictionContext, seq: Int, lastKnownId: Long, ts: Long, lockId: String?): FinalPrediction {
        val mixes = Array(4) { axisMix(ctx, Axis.values()[it]) }
        val fin = Array(4) { Pool.shape(alphabet.k(Axis.values()[it]), mixes[it], ctx.axis(Axis.values()[it]).tau) }
        val d = Decision.decide(fin, ctx.blendProduct)
        val number = alphabet.number(d.numberIdx)
        val classes = IntArray(alphabet.size) { alphabet.number(it) }
        val numDist = PredictionDistribution.of("final", classes, fin[0], ts)
        val calN = ens[Axis.NUMBER.ordinal].finalTracker.cal
        val conf = calN.calibrate(fin[0][d.numberIdx])
        val calB = ens[Axis.BS.ordinal].finalTracker.cal; val calO = ens[Axis.OE.ordinal].finalTracker.cal
        val sideConf = 0.5 * (calB.calibrate(d.marginalBs[d.bs]) + calO.calibrate(d.marginalOe[d.oe]))
        val bsE = BigSmall.fromIndex(d.bs); val oeE = OddEven.fromIndex(d.oe)
        val combined = CombinedSide.of(bsE, oeE)
        val sideDist = SideDistribution("final", d.marginalBs, d.marginalOe, d.joint,
            sideConf, Mx.entropy(d.joint), ts)
        val chosen = intArrayOf(d.numberIdx, d.bs, d.oe, combined.index)
        val contributions = ArrayList<ModelContribution>()
        for (ax in Axis.values()) {
            val a = ctx.axis(ax); val c = chosen[ax.ordinal]; val mx = mixes[ax.ordinal]
            for (i in 0 until a.size) {
                val pf = a.dists[i][c]
                contributions.add(ModelContribution(ax, a.ids[i], a.names[i], a.groups[i], a.weights[i],
                    Mx.argmax(a.dists[i]), pf, if (mx[c] > 0) a.weights[i] * pf / mx[c] else 0.0))
            }
        }
        val crc = CRC32()
        fun feed(v: Long) { for (b in 0 until 8) crc.update(((v shr (8 * b)) and 0xFF).toInt()) }
        feed(seq.toLong()); feed(lastKnownId); feed(number.toLong()); feed(combined.index.toLong())
        for (x in fin[0]) feed((x * 1e6).toLong())
        for (x in d.joint) feed((x * 1e6).toLong())
        val hash = crc.value
        val id = lockId ?: ("L$seq-" + java.lang.Long.toHexString(hash))
        val expl = Explainer(this, ctx, fin, d, mixes, combined, contributions).build()
        return FinalPrediction(
            number, numDist, bsE, oeE, combined.name, sideDist, conf, sideConf, Mx.entropy(fin[0]),
            V13.REGIME_NAMES[ctx.regime], V13.ENSEMBLE_VERSION, ts, id, seq, lastKnownId, hash, contributions, expl
        )
    }

    /** Karşı-olgusal analiz için: şu anki kilidin bağlamı (varsa). */
    fun currentContext(): PredictionContext? = open?.ctx

    // ------------------------------------------------------------------ gerçek sonuç + online öğrenme
    /** Gerçek sonuç geldi. Kilitli tahmin değerlendirilir, sonra TÜM durum güncellenir. */
    fun onActual(rec: FanRecord): EvaluationResult {
        if (!alphabet.valid(rec.number)) throw FanException(ErrorCodes.VALIDATE, "geçersiz rakam ${rec.number}")
        FanLog.event(FanLog.NEW_RECORD, "#${rec.recordId} = ${rec.number}")
        if (recordUndo) pushUndo()
        val lock = open
        val actual = IntArray(4) { alphabet.actual(Axis.values()[it], rec.number) }
        var ev: EvaluationResult
        if (lock != null && lock.sequence == mem.n) {
            val p = lock.prediction
            guard.check(p.lastKnownRecordId == mem.lastRecordId) { "kilit #${p.predictionSequence} bilinen kayıt kimliği uyuşmuyor" }
            guard.warn(p.predictionTimestamp <= rec.timestamp) { "tahmin zamanı gerçek sonuçtan sonra (#${rec.recordId})" }
            ev = evaluate(lock, rec, actual)
            FanLog.event(FanLog.ACTUAL_RESULT_RECEIVED, "#${rec.recordId}: tahmin ${p.number} → gerçek ${rec.number} " +
                (if (ev.numberHit) "✓" else "✗") + " · yan " + (if (ev.sideHit) "✓" else "✗"))
        } else {
            if (lock != null) FanLog.event(FanLog.ERROR, "${ErrorCodes.SEQUENCE} kilit sırası ${lock.sequence} ≠ ${mem.n}")
            ev = EvaluationResult(mem.n, rec.recordId, rec.timestamp, rec.number, -1, CombinedSide.SMALL_EVEN,
                false, false, false, false, false, 0.0, 0.0, regime.name(), "", false)
        }
        // Yan modeller bağlam hafıza güncellenmeden ÖNCE öğrenir
        side.learn(mem, regime.current, actual[1], actual[2], actual[3])
        mem.append(rec)
        val changed = regime.update(mem)
        if (changed) FanLog.event(FanLog.REGIME_CHANGED, "→ ${regime.name()} (#${mem.n})")
        for (e in ens) e.updateWeights(regime.current)
        FanLog.event(FanLog.ENSEMBLE_UPDATED, "ağırlıklar yumuşatıldı")
        open = null
        lastEvaluation = ev
        recentEvals.addLast(ev); while (recentEvals.size > 300) recentEvals.removeFirst()
        return ev
    }

    private fun evaluate(lock: OpenLock, rec: FanRecord, actual: IntArray): EvaluationResult {
        val p = lock.prediction; val ctx = lock.ctx
        // Taban modeller, ensemble izleyicileri, kalibrasyon, çeşitlilik
        for (ax in Axis.values()) ens[ax.ordinal].observe(ctx.axis(ax), actual[ax.ordinal], ctx.regime)
        FanLog.event(FanLog.MODEL_UPDATED, "izleyiciler güncellendi")
        FanLog.event(FanLog.CALIBRATION_UPDATED, "kalibrasyon+sıcaklık güncellendi")
        val numHit = alphabet.number(Mx.argmax(p.numberDistribution.probabilities)) == rec.number
        val t2 = Mx.top2(p.numberDistribution.probabilities)
        val top2Hit = alphabet.number(t2[0]) == rec.number || alphabet.number(t2[1]) == rec.number
        val bsHit = p.bigSmall.index == actual[1]; val oeHit = p.oddEven.index == actual[2]
        val sideHit = bsHit && oeHit
        val numLL = Mx.logLoss(p.numberDistribution.probabilities, actual[0])
        val sideLL = Mx.logLoss(p.sideDistribution.combined, actual[3])
        // Yan karışımı (ürün vs doğrudan birleşik) — Hedge, sabit paylaşımlı
        val ctxFin = Array(4) { Pool.shape(alphabet.k(Axis.values()[it]), axisMix(ctx, Axis.values()[it]), ctx.axis(Axis.values()[it]).tau) }
        val pb = ctxFin[1]; val po = ctxFin[2]; val pc = ctxFin[3]
        val lp = -ln(max(pb[actual[1]] * po[actual[2]], Mx.FLOOR))
        val lc = -ln(max(pc[actual[3]], Mx.FLOOR))
        val a = ctx.blendProduct * exp(-0.3 * lp); val b = (1 - ctx.blendProduct) * exp(-0.3 * lc)
        stats.blendProduct = Mx.clamp(a / (a + b), 0.1, 0.9)
        // İstatistikler (+ nedensel çoğunluk baseline'ı: sonuç öğrenilmeden önceki frekanslar)
        stats.total++
        if (numHit) stats.numberHits++; if (top2Hit) stats.top2Hits++
        if (bsHit) stats.bsHits++; if (oeHit) stats.oeHits++; if (sideHit) stats.sideHits++
        stats.numberLL += numLL; stats.sideLL += sideLL
        stats.rollNumber.add(numHit); stats.rollSide.add(sideHit)
        if (Mx.argmax(mem.counts(Axis.NUMBER)) == actual[0]) stats.baseNumber++
        if (Mx.argmax(mem.counts(Axis.BS)) == actual[1]) stats.baseBs++
        if (Mx.argmax(mem.counts(Axis.OE)) == actual[2]) stats.baseOe++
        if (Mx.argmax(mem.counts(Axis.COMB)) == actual[3]) stats.baseSide++
        regime.record(ctx.regime, numHit, sideHit, top2Hit)
        return EvaluationResult(p.predictionSequence, rec.recordId, rec.timestamp, rec.number, p.number, p.side,
            numHit, top2Hit, bsHit, oeHit, sideHit, numLL, sideLL, V13.REGIME_NAMES[ctx.regime], p.predictionLockId, true)
    }

    // ------------------------------------------------------------------ geri alma (DEL)
    private fun pushUndo() {
        undoRing.addLast(mem.n to snapshot())
        while (undoRing.size > UNDO_DEPTH) undoRing.removeFirst()
    }

    val undoDepth: Int get() = undoRing.size

    /** Durumu [count] kayıt öğrenilmiş haline (o kayda ait KİLİTLİ tahminle) döndürür. */
    fun undoTo(count: Int): Boolean {
        while (undoRing.isNotEmpty() && undoRing.last().first > count) undoRing.removeLast()
        val s = undoRing.lastOrNull()?.takeIf { it.first == count } ?: return false
        return try {
            restore(s.second)
            undoRing.removeLast()
            recentEvals.clear()
            true
        } catch (e: Exception) { FanLog.event(FanLog.ERROR, "${ErrorCodes.STATE_LOAD} undo: ${e.message}"); false }
    }

    // ------------------------------------------------------------------ durum (kalıcılık)
    private inline fun bytes(f: (Out) -> Unit): ByteArray { val o = Out(); f(o); return o.toBytes() }

    fun exportParts(): List<StatePart> {
        val out = ArrayList<StatePart>()
        out += StatePart("model_state", "memory", bytes { mem.write(it) })
        out += StatePart("model_state", "side", bytes { side.write(it) })
        out += StatePart("model_state", "stats", bytes { stats.write(it); guard.write(it) })
        out += StatePart("regime_state", "live", bytes { regime.write(it) })
        for (ax in Axis.values()) {
            val e = ens[ax.ordinal]
            out += StatePart("ensemble_state", ax.name, bytes { e.writeEnsemble(it) })
            out += StatePart("calibration_state", ax.name, bytes { e.writeFinal(it) })
            for ((id, t) in e.trackers) {
                out += StatePart("model_performance", "${ax.name}:$id", bytes { e.writeTracker(id, it) },
                    PerfRow(ax.name, id, e.name(id), e.group(id).name, t.n, t.top1, t.top2, t.logLoss, t.brier,
                        t.entropy, t.recent, t.cal.gap(), e.weight(id), 0.5 * t.ewmaLong + 0.5 * t.ewmaShort))
            }
        }
        out += StatePart("model_state", "lock", bytes { writeLock(it) })
        return out
    }

    /** Bütün parçaları yükler. Eksik/bozuk parça → FAN-E-STATE-001. Önce sıfırlama yapılmaz: yeni brain'e yükleyin. */
    fun importParts(parts: Map<String, ByteArray>) {
        fun need(k: String): ByteArray = parts[k] ?: throw FanException(ErrorCodes.STATE_LOAD, "eksik parça $k")
        mem.read(In(need("model_state/memory")))
        side.read(In(need("model_state/side")))
        run { val i = In(need("model_state/stats")); stats.read(i); guard.read(i) }
        regime.read(In(need("regime_state/live")))
        for (ax in Axis.values()) {
            val e = ens[ax.ordinal]
            e.readEnsemble(In(need("ensemble_state/${ax.name}")))
            e.readFinal(In(need("calibration_state/${ax.name}")))
            for (id in e.trackers.keys.toList()) e.readTracker(id, In(need("model_performance/${ax.name}:$id")))
        }
        open = null
        val lockBytes = need("model_state/lock")
        readLock(In(lockBytes))
    }

    private fun writeLock(o: Out) {
        val l = open
        o.b(l != null)
        if (l == null) return
        val p = l.prediction; val ctx = l.ctx
        for (ax in Axis.values()) {
            val a = ctx.axis(ax)
            o.i(a.size)
            for (i in 0 until a.size) { o.s(a.ids[i]); o.s(a.names[i]); o.i(a.groups[i].ordinal); o.da(a.dists[i]) }
            o.da(a.weights); o.d(a.tau)
        }
        o.i(ctx.regime); o.d(ctx.blendProduct)
        o.i(p.predictionSequence); o.l(p.lastKnownRecordId); o.l(p.predictionTimestamp); o.s(p.predictionLockId); o.l(p.lockHash)
    }

    private fun readLock(i: In) {
        if (!i.b()) { open = null; return }
        val axes = Array(4) {
            val n = i.i()
            val ids = Array(n) { "" }; val names = Array(n) { "" }; val groups = Array(n) { Group.KOTLIN }
            val dists = Array(n) { DoubleArray(0) }
            for (j in 0 until n) { ids[j] = i.s(); names[j] = i.s(); groups[j] = Group.values()[i.i()]; dists[j] = i.da() }
            val w = i.da(); val tau = i.d()
            AxisInput(ids, names, groups, dists, w, tau)
        }
        val reg = i.i(); val bp = i.d()
        val seq = i.i(); val lastId = i.l(); val ts = i.l(); val lockId = i.s(); val hash = i.l()
        val ctx = PredictionContext(axes, reg, bp)
        val pred = compose(ctx, seq, lastId, ts, lockId)
        if (pred.lockHash != hash) {
            FanLog.event(FanLog.ERROR, "${ErrorCodes.LOCK_TAMPERED} kilit özeti uyuşmuyor (#$seq); kilit atıldı")
            open = null
        } else open = OpenLock(pred, ctx)
    }

    /** Tüm durumun sıkıştırılmış anlık görüntüsü (undo halkası ve kontrol noktaları için). */
    fun snapshot(): ByteArray {
        val parts = exportParts()
        val o = Out()
        o.i(parts.size)
        for (p in parts) { o.s(p.full); o.bytes(p.bytes) }
        return Gz.pack(o.toBytes())
    }

    fun restore(snap: ByteArray) {
        val i = In(Gz.unpack(snap))
        val n = i.i()
        val m = HashMap<String, ByteArray>()
        repeat(n) { val k = i.s(); m[k] = i.bytes() }
        // Mevcut nesneyi yerinde yeniden kurmak yerine, durumu okuyup yazar: önce ensembles sıfırlanır
        for (e in ens) e.reset()
        importParts(m)
    }
}
