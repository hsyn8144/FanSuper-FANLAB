package fan.lightningroulette.engine

import android.content.Context
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import fan.lightningroulette.core.PyHost
import fan.lightningroulette.core.PyOut
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Gömülü Python meclisi (Chaquopy). Tüm çağrılar motor iş parçacığından yapılır.
 * Hata olursa fırlatır; [fan.lightningroulette.core.Session] bunu yakalar, status=ERROR yazar ve Kotlin-only'ye düşer.
 */
class PyBridge(private val ctx: Context, private val bounds: IntArray) : PyHost {
    private val mod: PyObject
    private val stateFile = File(ctx.filesDir, "py_state.b64")
    @Volatile var lastLam: List<Double> = emptyList()
    @Volatile var names: List<String> = emptyList()
    private var sinceSave = 0

    init {
        if (!Python.isStarted()) Python.start(AndroidPlatform(ctx))
        mod = Python.getInstance().getModule("lr_council")
        configure()
        try { if (stateFile.exists()) mod.callAttr("state_load_b64", stateFile.readText()) } catch (_: Exception) { /* bozuk durum: Python kendini yeniden kurar */ }
    }

    private fun configure() {
        mod.callAttr("configure", JSONObject().put("bounds", JSONArray(bounds.toList())).toString())
    }

    override fun predict(window: IntArray, offset: Int): PyOut {
        val sb = StringBuilder(window.size * 3 + 2)
        sb.append('[')
        for (i in window.indices) { if (i > 0) sb.append(','); sb.append(window[i]) }
        sb.append(']')
        val res = mod.callAttr("live_predict", sb.toString(), offset).toString()
        val o = JSONObject(res)
        val ids = o.getJSONArray("ids"); val probs = o.getJSONArray("probs"); val lam = o.getJSONArray("lam")
        val idList = List(ids.length()) { ids.getString(it) }
        val pl = List(probs.length()) { r -> val a = probs.getJSONArray(r); DoubleArray(a.length()) { a.getDouble(it) } }
        lastLam = List(lam.length()) { lam.getDouble(it) }
        names = o.getJSONArray("names").let { n -> List(n.length()) { n.getString(it) } }
        if (++sinceSave >= 10) save()
        return PyOut(idList, pl)
    }

    /** Durumu dosyaya yazar (atomik: geçici dosya + yeniden adlandırma). */
    fun save() {
        sinceSave = 0
        try {
            val tmp = File(ctx.filesDir, "py_state.tmp")
            tmp.writeText(mod.callAttr("state_save_b64").toString())
            if (!tmp.renameTo(stateFile)) { stateFile.delete(); tmp.renameTo(stateFile) }
        } catch (_: Exception) { /* yazılamazsa bir sonraki sefer yeniden denenir */ }
    }

    fun stateN(): Int = try { mod.callAttr("state_n").toInt() } catch (_: Exception) { -1 }

    /** Python meclisini sıfırla (veri sıfırlama / dataset değişimi). */
    fun reset() { configure(); stateFile.delete() }

    /** Canlı durumdan bağımsız kendi kendine sınama (ayrı Council örneği). */
    fun selfTest(): String = mod.callAttr("selftest").toString()

    fun info(): String = try { mod.callAttr("info").toString() } catch (e: Exception) { "hata: ${e.message}" }

    // ── LAB: parça parça replay (iptal/ilerleme); her adımda yalnızca values[:i] görülür
    fun replayBegin(values: IntArray, warmFrom: Int) {
        val sb = StringBuilder(values.size * 3 + 2)
        sb.append('[')
        for (i in values.indices) { if (i > 0) sb.append(','); sb.append(values[i]) }
        sb.append(']')
        mod.callAttr("replay_begin", sb.toString(), warmFrom)
    }

    /** (recordFrom, nSteps) → her kayıtlı adım için 8 üye × 37 olasılık (float32). */
    fun replayChunk(recordFrom: Int, nSteps: Int): List<List<DoubleArray>> {
        val b64 = mod.callAttr("replay_chunk_b64", recordFrom, nSteps).toString()
        if (b64.isEmpty()) return emptyList()
        val raw = java.util.Base64.getDecoder().decode(b64)
        val bb = java.nio.ByteBuffer.wrap(raw).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val members = 8; val k = 37
        val steps = raw.size / (4 * members * k)
        return List(steps) { List(members) { DoubleArray(k) { bb.float.toDouble() } } }
    }
}
