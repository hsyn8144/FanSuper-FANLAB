package fan.superai.v13.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Ham veri: recordId, timestamp, number, bigSmall, oddEven, combinedSide. */
@Entity(tableName = "FanRecord", indices = [Index("timestamp")])
data class FanRecordEntity(
    @PrimaryKey val recordId: Long,
    val timestamp: Long,
    val number: Int,
    val bigSmall: String,
    val oddEven: String,
    val combinedSide: String
)

/** Her tahmin (LIVE / REPLAY), kilit bilgileri ve — sonuç gelince — değerlendirme. */
@Entity(tableName = "PredictionRecord", indices = [Index(value = ["mode", "sequence"], unique = true)])
data class PredictionRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mode: String,
    val sequence: Int,
    val lockId: String,
    val lockHash: Long,
    val predictionTimestamp: Long,
    val lastKnownRecordId: Long,
    val predictedNumber: Int,
    val bigSmall: String,
    val oddEven: String,
    val sideLabel: String,
    val confidence: Double,
    val sideConfidence: Double,
    val entropy: Double,
    val regime: String,
    val ensembleVersion: String,
    val numberDist: String,
    val sideDist: String,
    val actualRecordId: Long? = null,
    val actualNumber: Int? = null,
    val actualTimestamp: Long? = null,
    val numberHit: Boolean? = null,
    val top2Hit: Boolean? = null,
    val bigSmallHit: Boolean? = null,
    val oddEvenHit: Boolean? = null,
    val sideHit: Boolean? = null,
    val numberLogLoss: Double? = null,
    val sideLogLoss: Double? = null
)

@Entity(tableName = "PredictionExplanation")
data class PredictionExplanationEntity(
    @PrimaryKey val sequence: Int,
    val lockId: String,
    val payload: String
)

@Entity(tableName = "ModelState")
data class ModelStateEntity(
    @PrimaryKey val stateKey: String,
    val blob: ByteArray,
    val updatedAt: Long,
    val recordCount: Int
)

@Entity(tableName = "ModelPerformance")
data class ModelPerformanceEntity(
    @PrimaryKey val stateKey: String,
    val axis: String,
    val modelId: String,
    val name: String,
    val grp: String,
    val n: Int,
    val top1: Int,
    val top2: Int,
    val logLoss: Double,
    val brier: Double,
    val entropy: Double,
    val recent: Double,
    val calGap: Double,
    val weight: Double,
    val gain: Double,
    val blob: ByteArray,
    val updatedAt: Long
)

@Entity(tableName = "RegimeState")
data class RegimeStateEntity(
    @PrimaryKey val stateKey: String,
    val blob: ByteArray,
    val updatedAt: Long
)

@Entity(tableName = "CalibrationState")
data class CalibrationStateEntity(
    @PrimaryKey val stateKey: String,
    val blob: ByteArray,
    val updatedAt: Long
)

@Entity(tableName = "EnsembleState")
data class EnsembleStateEntity(
    @PrimaryKey val stateKey: String,
    val blob: ByteArray,
    val updatedAt: Long
)

@Entity(tableName = "ReplayState")
data class ReplayStateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mode: String,
    val startedAt: Long,
    val finishedAt: Long,
    val processed: Int,
    val total: Int,
    val status: String,
    val leakChecks: Long,
    val leakViolations: Long,
    val summary: String
)
