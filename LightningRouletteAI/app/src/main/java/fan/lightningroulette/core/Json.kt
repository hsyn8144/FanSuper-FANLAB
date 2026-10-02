package fan.lightningroulette.core

import kotlin.math.abs

/** Bağımsız (Android'e ihtiyaç duymayan) küçük JSON yazıcı/ayrıştırıcı: testler JVM'de çalışabilsin diye. */
object Json {
    fun stringify(v: Any?): String { val sb = StringBuilder(); write(sb, v); return sb.toString() }

    private fun quote(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) when {
            c == '"' -> sb.append("\\\"")
            c == '\\' -> sb.append("\\\\")
            c == '\n' -> sb.append("\\n")
            c == '\r' -> sb.append("\\r")
            c == '\t' -> sb.append("\\t")
            c.code < 0x20 -> sb.append(String.format("\\u%04x", c.code))
            else -> sb.append(c)
        }
        sb.append('"')
    }

    private fun num(d: Double): String =
        if (d == Math.rint(d) && abs(d) < 1e15) d.toLong().toString() else java.lang.Double.toString(d)

    private fun write(sb: StringBuilder, v: Any?) {
        when (v) {
            null -> sb.append("null")
            is String -> quote(sb, v)
            is Boolean -> sb.append(v.toString())
            is Int -> sb.append(v.toString())
            is Long -> sb.append(v.toString())
            is Double -> if (v.isNaN() || v.isInfinite()) sb.append("null") else sb.append(num(v))
            is Float -> write(sb, v.toDouble())
            is Map<*, *> -> {
                sb.append('{'); var first = true
                for ((k, x) in v) { if (!first) sb.append(','); first = false; quote(sb, k.toString()); sb.append(':'); write(sb, x) }
                sb.append('}')
            }
            is Iterable<*> -> {
                sb.append('['); var first = true
                for (x in v) { if (!first) sb.append(','); first = false; write(sb, x) }
                sb.append(']')
            }
            is DoubleArray -> write(sb, v.toList())
            is IntArray -> write(sb, v.toList())
            is LongArray -> write(sb, v.toList())
            is BooleanArray -> write(sb, v.toList())
            is Array<*> -> write(sb, v.toList())
            else -> quote(sb, v.toString())
        }
    }

    /** Sonuç: Map<String,Any?> | List<Any?> | String | Long | Double | Boolean | null */
    fun parse(text: String): Any? {
        val p = P(text); p.ws(); val v = p.value(); p.ws()
        if (p.i != text.length) throw IllegalArgumentException("JSON sonunda fazla veri (${p.i})")
        return v
    }

    private class P(val s: String) {
        var i = 0
        fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun value(): Any? {
            ws()
            if (i >= s.length) throw IllegalArgumentException("JSON beklenmedik son")
            return when (s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> { expect("true"); true }
                'f' -> { expect("false"); false }
                'n' -> { expect("null"); null }
                else -> number()
            }
        }
        fun expect(w: String) { if (!s.startsWith(w, i)) throw IllegalArgumentException("JSON '$w' bekleniyordu ($i)"); i += w.length }
        fun obj(): Map<String, Any?> {
            val m = LinkedHashMap<String, Any?>(); i++; ws()
            if (s[i] == '}') { i++; return m }
            while (true) {
                ws(); val k = str(); ws()
                if (s[i] != ':') throw IllegalArgumentException("JSON ':' bekleniyordu ($i)")
                i++; m[k] = value(); ws()
                if (s[i] == ',') { i++; continue }
                if (s[i] == '}') { i++; return m }
                throw IllegalArgumentException("JSON nesnesinde ',' ya da '}' bekleniyordu ($i)")
            }
        }
        fun arr(): List<Any?> {
            val l = ArrayList<Any?>(); i++; ws()
            if (s[i] == ']') { i++; return l }
            while (true) {
                l.add(value()); ws()
                if (s[i] == ',') { i++; continue }
                if (s[i] == ']') { i++; return l }
                throw IllegalArgumentException("JSON dizisinde ',' ya da ']' bekleniyordu ($i)")
            }
        }
        fun str(): String {
            if (s[i] != '"') throw IllegalArgumentException("JSON metin bekleniyordu ($i)")
            i++; val sb = StringBuilder()
            while (true) {
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        when (val e = s[i++]) {
                            'n' -> sb.append('\n'); 'r' -> sb.append('\r'); 't' -> sb.append('\t'); 'b' -> sb.append('\b'); 'f' -> sb.append('\u000c')
                            'u' -> { sb.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                            else -> sb.append(e)
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }
        fun number(): Any {
            val st = i
            while (i < s.length && (s[i].isDigit() || s[i] == '-' || s[i] == '+' || s[i] == '.' || s[i] == 'e' || s[i] == 'E')) i++
            val t = s.substring(st, i)
            if (t.isEmpty()) throw IllegalArgumentException("JSON sayı bekleniyordu ($st)")
            return if (t.contains('.') || t.contains('e') || t.contains('E')) t.toDouble() else t.toLongOrNull() ?: t.toDouble()
        }
    }
}

// ---- Kolay erişim yardımcıları
@Suppress("UNCHECKED_CAST") fun Any?.jmap(): Map<String, Any?> = (this as? Map<String, Any?>) ?: emptyMap()
@Suppress("UNCHECKED_CAST") fun Any?.jlist(): List<Any?> = (this as? List<Any?>) ?: emptyList()
fun Any?.jnum(d: Double = 0.0): Double = when (this) { is Number -> this.toDouble(); is String -> this.toDoubleOrNull() ?: d; else -> d }
fun Any?.jint(d: Int = 0): Int = when (this) { is Number -> this.toInt(); is String -> this.toIntOrNull() ?: d; else -> d }
fun Any?.jlong(d: Long = 0L): Long = when (this) { is Number -> this.toLong(); is String -> this.toLongOrNull() ?: d; else -> d }
fun Any?.jstr(d: String = ""): String = when (this) { null -> d; is String -> this; else -> this.toString() }
fun Any?.jbool(d: Boolean = false): Boolean = when (this) { is Boolean -> this; is String -> this == "true"; else -> d }
fun Any?.jdoubles(): DoubleArray = jlist().map { it.jnum() }.toDoubleArray()
fun Any?.jints(): IntArray = jlist().map { it.jint() }.toIntArray()
