package fan.superai.engine

import android.content.Context
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import org.json.JSONArray
import org.json.JSONObject
import fan.superai.v13.PyCapture
import fan.superai.v13.PyNumberInfo
import fan.superai.v13.PySideCapture
import fan.superai.v13.PySideMember
import fan.superai.v13.PySideStep
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

    /** v1.3: true ise bir sonraki tam replay üye dağılımlarını da döndürür (ilk kurulum / Full Replay). */
    @Volatile var captureMembers = false
    /** Son [replay] çağrısında yakalanan üye dağılımları (önbellekten yüklemede yalnızca yeni kayıtlar). */
    @Volatile var lastCapture: PyCapture? = null

    private fun rows(a: JSONArray): List<DoubleArray> = List(a.length()) { arr(a.getJSONArray(it)) }

    private fun capture(o: JSONObject, key: String, start: Int) {
        try {
            if (!o.has(key) || !o.has("ids")) { lastCapture = null; return }
            val ids = o.getJSONArray("ids"); val m = o.getJSONArray(key)
            lastCapture = PyCapture(List(ids.length()) { ids.getString(it) }, start, List(m.length()) { rows(m.getJSONArray(it)) })
        } catch (e: Exception) { lastCapture = null }
    }

    private fun parse(res: String): Pair<List<DoubleArray>, DoubleArray> {
        val o = JSONObject(res)
        val per = o.getJSONArray("per")
        return List(per.length()) { arr(per.getJSONArray(it)) } to arr(o.getJSONArray("next"))
    }

    override fun replay(values: IntArray, times: LongArray): Pair<List<DoubleArray>, DoubleArray> {
        val vj = JSONArray(values.toList()).toString()
        val tj = JSONArray(times.toList()).toString()
        lastCapture = null
        val cached = try { mod.callAttr("load_state", stateFile.absolutePath, vj, tj).toString() } catch (e: Exception) { "" }
        if (cached.isNotEmpty()) {
            val r = parse(cached)
            if (r.first.size == values.size) {
                val o = JSONObject(cached)
                capture(o, "tail_members", o.optInt("cached", 0))
                return r
            }
        }
        val want = captureMembers
        val text = mod.callAttr("replay", vj, tj, want).toString()
        val r = parse(text)
        if (want) capture(JSONObject(text), "members", 0)
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
        try { mod.callAttr("save_state", stateFile.absolutePath); sinceSave = 0; sideSave() } catch (_: Exception) {}
    }

    fun deleteState() { stateFile.delete(); sideFile.delete() }

    // ------------------------------------------------------------------ v1.3: üye dağılımları + yan meclisi
    private val sideFile = File(ctx.filesDir, "py_side_state.pkl")
    private var sideIds: List<String> = emptyList()
    private var sideNames: List<String> = emptyList()

    /** Canlı Python meclisinin son (bir sonraki) rakam tahminleri, üye bazında. */
    fun memberInfo(): PyNumberInfo? {
        val t = mod.callAttr("member_info").toString()
        if (t.length < 3) return null
        val o = JSONObject(t)
        val ids = o.getJSONArray("ids"); val names = o.getJSONArray("names")
        return PyNumberInfo(List(ids.length()) { ids.getString(it) }, List(names.length()) { names.getString(it) }, rows(o.getJSONArray("preds")))
    }

    private fun sideMembers(o: JSONObject): PySideStep {
        val m = o.getJSONArray("members")
        val list = List(m.length()) { i ->
            val e = m.getJSONObject(i)
            PySideMember(sideIds.getOrElse(i) { "py_side_$i" }, sideNames.getOrElse(i) { "yan $i" },
                arr(e.getJSONArray("bs")), arr(e.getJSONArray("oe")), arr(e.getJSONArray("comb")))
        }
        return PySideStep(list)
    }

    private fun readIds(o: JSONObject) {
        if (!o.has("ids")) return
        val ids = o.getJSONArray("ids"); val names = o.getJSONArray("names")
        sideIds = List(ids.length()) { ids.getString(it) }
        sideNames = List(names.length()) { names.getString(it) }
    }

    /**
     * Yan meclisini kayıtlarla eşitler. Kayıtlı durum mevcut verinin önekiyse yalnızca yeni kayıtlar işlenir;
     * değilse baştan öğrenilir. [sandbox]=true: canlı yan meclisine dokunmaz (araştırma).
     */
    fun sideSync(bs: IntArray, oe: IntArray, times: LongArray, sandbox: Boolean = false): PySideCapture? {
        val bj = JSONArray(bs.toList()).toString(); val oj = JSONArray(oe.toList()).toString()
        if (!sandbox) {
            val t = try { mod.callAttr("side_load", sideFile.absolutePath, bj, oj).toString() } catch (e: Exception) { "" }
            if (t.isNotEmpty()) {
                val o = JSONObject(t); readIds(o)
                val tail = o.getJSONArray("tail")
                return PySideCapture(o.optInt("cached", 0), List(tail.length()) { sideMembers(tail.getJSONObject(it)) },
                    sideMembers(o))
            }
        }
        val t = mod.callAttr("side_replay", bj, oj, JSONArray(times.toList()).toString(), sandbox).toString()
        val o = JSONObject(t); readIds(o)
        val st = o.getJSONArray("steps")
        val cap = PySideCapture(0, List(st.length()) { sideMembers(st.getJSONObject(it)) }, sideMembers(o.getJSONObject("next")))
        if (!sandbox) sideSave()
        return cap
    }

    fun sideStep(bs: Int, oe: Int, t: Long): PySideStep? {
        val s = mod.callAttr("side_step", bs, oe, t).toString()
        return if (s.isEmpty()) null else sideMembers(JSONObject(s))
    }

    fun sideNext(): PySideStep? {
        val s = mod.callAttr("side_next").toString()
        return if (s.isEmpty()) null else sideMembers(JSONObject(s))
    }

    /** null → ulaşılamadı (çağıran yan meclisi yeniden öğretmeli). */
    fun sideUndoTo(n: Int): PySideStep? {
        val s = try { mod.callAttr("side_undo_to", n).toString() } catch (e: Exception) { "" }
        return if (s.isEmpty()) null else sideMembers(JSONObject(s))
    }

    fun sideSave() { try { mod.callAttr("side_save", sideFile.absolutePath) } catch (_: Exception) {} }

    /** ARAŞTIRMA modu: canlı meclise dokunmadan tüm geçmişi oynatır (üye dağılımlarıyla). */
    fun sandboxReplay(values: IntArray, times: LongArray): PyCapture? {
        val vj = JSONArray(values.toList()).toString(); val tj = JSONArray(times.toList()).toString()
        val o = JSONObject(mod.callAttr("sandbox_replay", vj, tj).toString())
        val ids = o.getJSONArray("ids"); val m = o.getJSONArray("members")
        return PyCapture(List(ids.length()) { ids.getString(it) }, 0, List(m.length()) { rows(m.getJSONArray(it)) })
    }

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
