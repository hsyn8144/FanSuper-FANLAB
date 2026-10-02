package fan.lightningroulette.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LrDao {
    // ── datasets
    @Query("SELECT * FROM datasets ORDER BY id") fun datasets(): List<DatasetE>
    @Query("SELECT * FROM datasets ORDER BY id") fun datasetsFlow(): Flow<List<DatasetE>>
    @Query("SELECT * FROM datasets WHERE active = 1 ORDER BY id DESC LIMIT 1") fun activeDataset(): DatasetE?
    @Query("SELECT * FROM datasets WHERE id = :id") fun dataset(id: Long): DatasetE?
    @Insert fun insertDataset(d: DatasetE): Long
    @Update fun updateDataset(d: DatasetE)
    @Query("UPDATE datasets SET active = 0") fun deactivateAll()
    @Query("DELETE FROM datasets WHERE id = :id") fun deleteDataset(id: Long)
    @Query("DELETE FROM dataset_versions WHERE datasetId = :id") fun deleteVersionsOf(id: Long)
    @Query("DELETE FROM import_batches") fun deleteAllBatches()
    @Query("DELETE FROM logs") fun deleteAllLogs()
    @Query("DELETE FROM state_blobs") fun deleteAllStates()
    @Query("DELETE FROM model_versions") fun deleteAllModelVersions()
    @Query("DELETE FROM evaluations") fun deleteAllEvaluations()
    @Query("DELETE FROM predictions") fun deleteAllPredictions()
    @Query("DELETE FROM spins") fun deleteAllSpins()
    @Query("DELETE FROM datasets") fun deleteAllDatasets()
    @Query("DELETE FROM dataset_versions") fun deleteAllVersions()
    @Insert fun insertVersion(v: DatasetVersionE): Long
    @Query("SELECT * FROM dataset_versions WHERE datasetId = :d ORDER BY version DESC") fun versions(d: Long): List<DatasetVersionE>
    @Insert fun insertBatch(b: ImportBatchE): Long
    @Query("SELECT * FROM import_batches ORDER BY id DESC LIMIT :n") fun batches(n: Int): List<ImportBatchE>

    // ── spins
    @Query("SELECT * FROM spins WHERE datasetId = :d ORDER BY id ASC") fun spins(d: Long): List<SpinE>
    @Query("SELECT * FROM spins WHERE datasetId = :d ORDER BY id DESC LIMIT :limit OFFSET :offset") fun spinsPage(d: Long, limit: Int, offset: Int): List<SpinE>
    @Query("SELECT * FROM spins WHERE datasetId = :d AND (value = :v) ORDER BY id DESC LIMIT :limit OFFSET :offset") fun spinsByValue(d: Long, v: Int, limit: Int, offset: Int): List<SpinE>
    @Query("SELECT * FROM spins WHERE datasetId = :d AND source = :src ORDER BY id DESC LIMIT :limit OFFSET :offset") fun spinsBySource(d: Long, src: String, limit: Int, offset: Int): List<SpinE>
    @Query("SELECT COUNT(*) FROM spins WHERE datasetId = :d") fun spinCount(d: Long): Int
    @Query("SELECT COUNT(*) FROM spins WHERE datasetId = :d") fun spinCountFlow(d: Long): Flow<Int>
    @Query("SELECT * FROM spins WHERE datasetId = :d ORDER BY id DESC LIMIT :n") fun lastSpinsFlow(d: Long, n: Int): Flow<List<SpinE>>
    @Query("SELECT * FROM spins WHERE datasetId = :d ORDER BY id DESC LIMIT 1") fun lastSpin(d: Long): SpinE?
    @Query("UPDATE spins SET value = :v WHERE id = :id") fun updateSpinValue(id: Long, v: Int)
    @Query("SELECT COUNT(*) FROM spins WHERE datasetId = :d AND id <= :id") fun spinIndex(d: Long, id: Long): Int
    @Query("SELECT COUNT(*) FROM spins WHERE datasetId = :d AND source = :src") fun spinCountBySource(d: Long, src: String): Int
    @Insert fun insertSpin(s: SpinE): Long
    @Insert fun insertSpins(list: List<SpinE>)
    @Query("DELETE FROM spins WHERE id = :id") fun deleteSpin(id: Long)
    @Query("DELETE FROM spins WHERE datasetId = :d") fun deleteSpinsOf(d: Long)

    // ── predictions / evaluations
    @Query("SELECT * FROM predictions WHERE datasetId = :d AND refCount = :r") fun prediction(d: Long, r: Int): PredictionE?
    @Insert(onConflict = OnConflictStrategy.IGNORE) fun insertPrediction(p: PredictionE): Long
    @Query("DELETE FROM predictions WHERE datasetId = :d AND refCount > :r") fun deletePredictionsAfter(d: Long, r: Int)
    @Query("DELETE FROM predictions WHERE datasetId = :d") fun deletePredictionsOf(d: Long)
    @Query("DELETE FROM evaluations WHERE predictionId IN (SELECT id FROM predictions WHERE datasetId = :d AND refCount > :r)") fun deleteEvaluationsAfter(d: Long, r: Int)
    @Query("SELECT COUNT(*) FROM predictions WHERE datasetId = :d") fun predictionCount(d: Long): Int
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun insertEvaluation(e: EvaluationE): Long
    @Query("DELETE FROM evaluations WHERE spinId = :spinId") fun deleteEvaluationFor(spinId: Long)
    @Query("DELETE FROM evaluations WHERE predictionId IN (SELECT id FROM predictions WHERE datasetId = :d)") fun deleteEvaluationsOf(d: Long)
    @Query("SELECT e.* FROM evaluations e JOIN predictions p ON p.id = e.predictionId WHERE p.datasetId = :d ORDER BY e.id DESC LIMIT :n") fun lastEvaluations(d: Long, n: Int): List<EvaluationE>
    @Query("SELECT e.* FROM evaluations e JOIN predictions p ON p.id = e.predictionId WHERE p.datasetId = :d ORDER BY e.id DESC LIMIT :n") fun lastEvaluationsFlow(d: Long, n: Int): Flow<List<EvaluationE>>
    @Query("SELECT COUNT(*) FROM evaluations e JOIN predictions p ON p.id = e.predictionId WHERE p.datasetId = :d") fun evaluationCount(d: Long): Int
    @Query("SELECT * FROM evaluations WHERE spinId = :spinId LIMIT 1") fun evaluationForSpin(spinId: Long): EvaluationE?
    @Query("SELECT * FROM predictions WHERE id = :id") fun predictionById(id: Long): PredictionE?
    @Query("SELECT * FROM predictions WHERE datasetId = :d ORDER BY refCount DESC LIMIT :n") fun lastPredictions(d: Long, n: Int): List<PredictionE>

    // ── state
    @Query("SELECT value FROM state_blobs WHERE `key` = :k") fun getState(k: String): String?
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun putState(s: StateE)
    @Query("DELETE FROM state_blobs WHERE `key` = :k") fun deleteState(k: String)
    @Query("DELETE FROM state_blobs WHERE `key` LIKE :prefix || '%'") fun deleteStatePrefix(prefix: String)

    // ── logs
    @Insert fun insertLog(l: LogE)
    @Query("SELECT * FROM logs ORDER BY id DESC LIMIT :n") fun logsFlow(n: Int): Flow<List<LogE>>
    @Query("SELECT * FROM logs ORDER BY id DESC LIMIT :n") fun logs(n: Int): List<LogE>
    @Query("DELETE FROM logs WHERE level = 'INFO'") fun clearInfoLogs()
    @Query("DELETE FROM logs WHERE id NOT IN (SELECT id FROM logs ORDER BY id DESC LIMIT :keep)") fun trimLogs(keep: Int)

    // ── experiments (silinmez; yalnızca durum değişir)
    @Insert fun insertExperiment(e: ExperimentE): Long
    @Update fun updateExperiment(e: ExperimentE)
    @Query("SELECT * FROM experiments ORDER BY id DESC") fun experiments(): List<ExperimentE>
    @Query("SELECT * FROM experiments ORDER BY id DESC") fun experimentsFlow(): Flow<List<ExperimentE>>
    @Query("SELECT * FROM experiments WHERE id = :id") fun experiment(id: Long): ExperimentE?
    @Query("SELECT * FROM experiments WHERE status = 'QUEUED' ORDER BY priority DESC, id ASC LIMIT 1") fun nextQueued(): ExperimentE?
    @Query("SELECT * FROM experiments WHERE status = 'RUNNING'") fun running(): List<ExperimentE>
    @Query("SELECT COUNT(*) FROM experiments") fun experimentCount(): Int
    @Query("SELECT MAX(id) FROM experiments") fun maxExperimentId(): Long?
    @Query("DELETE FROM experiments") fun deleteAllExperiments()

    // ── model sürümleri
    @Insert fun insertModelVersion(m: ModelVersionE): Long
    @Update fun updateModelVersion(m: ModelVersionE)
    @Query("SELECT * FROM model_versions ORDER BY id DESC") fun modelVersions(): List<ModelVersionE>
    @Query("SELECT * FROM model_versions ORDER BY id DESC") fun modelVersionsFlow(): Flow<List<ModelVersionE>>
    @Query("UPDATE model_versions SET champion = 0") fun clearChampion()
}
