package fan.lightningroulette.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fan.lightningroulette.core.Sec
import fan.lightningroulette.core.jbool
import fan.lightningroulette.core.jdoubles
import fan.lightningroulette.core.jint
import fan.lightningroulette.core.jlist
import fan.lightningroulette.core.jnum
import fan.lightningroulette.core.jstr
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

internal fun DrawScope.txt(s: String, x: Float, y: Float, sizeSp: Float, color: Color, align: Paint.Align = Paint.Align.LEFT, bold: Boolean = false) {
    val p = Paint()
    p.isAntiAlias = true
    p.textSize = sizeSp.sp.toPx()
    p.color = color.toArgb()
    p.textAlign = align
    p.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    drawContext.canvas.nativeCanvas.drawText(s, x, y, p)
}

private fun strList(a: Any?): List<String> = a.jlist().map { it.jstr() }
private fun rows(a: Any?): List<List<String>> = a.jlist().map { r -> r.jlist().map { it.jstr() } }

/** LAB sekmelerinin tek tip çizicisi: Sec.type'a göre kart çizer. */
@Composable
fun SecView(s: Sec) {
    when (s.type) {
        "tiles" -> TilesSec(s)
        "kv" -> KvSec(s)
        "table" -> TableSec(s)
        "bars" -> BarsSec(s)
        "forest" -> ForestSec(s)
        "heat" -> HeatSec(s)
        "line" -> LineSec(s)
        "reliab" -> ReliabSec(s)
        "gauge" -> GaugeSec(s)
        "flags" -> FlagsSec(s)
        "pbars" -> PBarsSec(s)
        else -> TextSec(s)
    }
}

@Composable
private fun TilesSec(s: Sec) {
    val items = rows(s.d["items"])
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        for (pair in items.chunked(2)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                for ((i, it) in pair.withIndex()) {
                    val tone = it.getOrElse(3) { "" }
                    Column(Modifier.weight(1f).padding(end = if (i == 0) 6.dp else 0.dp).clip(RoundedCornerShape(12.dp)).background(C.card).padding(10.dp)) {
                        T(it.getOrElse(0) { "" }, C.dim, 11)
                        T(it.getOrElse(1) { "" }, if (tone.isEmpty()) C.text else C.tone(tone), 18, true, true)
                        T(it.getOrElse(2) { "" }, C.dim2, 10)
                    }
                }
                if (pair.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun KvSec(s: Sec) {
    LrCard(title = s.title) {
        for (r in rows(s.d["rows"])) KV(r.getOrElse(0) { "" }, r.getOrElse(1) { "" }, r.getOrElse(2) { "" }, r.getOrElse(3) { "" })
        val note = s.d["note"].jstr()
        if (note.isNotEmpty()) T(note, C.dim2, 11, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun TableSec(s: Sec) {
    val head = strList(s.d["head"]); val body = rows(s.d["rows"]); val al = s.d["al"].jstr()
    val dim = s.d["dim"].jlist().map { it.jint() }.toSet()
    LrCard(title = s.title) {
        val cols = max(head.size, 1)
        fun weight(i: Int): Float = if (i == 0) (if (cols > 4) 2.2f else 1.8f) else 1f
        fun talign(i: Int): TextAlign = when (al.getOrNull(i)) { 'r' -> TextAlign.End; 'c' -> TextAlign.Center; else -> TextAlign.Start }
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) { for ((i, h) in head.withIndex()) T(h, C.dim, 10, true, align = talign(i), modifier = Modifier.weight(weight(i))) }
        Divider1()
        for ((ri, r) in body.withIndex()) {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                for (i in 0 until cols) {
                    val cell = r.getOrElse(i) { "" }
                    val col = if (ri in dim) C.dim2 else if (cell == "S" || cell == "Düşük sample" || cell.startsWith("Overfit")) C.warn else C.text
                    T(cell, col, 11, i > 0 && cell.length < 12, i > 0, talign(i), Modifier.weight(weight(i)))
                }
            }
        }
        val note = s.d["note"].jstr()
        if (note.isNotEmpty()) T(note, C.dim2, 11, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun BarsSec(s: Sec) {
    val labels = strList(s.d["labels"]); val vals = s.d["values"].jdoubles(); val exp = s.d["expected"].jdoubles(); val unit = s.d["unit"].jstr()
    LrCard(title = s.title) {
        if (vals.isEmpty()) { T("veri yok", C.dim); return@LrCard }
        val top = max(vals.max(), if (exp.isEmpty()) 0.0 else exp.max()) * 1.18 + 1e-9
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            val left = 34.dp.toPx(); val bottom = 22.dp.toPx(); val topPad = 6.dp.toPx()
            val ph = size.height - bottom - topPad; val pw = size.width - left - 4.dp.toPx(); val bw = pw / vals.size
            for (t in 0..3) {
                val v = top * t / 3; val y = topPad + ph - (v / top * ph).toFloat()
                drawLine(C.line, Offset(left, y), Offset(size.width, y), 1f)
                txt(if (top >= 20) v.toInt().toString() + unit else String.format(java.util.Locale.US, "%.1f", v).replace('.', ',') + unit, left - 4.dp.toPx(), y + 3.dp.toPx(), 9f, C.dim2, Paint.Align.RIGHT)
            }
            for (i in vals.indices) {
                val x = left + i * bw + bw * 0.14f; val w = bw * 0.72f
                val h = (vals[i] / top * ph).toFloat()
                drawRect(C.blueDeep, Offset(x, topPad + ph - h), Size(w, h))
                if (i < exp.size) { val ey = topPad + ph - (exp[i] / top * ph).toFloat(); drawLine(C.gold, Offset(x - 2.dp.toPx(), ey), Offset(x + w + 2.dp.toPx(), ey), 2.dp.toPx()) }
                if (vals.size <= 20 || i % 2 == 0) txt(labels.getOrElse(i) { "" }, x + w / 2, size.height - 6.dp.toPx(), 9f, C.dim, Paint.Align.CENTER)
            }
        }
        val note = s.d["note"].jstr()
        if (note.isNotEmpty()) T(note, C.dim2, 11)
    }
}

@Composable
private fun PBarsSec(s: Sec) {
    val labels = strList(s.d["labels"]); val v = s.d["v"].jdoubles(); val mx = s.d["mx"].jdoubles(); val right = strList(s.d["right"])
    LrCard(title = s.title) {
        for (i in labels.indices) {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                T(labels[i], C.dim, 11, modifier = Modifier.weight(1.4f))
                Box(Modifier.weight(2f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(C.card2)) {
                    val f = if (mx.getOrElse(i) { 1.0 } <= 0) 0f else (v.getOrElse(i) { 0.0 } / mx.getOrElse(i) { 1.0 }).toFloat().coerceIn(0f, 1f)
                    Box(Modifier.fillMaxWidth(f).height(8.dp).background(C.blue))
                }
                T(right.getOrElse(i) { "" }, C.text, 11, true, true, TextAlign.End, Modifier.weight(0.9f))
            }
        }
    }
}

@Composable
private fun ForestSec(s: Sec) {
    val labels = strList(s.d["labels"]); val dl = s.d["delta"].jdoubles(); val lo = s.d["lo"].jdoubles(); val hi = s.d["hi"].jdoubles()
    val range = max(0.5, s.d["range"].jnum(6.0))
    LrCard(title = s.title) {
        val rh = 26
        Canvas(Modifier.fillMaxWidth().height((labels.size * rh + 22).dp)) {
            val left = 92.dp.toPx(); val right = size.width - 8.dp.toPx(); val pw = right - left
            fun x(v: Double): Float = left + ((v + range) / (2 * range)).toFloat().coerceIn(0f, 1f) * pw
            for (t in -2..2) { val v = range * t / 2; drawLine(C.line, Offset(x(v), 0f), Offset(x(v), size.height - 16.dp.toPx()), 1f); txt(String.format(java.util.Locale.US, "%.0f", v), x(v), size.height - 4.dp.toPx(), 9f, C.dim2, Paint.Align.CENTER) }
            drawLine(C.gold, Offset(x(0.0), 0f), Offset(x(0.0), size.height - 16.dp.toPx()), 2.dp.toPx())
            for (i in labels.indices) {
                val cy = (i * rh + rh / 2).dp.toPx()
                txt(labels[i], 0f, cy + 4.dp.toPx(), 11f, C.dim)
                val sig = lo[i] > 0 || hi[i] < 0
                val col = if (sig) C.warn else C.blue
                drawLine(col.copy(alpha = 0.7f), Offset(x(lo[i]), cy), Offset(x(hi[i]), cy), 2.dp.toPx())
                drawCircle(col, 4.dp.toPx(), Offset(x(dl[i]), cy))
            }
        }
        val note = s.d["note"].jstr()
        if (note.isNotEmpty()) T(note, C.dim2, 11)
    }
}

@Composable
private fun HeatSec(s: Sec) {
    val rowsL = strList(s.d["rows"]); val colsL = strList(s.d["cols"])
    val v = s.d["v"].jlist().map { it.jdoubles() }
    val vmin = s.d["vmin"].jnum(); val vmax = s.d["vmax"].jnum(1.0); val div = s.d["div"].jbool(); val dec = s.d["dec"].jint(1)
    val marks = s.d["mark"].jlist().map { it.jlist().map { x -> x.jint() } }.map { it[0] to it[1] }.toSet()
    LrCard(title = s.title) {
        val cellH = 24; val many = colsL.size > 14
        Canvas(Modifier.fillMaxWidth().height((rowsL.size * cellH + 26).dp)) {
            val left = if (rowsL.size == 1 && rowsL[0].length <= 5) 40.dp.toPx() else 54.dp.toPx(); val top = 18.dp.toPx()
            val cw = (size.width - left) / max(1, colsL.size); val ch = cellH.dp.toPx()
            for (c in colsL.indices) if (!many || c % 3 == 0 || colsL.size <= 37) txt(colsL[c], left + c * cw + cw / 2, 12.dp.toPx(), if (many) 6.5f else 9f, C.dim, Paint.Align.CENTER)
            for (r in rowsL.indices) {
                txt(rowsL[r], 0f, top + r * ch + ch * 0.62f, 10f, C.dim)
                for (c in colsL.indices) {
                    val x = v.getOrNull(r)?.getOrNull(c) ?: 0.0
                    val col = if (div) {
                        val m = max(abs(vmin), abs(vmax)).coerceAtLeast(1e-9); val t = (x / m).toFloat().coerceIn(-1f, 1f)
                        if (t >= 0) lerp(Color(0xFF1B2740), Color(0xFF2E9E5B), t) else lerp(Color(0xFF1B2740), Color(0xFFC0443F), -t)
                    } else {
                        val t = if (vmax - vmin <= 1e-12) 0f else ((x - vmin) / (vmax - vmin)).toFloat().coerceIn(0f, 1f)
                        lerp(Color(0xFF16233D), Color(0xFF42A5F5), t)
                    }
                    drawRect(col, Offset(left + c * cw + 1f, top + r * ch + 1f), Size(cw - 2f, ch - 2f))
                    if (!many) txt(String.format(java.util.Locale.US, "%.${dec}f", x).replace('.', ','), left + c * cw + cw / 2, top + r * ch + ch * 0.64f, 9f, Color.White, Paint.Align.CENTER)
                    if ((r to c) in marks) drawRect(C.gold, Offset(left + c * cw + 1f, top + r * ch + 1f), Size(cw - 2f, ch - 2f), style = Stroke(2.dp.toPx()))
                }
            }
        }
        val note = s.d["note"].jstr()
        if (note.isNotEmpty()) T(note, C.dim2, 11)
    }
}

private fun lerp(a: Color, b: Color, t: Float): Color = Color(a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, 1f)

@Composable
private fun LineSec(s: Sec) {
    val labels = strList(s.d["labels"]); val series = s.d["series"].jlist().map { it.jdoubles() }
    val h = s.d["hline"]; val ymin0 = s.d["ymin"]; val ymax0 = s.d["ymax"]
    LrCard(title = s.title) {
        val all = series.flatMap { it.toList() }
        if (all.isEmpty()) { T("veri yok", C.dim); return@LrCard }
        var lo = ymin0?.jnum() ?: all.min(); var hi = ymax0?.jnum() ?: all.max()
        if (h != null) { lo = min(lo, h.jnum()); hi = max(hi, h.jnum()) }
        if (hi - lo < 1e-9) { hi += 1.0; lo -= 1.0 }
        val pad = (hi - lo) * 0.08; lo -= pad; hi += pad
        Canvas(Modifier.fillMaxWidth().height(130.dp)) {
            val left = 36.dp.toPx(); val bottom = 20.dp.toPx(); val top = 6.dp.toPx(); val ph = size.height - bottom - top; val pw = size.width - left - 6.dp.toPx()
            fun y(v: Double): Float = top + ph - ((v - lo) / (hi - lo)).toFloat() * ph
            for (t in 0..3) { val v = lo + (hi - lo) * t / 3; drawLine(C.line, Offset(left, y(v)), Offset(size.width, y(v)), 1f); txt(String.format(java.util.Locale.US, "%.2f", v).replace('.', ','), left - 3.dp.toPx(), y(v) + 3.dp.toPx(), 8.5f, C.dim2, Paint.Align.RIGHT) }
            if (h != null) drawLine(C.gold, Offset(left, y(h.jnum())), Offset(size.width, y(h.jnum())), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
            for ((si, sr) in series.withIndex()) {
                val col = if (si == 0) C.blue else C.purple
                for (i in 1 until sr.size) drawLine(col, Offset(left + (i - 1) * pw / max(1, sr.size - 1), y(sr[i - 1])), Offset(left + i * pw / max(1, sr.size - 1), y(sr[i])), 2.dp.toPx())
                for (i in sr.indices) drawCircle(col, 2.5.dp.toPx(), Offset(left + i * pw / max(1, sr.size - 1), y(sr[i])))
            }
            for (i in labels.indices) if (labels.size <= 12 || i % max(1, labels.size / 8) == 0) txt(labels[i], left + i * pw / max(1, labels.size - 1), size.height - 5.dp.toPx(), 8.5f, C.dim, Paint.Align.CENTER)
        }
        val note = s.d["note"].jstr()
        if (note.isNotEmpty()) T(note, C.dim2, 11)
    }
}

@Composable
private fun ReliabSec(s: Sec) {
    val px = s.d["px"].jdoubles(); val py = s.d["py"].jdoubles(); val n = s.d["n"].jlist().map { it.jint() }
    LrCard(title = s.title) {
        if (px.isEmpty()) { T("Bu eksende yeterli örnek yok.", C.dim); return@LrCard }
        val mx = max(0.05, max(px.max(), py.max()) * 1.15)
        Canvas(Modifier.fillMaxWidth().height(200.dp)) {
            val side = min(size.width, size.height) - 24.dp.toPx(); val ox = 28.dp.toPx(); val oy = 4.dp.toPx()
            fun X(v: Double) = ox + (v / mx).toFloat().coerceIn(0f, 1f) * side
            fun Y(v: Double) = oy + side - (v / mx).toFloat().coerceIn(0f, 1f) * side
            drawRect(C.line, Offset(ox, oy), Size(side, side), style = Stroke(1f))
            drawLine(C.gold, Offset(X(0.0), Y(0.0)), Offset(X(mx), Y(mx)), 2.dp.toPx())
            val maxN = max(1, n.maxOrNull() ?: 1)
            for (i in px.indices) drawCircle(C.blue, (3 + 7 * sqrt(n.getOrElse(i) { 1 }.toDouble() / maxN)).toFloat().dp.toPx(), Offset(X(px[i]), Y(py[i])))
            txt("tahmin", ox + side / 2, oy + side + 16.dp.toPx(), 9f, C.dim, Paint.Align.CENTER)
            txt("0", ox - 4.dp.toPx(), oy + side + 3.dp.toPx(), 9f, C.dim2, Paint.Align.RIGHT)
            txt(String.format(java.util.Locale.US, "%.2f", mx).replace('.', ','), ox - 4.dp.toPx(), oy + 9.dp.toPx(), 9f, C.dim2, Paint.Align.RIGHT)
        }
        val note = s.d["note"].jstr()
        if (note.isNotEmpty()) T(note, C.dim2, 11)
    }
}

@Composable
private fun GaugeSec(s: Sec) {
    val v = s.d["value"].jint(); val cls = s.d["cls"].jstr()
    LrCard(title = s.title) {
        Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
            val col = if (v >= 70) C.ok else if (v >= 40) C.blue else C.warn
            Canvas(Modifier.size(150.dp)) {
                val st = 14.dp.toPx(); val sz = Size(size.width - st, size.height - st); val tl = Offset(st / 2, st / 2)
                drawArc(C.card2, 135f, 270f, false, tl, sz, style = Stroke(st))
                drawArc(col, 135f, 270f * (v / 100f), false, tl, sz, style = Stroke(st))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                T("$v", col, 34, true, true)
                T("/ 100", C.dim, 11)
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) { ClassBadge(cls); HGap(6); T("sınıf $cls", C.dim, 11) }
            }
        }
        T(s.d["note"].jstr(), C.dim2, 11)
    }
}

@Composable
private fun FlagsSec(s: Sec) {
    LrCard(title = s.title) {
        for (t in strList(s.d["items"])) Row(Modifier.padding(vertical = 3.dp)) { T("⚠ ", C.warn, 12, true); T(t, C.text, 12) }
    }
}

@Composable
private fun TextSec(s: Sec) {
    val tone = s.d["tone"].jstr()
    val body = s.d["body"].jstr()
    if (s.title.isNotEmpty() && tone.isEmpty()) {
        LrCard(title = s.title) { T(body, C.text, 12) }
    } else {
        if (s.title.isNotEmpty()) T(s.title, C.dim, 11, true, modifier = Modifier.padding(top = 6.dp, start = 2.dp))
        Banner(body, tone.ifEmpty { "info" })
    }
}
