package fan.lightningroulette.core

/** LAB sekmelerinin sunum verisi: tek tip bölüm (JSON'a yazılabilir). Arayüz yalnızca bunu çizer. */
class Sec(val type: String, val title: String, val d: Map<String, Any?>) {
    fun toMap(): Map<String, Any?> = mapOf("type" to type, "title" to title, "d" to d)
    companion object { fun fromMap(m: Map<String, Any?>) = Sec(m["type"].jstr(), m["title"].jstr(), m["d"].jmap()) }
}

object S {
    fun f(x: Double, d: Int = 1): String { val s = String.format(java.util.Locale.US, "%.${d}f", Math.abs(x)).replace('.', ','); return (if (x < -0.5 * Math.pow(10.0, -d.toDouble())) "−" else "") + s }
    fun pc(x: Double, d: Int = 1) = "%" + f(x, d)
    fun sg(x: Double, d: Int = 1, unit: String = " pp"): String = (if (x > 0.5 * Math.pow(10.0, -d.toDouble())) "+" else if (x < -0.5 * Math.pow(10.0, -d.toDouble())) "−" else "±") + f(Math.abs(x), d) + unit
    fun th(n: Int): String = String.format(java.util.Locale.US, "%,d", n).replace(',', ' ')

    /** [etiket, değer, alt metin, ton("", ok, warn, bad)] */
    fun tiles(items: List<List<String>>) = Sec("tiles", "", mapOf("items" to items))
    /** satır: [etiket, değer, ton, alt metin] */
    fun kv(title: String, rows: List<List<String>>, note: String = "") = Sec("kv", title, mapOf("rows" to rows, "note" to note))
    fun table(title: String, head: List<String>, rows: List<List<String>>, note: String = "", right: String = "", dim: List<Int> = emptyList(), al: String = "") =
        Sec("table", title, mapOf("head" to head, "rows" to rows, "note" to note, "right" to right, "dim" to dim, "al" to al))
    fun bars(title: String, labels: List<String>, values: List<Double>, expected: List<Double>, unit: String = "%", note: String = "") =
        Sec("bars", title, mapOf("labels" to labels, "values" to values, "expected" to expected, "unit" to unit, "note" to note))
    fun forest(title: String, labels: List<String>, delta: List<Double>, lo: List<Double>, hi: List<Double>, range: Double = 6.0, note: String = "") =
        Sec("forest", title, mapOf("labels" to labels, "delta" to delta, "lo" to lo, "hi" to hi, "range" to range, "note" to note))
    fun heat(title: String, rows: List<String>, cols: List<String>, v: List<List<Double>>, vmin: Double, vmax: Double, diverging: Boolean, dec: Int = 1, mark: List<List<Int>> = emptyList(), note: String = "") =
        Sec("heat", title, mapOf("rows" to rows, "cols" to cols, "v" to v, "vmin" to vmin, "vmax" to vmax, "div" to diverging, "dec" to dec, "mark" to mark, "note" to note))
    fun line(title: String, labels: List<String>, series: List<List<Double>>, hline: Double = Double.NaN, ymin: Double = Double.NaN, ymax: Double = Double.NaN, note: String = "") =
        Sec("line", title, mapOf("labels" to labels, "series" to series, "hline" to (if (hline.isNaN()) null else hline), "ymin" to (if (ymin.isNaN()) null else ymin), "ymax" to (if (ymax.isNaN()) null else ymax), "note" to note))
    fun reliab(title: String, px: List<Double>, py: List<Double>, n: List<Int>, note: String = "") = Sec("reliab", title, mapOf("px" to px, "py" to py, "n" to n, "note" to note))
    fun gauge(title: String, value: Int, cls: String, note: String) = Sec("gauge", title, mapOf("value" to value, "cls" to cls, "note" to note))
    fun text(title: String, body: String, tone: String = "") = Sec("text", title, mapOf("body" to body, "tone" to tone))
    fun flags(title: String, items: List<String>) = Sec("flags", title, mapOf("items" to items))
    fun bar(title: String, labels: List<String>, v: List<Double>, mx: List<Double>, right: List<String>) = Sec("pbars", title, mapOf("labels" to labels, "v" to v, "mx" to mx, "right" to right))

    fun encode(l: List<Sec>): String = Json.stringify(l.map { it.toMap() })
    fun decode(s: String): List<Sec> = try { Json.parse(s).jlist().map { Sec.fromMap(it.jmap()) } } catch (e: Exception) { emptyList() }
}
