package fan.lightningroulette.core

/** Bellek içi Store: Room ile aynı sözleşme (unique refCount, tek transaction, geri alma). */
class MemStore : Store {
    val spinList = ArrayList<Spin>(); var nextSpin = 1L
    val preds = LinkedHashMap<Int, StoredPrediction>(); var nextPred = 1L
    val evals = LinkedHashMap<Long, Eval>()
    val states = LinkedHashMap<String, String>()
    val logs = ArrayList<Triple<String, String?, String>>()
    var failTx = false

    override fun spins(): List<Spin> = spinList.toList()
    override fun addSpin(value: Int, ts: Long, source: String, tsType: String): Spin { val s = Spin(nextSpin++, value, ts, source, tsType); spinList.add(s); return s }
    override fun deleteLastSpin(): Spin? = if (spinList.isEmpty()) null else spinList.removeAt(spinList.size - 1)
    override fun prediction(refCount: Int): StoredPrediction? = preds[refCount]
    override fun savePrediction(refCount: Int, createdAt: Long, json: String, pyStatus: String, modelVersion: String): StoredPrediction? {
        if (preds.containsKey(refCount)) return null
        val p = StoredPrediction(nextPred++, refCount, createdAt, json, pyStatus, modelVersion); preds[refCount] = p; return p
    }
    override fun deletePredictionsAfter(refCount: Int) { preds.keys.filter { it > refCount }.forEach { preds.remove(it) } }
    override fun saveEvaluation(predictionId: Long, spinId: Long, ev: Eval) { evals[spinId] = ev }
    override fun deleteEvaluationFor(spinId: Long) { evals.remove(spinId) }
    override fun getState(key: String): String? = states[key]
    override fun putState(key: String, value: String) { states[key] = value }
    override fun deleteState(key: String) { states.remove(key) }
    override fun log(level: String, code: String?, event: String, detail: String) { logs.add(Triple(level, code, event)) }
    override fun <T> tx(block: () -> T): T {
        val s1 = spinList.toList(); val n1 = nextSpin; val p1 = LinkedHashMap(preds); val e1 = LinkedHashMap(evals); val st1 = LinkedHashMap(states)
        try {
            val r = block()
            if (failTx) { failTx = false; throw IllegalStateException("simüle edilmiş DB hatası") }
            return r
        } catch (e: Exception) {
            spinList.clear(); spinList.addAll(s1); nextSpin = n1; preds.clear(); preds.putAll(p1); evals.clear(); evals.putAll(e1); states.clear(); states.putAll(st1)
            throw e
        }
    }
    fun seed(values: List<Int>, t0: Long = 1_700_000_000_000L) { values.forEachIndexed { i, v -> addSpin(v, t0 + i * 30_000L, "IMPORT", "REAL") } }
    fun hasLog(code: String) = logs.any { it.second == code }
}

