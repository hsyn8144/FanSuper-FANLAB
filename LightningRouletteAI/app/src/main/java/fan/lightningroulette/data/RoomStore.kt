package fan.lightningroulette.data

import fan.lightningroulette.core.Eval
import fan.lightningroulette.core.Json
import fan.lightningroulette.core.Spin
import fan.lightningroulette.core.Store
import fan.lightningroulette.core.StoredPrediction
import java.util.concurrent.Callable

/** [Store] sözleşmesinin Room gerçeklemesi (aktif dataset kapsamında). Tüm çağrılar motor iş parçacığından yapılır. */
class RoomStore(private val db: LrDb, val datasetId: Long, private val now: () -> Long = { System.currentTimeMillis() }) : Store {
    private val dao = db.dao()

    private fun toSpin(e: SpinE) = Spin(e.id, e.value, e.tsMs, e.source, e.tsType)
    private fun toPred(e: PredictionE) = StoredPrediction(e.id, e.refCount, e.createdAt, e.json, e.pyStatus, e.modelVersion)

    override fun spins(): List<Spin> = dao.spins(datasetId).map { toSpin(it) }
    override fun addSpin(value: Int, ts: Long, source: String, tsType: String): Spin {
        val id = dao.insertSpin(SpinE(0, datasetId, value, ts, tsType, source, 0))
        return Spin(id, value, ts, source, tsType)
    }
    override fun deleteLastSpin(): Spin? {
        val last = dao.lastSpin(datasetId) ?: return null
        dao.deleteSpin(last.id)
        return toSpin(last)
    }
    override fun prediction(refCount: Int): StoredPrediction? = dao.prediction(datasetId, refCount)?.let { toPred(it) }
    override fun savePrediction(refCount: Int, createdAt: Long, json: String, pyStatus: String, modelVersion: String): StoredPrediction? {
        val id = dao.insertPrediction(PredictionE(0, datasetId, refCount, createdAt, json, pyStatus, modelVersion))
        return if (id < 0) null else StoredPrediction(id, refCount, createdAt, json, pyStatus, modelVersion)
    }
    override fun deletePredictionsAfter(refCount: Int) { dao.deletePredictionsAfter(datasetId, refCount) }
    override fun saveEvaluation(predictionId: Long, spinId: Long, ev: Eval) {
        dao.insertEvaluation(EvaluationE(0, predictionId, spinId, ev.actual, Json.stringify(ev.toMap()), now(), ev.exact, ev.candidate, ev.tableCount))
    }
    override fun deleteEvaluationFor(spinId: Long) { dao.deleteEvaluationFor(spinId) }

    private fun k(key: String) = "$datasetId:$key"
    override fun getState(key: String): String? = dao.getState(k(key))
    override fun putState(key: String, value: String) { dao.putState(StateE(k(key), value, now())) }
    override fun deleteState(key: String) { dao.deleteState(k(key)) }

    override fun log(level: String, code: String?, event: String, detail: String) {
        try { dao.insertLog(LogE(0, now(), level, code, event, detail.take(2000))) } catch (_: Exception) { /* günlük yazılamazsa akışı bozma */ }
    }

    override fun <T> tx(block: () -> T): T = db.runInTransaction(Callable { block() })
}
