package fan.lightningroulette.core

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class ImportRow(val line: Int, val raw: String, val value: Int?, val ts: Long?, val reason: String?)

/** İçe aktarma raporu (Prompt §46–§49): TOTAL / VALID / INVALID / DUPLICATE / NEW. Kayıtlar yalnızca onaylandığında yazılır. */
class ImportReport(
    val format: String, val order: String, val mode: String,
    val total: Int, val valid: Int, val invalid: Int, val duplicate: Int, val newCount: Int,
    val invalidRows: List<ImportRow>, val spins: List<Spin>, val overlap: Int, val syntheticTs: Boolean,
    val reordered: Int, val notes: List<String>
) {
    val ok: Boolean get() = newCount > 0
}

object Importer {
    private val VALUE_KEYS = setOf("value", "number", "numara", "sayi", "sayı", "sonuc", "sonuç", "result", "spin", "n", "num", "no")
    private val TS_KEYS = setOf("ts", "ts_ms", "time", "timestamp", "zaman", "date", "tarih", "datetime", "ts_iso", "created", "createdat")

    fun detect(text: String, fileName: String = ""): String {
        val t = text.trimStart('\uFEFF', ' ', '\n', '\r', '\t')
        if (t.startsWith("[") || t.startsWith("{")) return "JSON"
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (ext == "json") return "JSON"
        if (ext == "txt") return "TXT"
        val lines = t.lines().filter { it.isNotBlank() }.take(6)
        if (lines.isEmpty()) return "TXT"
        if (lines.any { it.contains(';') || it.contains('\t') || it.any { c -> c.isLetter() } }) return "CSV"
        if (ext == "csv") return "CSV"
        if (lines.size >= 2 && lines.all { it.contains(',') } && lines.map { it.split(',').size }.toSet().size == 1) return "CSV"
        return "TXT"
    }

    fun parseTs(raw: String, zone: ZoneId = ZoneId.of("UTC")): Long? {
        val s = raw.trim().trim('"')
        if (s.isEmpty()) return null
        if (s.all { it.isDigit() }) {
            val v = s.toLongOrNull() ?: return null
            return if (s.length >= 12) v else if (s.length >= 9) v * 1000L else null
        }
        try { return Instant.parse(s).toEpochMilli() } catch (e: Exception) { }
        try { return OffsetDateTime.parse(s).toInstant().toEpochMilli() } catch (e: Exception) { }
        val pats = listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy-MM-dd'T'HH:mm:ss", "dd.MM.yyyy HH:mm:ss", "dd.MM.yyyy HH:mm", "dd/MM/yyyy HH:mm:ss", "dd/MM/yyyy HH:mm", "yyyy/MM/dd HH:mm:ss")
        for (p in pats) try { return LocalDateTime.parse(s, DateTimeFormatter.ofPattern(p)).atZone(zone).toInstant().toEpochMilli() } catch (e: Exception) { }
        return null
    }

    private fun valueOf(cell: String): Pair<Int?, String?> {
        val s = cell.trim().trim('"', '\'').trim()
        if (s.isEmpty()) return null to "boş"
        if (s == "00") return null to "“00” Amerikan ruletidir (0–36 geçerli)"
        val v = s.toIntOrNull() ?: return null to "sayı değil"
        if (v !in 0..36) return null to "aralık dışı ($v)"
        return v to null
    }

    private fun parseTxt(text: String): List<ImportRow> {
        val out = ArrayList<ImportRow>()
        var ln = 0
        for (line in text.lines()) {
            ln++
            val l = line.trim('\uFEFF', ' ', '\r')
            if (l.isEmpty() || l.startsWith("#")) continue
            for (tok in l.split(Regex("[\\s,;]+"))) {
                if (tok.isEmpty()) continue
                val (v, r) = valueOf(tok)
                out.add(ImportRow(ln, tok, v, null, r))
            }
        }
        return out
    }

    private fun splitCsv(line: String, d: Char): List<String> {
        val out = ArrayList<String>(); val sb = StringBuilder(); var q = false
        for (c in line) { if (c == '"') q = !q else if (c == d && !q) { out.add(sb.toString()); sb.setLength(0) } else sb.append(c) }
        out.add(sb.toString()); return out
    }

    private fun parseCsv(text: String): List<ImportRow> {
        val lines = text.lines().mapIndexed { i, s -> (i + 1) to s.trim('\uFEFF', '\r') }.filter { it.second.isNotBlank() && !it.second.trimStart().startsWith("#") }
        if (lines.isEmpty()) return emptyList()
        val first = lines[0].second
        val d = listOf(',', ';', '\t').maxByOrNull { c -> first.count { it == c } }!!.takeIf { first.contains(it) } ?: ','
        val head = splitCsv(first, d)
        val hasHeader = head.any { c -> c.any { it.isLetter() } }
        var vi = 0; var ti = -1
        if (hasHeader) {
            val names = head.map { it.trim().lowercase().replace(" ", "_") }
            vi = names.indexOfFirst { it in VALUE_KEYS }.let { if (it >= 0) it else names.indexOfFirst { n -> n != "seq" && n != "id" && n !in TS_KEYS }.coerceAtLeast(0) }
            ti = names.indexOfFirst { it in TS_KEYS }
        } else if (head.size >= 2) ti = 1
        val out = ArrayList<ImportRow>()
        for ((idx, p) in lines.withIndex()) {
            if (idx == 0 && hasHeader) continue
            val cells = splitCsv(p.second, d)
            val (v, r) = valueOf(cells.getOrElse(vi) { "" })
            var ts: Long? = null
            if (ti in cells.indices && cells[ti].isNotBlank()) ts = parseTs(cells[ti])
            out.add(ImportRow(p.first, p.second.take(60), v, ts, r))
        }
        return out
    }

    private fun parseJson(text: String): List<ImportRow> {
        val root = try { Json.parse(text.trim('\uFEFF', ' ', '\n', '\r', '\t')) } catch (e: Exception) { throw LrError(Codes.IMP, "JSON okunamadı: ${e.message}") }
        val arr: List<Any?> = when (root) {
            is List<*> -> root
            is Map<*, *> -> { val m = root.jmap(); (listOf("spins", "data", "results", "history", "values").firstNotNullOfOrNull { k -> (m[k] as? List<*>) }) ?: throw LrError(Codes.IMP, "JSON içinde spin dizisi yok (spins/data/results/history/values)") }
            else -> throw LrError(Codes.IMP, "JSON biçimi tanınmadı")
        }
        val out = ArrayList<ImportRow>()
        for ((i, e) in arr.withIndex()) {
            val ln = i + 1
            when (e) {
                is Number -> { val (v, r) = valueOf(e.toString().removeSuffix(".0")); out.add(ImportRow(ln, e.toString(), v, null, r)) }
                is String -> { val (v, r) = valueOf(e); out.add(ImportRow(ln, e, v, null, r)) }
                is Map<*, *> -> {
                    val m = e.jmap().mapKeys { it.key.lowercase() }
                    val vk = VALUE_KEYS.firstOrNull { it in m }
                    val tk = TS_KEYS.firstOrNull { it in m }
                    val (v, r) = if (vk == null) null to "değer alanı yok" else valueOf(m[vk].jstr().removeSuffix(".0"))
                    val ts = if (tk == null) null else (m[tk].let { x -> if (x is Number) parseTs(x.toLong().toString()) else parseTs(x.jstr()) })
                    out.add(ImportRow(ln, e.toString().take(60), v, ts, r))
                }
                else -> out.add(ImportRow(ln, e.toString().take(40), null, null, "tanınmayan öğe"))
            }
        }
        return out
    }

    /**
     * Ayrıştır + doğrula + çakışma tespiti. existing: mevcut geçmiş (eski→yeni). mode: APPEND | NEW_DATASET.
     * order: AUTO (zaman damgası varsa zamana göre; yoksa dosya sırası eski→yeni) | OLD_FIRST | NEW_FIRST.
     */
    fun analyse(text: String, fileName: String = "", format: String? = null, order: String = "AUTO", mode: String = "APPEND",
                existing: List<Spin> = emptyList(), nowMs: Long = System.currentTimeMillis()): ImportReport {
        val fmt = (format ?: detect(text, fileName)).uppercase()
        val rows = when (fmt) { "JSON" -> parseJson(text); "CSV" -> parseCsv(text); else -> parseTxt(text) }
        val notes = ArrayList<String>()
        val bad = rows.filter { it.value == null }
        var good = rows.filter { it.value != null }.toMutableList()
        var reordered = 0
        val anyTs = good.any { it.ts != null }
        when {
            order == "NEW_FIRST" -> good = good.reversed().toMutableList()
            order == "AUTO" && anyTs -> {
                val sorted = good.withIndex().sortedWith(compareBy({ it.value.ts ?: Long.MIN_VALUE }, { it.index })).map { it.value }
                reordered = sorted.indices.count { sorted[it] !== good[it] }
                if (reordered > 0) notes.add("Zaman damgasına göre sıralandı ($reordered satır yer değiştirdi).")
                good = sorted.toMutableList()
            }
        }
        // birebir yinelenen kayıtlar (aynı değer + aynı GERÇEK zaman damgası)
        var dupInternal = 0
        if (anyTs) {
            val seen = HashSet<String>(); val keep = ArrayList<ImportRow>()
            for (g in good) { if (g.ts != null && !seen.add("${g.value}@${g.ts}")) dupInternal++ else keep.add(g) }
            good = keep
        }
        // mevcut geçmişle çakışma (yalnızca APPEND)
        var overlap = 0; var contained = false
        if (mode == "APPEND" && existing.isNotEmpty() && good.isNotEmpty()) {
            val ev = IntArray(existing.size) { existing[it].value }
            val nv = IntArray(good.size) { good[it].value!! }
            val maxL = minOf(nv.size, ev.size, 5000)
            var L = maxL
            while (L >= 8) {
                var ok = true
                val off = ev.size - L
                for (i in 0 until L) if (ev[off + i] != nv[i]) { ok = false; break }
                if (ok) { overlap = L; break }
                L--
            }
            if (overlap == 0 && nv.size >= 20) {
                outer@ for (s in 0..ev.size - nv.size) { if (ev[s] != nv[0]) continue; for (i in nv.indices) if (ev[s + i] != nv[i]) continue@outer; contained = true; break }
            }
            if (contained) { overlap = nv.size; notes.add("Dosya mevcut geçmişin içinde birebir bulundu — tamamı yinelenen sayıldı.") }
            else if (overlap > 0) notes.add("İlk $overlap spin mevcut geçmişin son $overlap spin'iyle birebir aynı — çakışma olarak atlandı.")
        }
        val fresh = good.drop(overlap)
        // zaman damgaları: eksik olanlar SYNTHETIC_IMPORT
        val lastTs = if (mode == "APPEND") (existing.lastOrNull()?.ts ?: 0L) else 0L
        var base = maxOf(nowMs, lastTs + 1000L) - fresh.size * 1000L
        if (base <= lastTs) base = lastTs + 1000L
        val spins = ArrayList<Spin>(fresh.size)
        var synth = 0; var prev = lastTs
        for ((i, g) in fresh.withIndex()) {
            val real = g.ts
            val t: Long; val type: String
            if (real != null && real >= prev) { t = real; type = "REAL" } else { t = maxOf(prev, base + i * 1000L); type = "SYNTHETIC_IMPORT"; synth++ }
            prev = t
            spins.add(Spin(0, g.value!!, t, "IMPORT", type))
        }
        if (synth > 0) notes.add("$synth kayıtta zaman damgası yok/geçersiz — SYNTHETIC_IMPORT olarak işaretlendi (gerçek zaman gibi gösterilmez).")
        if (mode == "NEW_DATASET") notes.add("Yeni veri seti olarak içe aktarılacak; mevcut geçmişle karıştırılmaz.")
        val dup = overlap + dupInternal
        return ImportReport(fmt, order, mode, rows.size, rows.size - bad.size, bad.size, dup, spins.size, bad, spins, overlap, synth > 0, reordered, notes)
    }

    /** Aynı değer + aynı gerçek zaman damgası → tek kayıt. */
    fun dedupeExact(spins: List<Spin>): List<Spin> {
        val seen = HashSet<String>(); val out = ArrayList<Spin>()
        for (s in spins) if (s.tsType != "REAL" || seen.add("${s.value}@${s.ts}")) out.add(s)
        return out
    }

    fun datasetHash(spins: List<Spin>): String {
        var h = Stats.FNV0
        for (s in spins) { h = Stats.fnv(h, s.value.toLong()); h = Stats.fnv(h, s.ts) }
        return java.lang.Long.toHexString(h).padStart(16, '0')
    }
}

object Exporter {
    fun txt(spins: List<Spin>): String = spins.joinToString("\n") { it.value.toString() } + "\n"
    fun csv(spins: List<Spin>): String {
        val sb = StringBuilder("seq,value,ts_ms,ts_iso,ts_type,source\n")
        for ((i, s) in spins.withIndex()) sb.append(i + 1).append(',').append(s.value).append(',').append(s.ts).append(',').append(Instant.ofEpochMilli(s.ts)).append(',').append(s.tsType).append(',').append(s.source).append('\n')
        return sb.toString()
    }
    fun json(spins: List<Spin>, meta: Map<String, Any?> = emptyMap(), extra: Map<String, Any?> = emptyMap()): String =
        Json.stringify(linkedMapOf<String, Any?>("format" to "LRAI-1", "count" to spins.size, "hash" to Importer.datasetHash(spins)).also { it.putAll(meta); it.putAll(extra) }.also {
            it["spins"] = spins.mapIndexed { i, s -> mapOf("seq" to i + 1, "value" to s.value, "ts" to s.ts, "tsType" to s.tsType, "source" to s.source) }
        })
}

/** Örnek veri seti: 2 000 SENTETİK (adil rulet, sabit seed) spin — sınıf C beklenir. */
object SampleData {
    const val SEED = 20240229L
    const val T0 = 1_767_225_600_000L   // 2026-01-01T00:00:00Z
    fun generate(n: Int = 2000, seed: Long = SEED): List<Spin> {
        val r = java.util.Random(seed)
        return List(n) { Spin(0, r.nextInt(37), T0 + it * 45_000L, "SAMPLE", "SYNTHETIC_IMPORT") }
    }
}
