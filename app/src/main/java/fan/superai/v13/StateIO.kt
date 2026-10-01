package fan.superai.v13

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** Küçük, sürümlü ikili serileştirme (JSON yok, gereksiz büyük dosya yok). */
class Out {
    private val bos = ByteArrayOutputStream()
    private val d = DataOutputStream(bos)
    fun i(v: Int) = d.writeInt(v)
    fun l(v: Long) = d.writeLong(v)
    fun d(v: Double) = d.writeDouble(v)
    fun b(v: Boolean) = d.writeBoolean(v)
    fun s(v: String) = d.writeUTF(v)
    fun da(a: DoubleArray) { d.writeInt(a.size); for (x in a) d.writeDouble(x) }
    fun ia(a: IntArray) { d.writeInt(a.size); for (x in a) d.writeInt(x) }
    fun ba(a: BooleanArray) { d.writeInt(a.size); for (x in a) d.writeBoolean(x) }
    fun bytes(a: ByteArray) { d.writeInt(a.size); d.write(a) }
    fun toBytes(): ByteArray { d.flush(); return bos.toByteArray() }
}

class In(data: ByteArray) {
    private val d = DataInputStream(ByteArrayInputStream(data))
    fun i() = d.readInt()
    fun l() = d.readLong()
    fun d() = d.readDouble()
    fun b() = d.readBoolean()
    fun s(): String = d.readUTF()
    fun da(): DoubleArray = DoubleArray(d.readInt()) { d.readDouble() }
    fun ia(): IntArray = IntArray(d.readInt()) { d.readInt() }
    fun ba(): BooleanArray = BooleanArray(d.readInt()) { d.readBoolean() }
    fun bytes(): ByteArray { val n = d.readInt(); val a = ByteArray(n); d.readFully(a); return a }
    /** Mevcut diziyi yerinde doldurur (boyut uyuşmazsa hata). */
    fun fill(target: DoubleArray) {
        val a = da()
        if (a.size != target.size) throw FanException(ErrorCodes.STATE_LOAD, "dizi boyutu ${a.size} != ${target.size}")
        a.copyInto(target)
    }
}

object Gz {
    fun pack(b: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(b) }
        return bos.toByteArray()
    }
    fun unpack(b: ByteArray): ByteArray = GZIPInputStream(ByteArrayInputStream(b)).use { it.readBytes() }
}

/** Gereksiz büyük log üretmeyen olay günlüğü. Android Log bağlantısı uygulama tarafında yapılır. */
object FanLog {
    const val NEW_RECORD = "NEW_RECORD"
    const val PREDICTION_CREATED = "PREDICTION_CREATED"
    const val PREDICTION_LOCKED = "PREDICTION_LOCKED"
    const val ACTUAL_RESULT_RECEIVED = "ACTUAL_RESULT_RECEIVED"
    const val MODEL_UPDATED = "MODEL_UPDATED"
    const val REGIME_CHANGED = "REGIME_CHANGED"
    const val CALIBRATION_UPDATED = "CALIBRATION_UPDATED"
    const val ENSEMBLE_UPDATED = "ENSEMBLE_UPDATED"
    const val STATE_PERSISTED = "STATE_PERSISTED"
    const val REPLAY_STARTED = "REPLAY_STARTED"
    const val REPLAY_COMPLETED = "REPLAY_COMPLETED"
    const val LEAKAGE_CHECK = "LEAKAGE_CHECK"
    const val ERROR = "ERROR"

    class Entry(val time: Long, val event: String, val message: String)

    private const val CAP = 200
    private val ring = ArrayDeque<Entry>()
    /** Gürültülü olaylar (adım başına) yalnızca bellek halkasına yazılır; sink'e ise önemli olaylar gider. */
    @Volatile var sink: ((String, String) -> Unit)? = null
    private val quietTl = ThreadLocal<Boolean>()
    /** Replay sırasında adım başı olayları bastırır (yalnızca çağıran iş parçacığı için). */
    var quiet: Boolean
        get() = quietTl.get() == true
        set(v) { quietTl.set(v) }

    private val noisy = setOf(NEW_RECORD, PREDICTION_CREATED, PREDICTION_LOCKED, ACTUAL_RESULT_RECEIVED,
        MODEL_UPDATED, CALIBRATION_UPDATED, ENSEMBLE_UPDATED, STATE_PERSISTED)

    @Synchronized fun event(name: String, msg: String) {
        if (quiet && name in noisy) return
        ring.addLast(Entry(System.currentTimeMillis(), name, msg))
        while (ring.size > CAP) ring.removeFirst()
        sink?.invoke(name, msg)
    }

    @Synchronized fun recent(n: Int = 60): List<Entry> = ring.toList().takeLast(n)
    @Synchronized fun clear() { ring.clear() }
}
