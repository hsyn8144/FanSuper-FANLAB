package fan.superai.engine

import android.content.Context
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Uygulamaya gömülü Python meclisi (Chaquopy). Tüm çağrılar motor iş parçacığından yapılır.
 */
class PythonBridge(private val ctx: Context, private val cfgJson: String) : ExternalCouncil {
    private companion object { const val SAVE_EVERY = 10 }

    private val mod: PyObject
    private val stateFile = File(ctx.filesDir, "py_state.pkl")
    private var sinceSave = 0

    init {
        if (!Python.isStarted()) Python.start(AndroidPlatform(ctx))
        mod = Python.getInstance().getModule("fan_super")
        mod.callAttr("configure", cfgJson)
    }

    private fun arr(a: JSONArray) = DoubleArray(a.length()) { a.getDouble(it) }

    /** Python listesini (PyObject) JSON'a hiç uğramadan DoubleArray'e çevirir — sıcak yol için. */
    private fun arr(o: PyObject): DoubleArray {
        val l = o.asList()
        return DoubleArray(l.size) { l[it].toDouble() }
    }

    private fun parse(res: String): Pair<List<DoubleArray>, DoubleArray> {
        val o = JSONObject(res)
        val per = o.getJSONArray("per")
        return List(per.length()) { arr(per.getJSONArray(it)) } to arr(o.getJSONArray("next"))
    }

    override fun replay(values: IntArray, times: LongArray): Pair<List<DoubleArray>, DoubleArray> {
        val vj = JSONArray(values.toList()).toString()
        val tj = JSONArray(times.toList()).toString()
        val cached = try { mod.callAttr("load_state", stateFile.absolutePath, vj, tj).toString() } catch (e: Exception) { "" }
        if (cached.isNotEmpty()) {
            val r = parse(cached)
            if (r.first.size == values.size) return r
        }
        val r = parse(mod.callAttr("replay", vj, tj).toString())
        save()
        return r
    }

    /**
     * Sıcak yol: her tek tuşlamada çağrılır. `step_fast` düz Python listesi döndürür,
     * JSON'a hiç uğramaz (json.dumps + JSONArray.parse maliyeti kalkar).
     */
    override fun step(value: Int, time: Long): DoubleArray {
        val r = arr(mod.callAttr("step_fast", value, time))
        if (++sinceSave >= SAVE_EVERY) save()
        return r
    }

    /**
     * Python meclisini tam olarak [count] kayda döndürür (hedef tutmazsa null).
     * `undo_to_fast` başarısızlıkta boş liste döner (gerçek tahminler hiçbir zaman
     * boş olmadığı için bu belirsizliğe yer bırakmaz), başarıda düz liste döner —
     * JSON'a hiç uğramadan.
     */
    override fun undoTo(count: Int): DoubleArray? {
        val res = try { mod.callAttr("undo_to_fast", count) } catch (e: Exception) { null } ?: return null
        val l = res.asList()
        if (l.isEmpty()) return null
        sinceSave = SAVE_EVERY      // durum dosyası bir sonraki adımda tazelenir
        return DoubleArray(l.size) { l[it].toDouble() }
    }

    fun save() {
        try { mod.callAttr("save_state", stateFile.absolutePath); sinceSave = 0 } catch (_: Exception) {}
    }

    fun deleteState() { stateFile.delete() }

    override fun stats(): List<MemberStat> {
        val a = JSONArray(mod.callAttr("stats").toString())
        return List(a.length()) { i ->
            val o = a.getJSONObject(i)
            MemberStat(o.getString("id"), o.getString("name"), o.getDouble("top1"), o.getDouble("top2"),
                o.getDouble("weight"), o.getBoolean("benched"), o.getBoolean("enabled"), o.getInt("n"))
        }.sortedWith(compareBy<MemberStat>({ !it.enabled }, { it.benched }).thenByDescending { it.weight })
    }

    override fun info(): Map<String, String> {
        val o = JSONObject(mod.callAttr("info").toString())
        return o.keys().asSequence().associateWith { o.get(it).toString() }
    }
}
