package fan.superai.v13.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface V13Dao {
    // ---- FanRecord
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun upsertRecords(list: List<FanRecordEntity>)
    @Query("SELECT COUNT(*) FROM FanRecord") fun recordCount(): Int
    @Query("SELECT * FROM FanRecord ORDER BY recordId") fun allRecords(): List<FanRecordEntity>
    @Query("DELETE FROM FanRecord WHERE recordId > :id") fun deleteRecordsAfter(id: Long)
    @Query("DELETE FROM FanRecord") fun clearRecords()

    // ---- PredictionRecord
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun upsertPrediction(p: PredictionRecordEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun upsertPredictions(list: List<PredictionRecordEntity>)
    @Query("SELECT * FROM PredictionRecord WHERE mode = :mode AND sequence = :seq LIMIT 1") fun predictionAt(mode: String, seq: Int): PredictionRecordEntity?
    @Query("SELECT * FROM PredictionRecord WHERE mode = :mode ORDER BY sequence DESC LIMIT :limit OFFSET :offset")
    fun predictionPage(mode: String, limit: Int, offset: Int): List<PredictionRecordEntity>
    @Query("SELECT * FROM PredictionRecord WHERE mode = 'LIVE' OR sequence NOT IN (SELECT sequence FROM PredictionRecord WHERE mode = 'LIVE') " +
        "ORDER BY sequence DESC, id DESC LIMIT :limit OFFSET :offset")
    fun predictionPageAll(limit: Int, offset: Int): List<PredictionRecordEntity>
    @Query("SELECT COUNT(*) FROM PredictionRecord WHERE mode = 'LIVE' OR sequence NOT IN (SELECT sequence FROM PredictionRecord WHERE mode = 'LIVE')")
    fun predictionCountAll(): Int
    @Query("SELECT COUNT(*) FROM PredictionRecord WHERE mode = :mode") fun predictionCount(mode: String): Int
    @Query("DELETE FROM PredictionRecord WHERE mode = :mode AND sequence > :seq") fun deletePredictionsAfter(mode: String, seq: Int)
    @Query("DELETE FROM PredictionRecord WHERE mode = :mode") fun clearPredictions(mode: String)
    @Query("UPDATE PredictionRecord SET actualRecordId = NULL, actualNumber = NULL, actualTimestamp = NULL, numberHit = NULL, top2Hit = NULL, " +
        "bigSmallHit = NULL, oddEvenHit = NULL, sideHit = NULL, numberLogLoss = NULL, sideLogLoss = NULL WHERE mode = :mode AND sequence = :seq")
    fun clearActual(mode: String, seq: Int)
    @Query("SELECT COUNT(*) FROM PredictionRecord WHERE mode = :mode AND actualNumber IS NOT NULL") fun evaluatedCount(mode: String): Int

    // ---- PredictionExplanation
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun upsertExplanation(e: PredictionExplanationEntity)
    @Query("SELECT * FROM PredictionExplanation WHERE sequence = :seq LIMIT 1") fun explanationAt(seq: Int): PredictionExplanationEntity?
    @Query("DELETE FROM PredictionExplanation WHERE sequence > :seq") fun deleteExplanationsAfter(seq: Int)
    @Query("DELETE FROM PredictionExplanation") fun clearExplanations()

    // ---- ModelState (+ undo anlık görüntüleri)
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun upsertModelStates(list: List<ModelStateEntity>)
    @Query("SELECT * FROM ModelState") fun modelStates(): List<ModelStateEntity>
    @Query("SELECT * FROM ModelState WHERE stateKey = :key LIMIT 1") fun modelState(key: String): ModelStateEntity?
    @Query("DELETE FROM ModelState WHERE stateKey LIKE 'undo.%'") fun clearUndo()
    @Query("DELETE FROM ModelState WHERE stateKey = :key") fun deleteModelState(key: String)
    @Query("DELETE FROM ModelState WHERE stateKey LIKE 'undo.%' AND recordCount >= :n") fun deleteUndoFrom(n: Int)
    @Query("DELETE FROM ModelState WHERE stateKey LIKE 'undo.%' AND recordCount < :n") fun deleteUndoBefore(n: Int)
    @Query("DELETE FROM ModelState") fun clearModelState()

    // ---- ModelPerformance
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun upsertPerformance(list: List<ModelPerformanceEntity>)
    @Query("SELECT * FROM ModelPerformance") fun performance(): List<ModelPerformanceEntity>
    @Query("DELETE FROM ModelPerformance") fun clearPerformance()

    // ---- RegimeState / CalibrationState / EnsembleState
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun upsertRegime(list: List<RegimeStateEntity>)
    @Query("SELECT * FROM RegimeState") fun regimeStates(): List<RegimeStateEntity>
    @Query("DELETE FROM RegimeState") fun clearRegime()
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun upsertCalibration(list: List<CalibrationStateEntity>)
    @Query("SELECT * FROM CalibrationState") fun calibrationStates(): List<CalibrationStateEntity>
    @Query("DELETE FROM CalibrationState") fun clearCalibration()
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun upsertEnsemble(list: List<EnsembleStateEntity>)
    @Query("SELECT * FROM EnsembleState") fun ensembleStates(): List<EnsembleStateEntity>
    @Query("DELETE FROM EnsembleState") fun clearEnsemble()

    // ---- ReplayState
    @Insert fun insertReplay(r: ReplayStateEntity): Long
    @Query("SELECT * FROM ReplayState ORDER BY id DESC LIMIT :limit") fun replays(limit: Int): List<ReplayStateEntity>
    @Query("DELETE FROM ReplayState") fun clearReplays()
}
