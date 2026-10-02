package fan.lightningroulette.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Dataset (sürümlü): her içe aktarma yeni sürüm üretir; silinmez, yalnızca pasife alınır (Prompt §38). */
@Entity(tableName = "datasets")
data class DatasetE(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String, val createdAt: Long, val source: String, val synthetic: Boolean, val active: Boolean,
    val version: Int, val hash: String, val note: String
)

@Entity(tableName = "dataset_versions", indices = [Index("datasetId")])
data class DatasetVersionE(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val datasetId: Long, val version: Int, val spinCount: Int, val hash: String, val createdAt: Long, val note: String
)

/** Ham spin. Sıra = id (artan). Arayüz en yeni → en eski gösterir; analiz en eski → en yeni okur. */
@Entity(tableName = "spins", indices = [Index(value = ["datasetId", "id"])])
data class SpinE(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val datasetId: Long, val value: Int, val tsMs: Long, val tsType: String, val source: String, val batchId: Long
)

/** Kilitli tahmin: aynı (dataset, refCount) için YALNIZCA bir kayıt (unique) → ikinci tahmin imkânsız (LR-E-LOCK-002). */
@Entity(tableName = "predictions", indices = [Index(value = ["datasetId", "refCount"], unique = true)])
data class PredictionE(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val datasetId: Long, val refCount: Int, val createdAt: Long, val json: String, val pyStatus: String, val modelVersion: String
)

@Entity(tableName = "evaluations", indices = [Index(value = ["predictionId"], unique = true), Index("spinId")])
data class EvaluationE(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val predictionId: Long, val spinId: Long, val actual: Int, val json: String, val createdAt: Long,
    val exactHit: Boolean, val candHit: Boolean, val tableHits: Int
)

/** LAB deneyi / kuyruk satırı. Başarısız olanlar SİLİNMEZ (başarısız hipotez hafızası, Prompt §35). */
@Entity(tableName = "experiments", indices = [Index(value = ["code"], unique = true), Index("status")])
data class ExperimentE(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String, val kind: String, val datasetId: Long, val datasetHash: String, val hypothesis: String,
    val paramsJson: String, val paramHash: String, val seed: Long, val codeVersion: String,
    val status: String, val cls: String, val deltaPp: Double, val n: Int, val reason: String,
    val resultJson: String, val ckpt: String, val priority: Int, val progress: Int,
    val createdAt: Long, val startedAt: Long, val finishedAt: Long
)

@Entity(tableName = "state_blobs")
data class StateE(@PrimaryKey val key: String, val value: String, val updatedAt: Long)

@Entity(tableName = "logs")
data class LogE(@PrimaryKey(autoGenerate = true) val id: Long = 0, val ts: Long, val level: String, val code: String?, val event: String, val detail: String)

@Entity(tableName = "model_versions")
data class ModelVersionE(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val version: String, val createdAt: Long, val champion: Boolean, val paramsJson: String, val note: String
)

@Entity(tableName = "import_batches")
data class ImportBatchE(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val datasetId: Long, val fileName: String, val format: String, val total: Int, val valid: Int, val invalid: Int,
    val duplicate: Int, val newCount: Int, val createdAt: Long, val synthetic: Boolean
)
