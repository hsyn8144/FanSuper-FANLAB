package fan.lightningroulette.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import fan.lightningroulette.core.Candidate
import fan.lightningroulette.core.Regions
import fan.lightningroulette.core.Sectors
import fan.lightningroulette.core.Wheel
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

val RANK_COLORS = listOf(Color(0xFFFFC531), Color(0xFF42A5F5), Color(0xFFB388FF), Color(0xFF26C6DA), Color(0xFFFF8A65))
val REGION_COLORS = listOf(Color(0xFF3F6FB5), Color(0xFF3E9B6A), Color(0xFFC98A2B))

/** Fiziksel çark (Ekran 13): gerçek wheel sırası, aday merkez + komşu yayı, sektörler, bölgeler, SON 8. */
@Composable
fun WheelView(
    candidates: List<Candidate>, last8: List<Int>, sectors: Sectors,
    showCand: Boolean = true, showSector: Boolean = true, showRegion: Boolean = true, showLast: Boolean = true,
    selected: Int = -1, onSelect: (Int) -> Unit = {}, modifier: Modifier = Modifier
) {
    val n = Wheel.N
    val step = 360f / n
    Canvas(
        modifier.fillMaxWidth().aspectRatio(1f).pointerInput(Unit) {
            detectTapGestures { off ->
                val cx = size.width / 2f; val cy = size.height / 2f
                val ang = Math.toDegrees(atan2((off.y - cy).toDouble(), (off.x - cx).toDouble())).toFloat()
                val idx = Math.floorMod(Math.round((ang + 90f) / step), n)
                onSelect(Wheel.ORDER[idx])
            }
        }
    ) {
        val cx = size.width / 2f; val cy = size.height / 2f
        val rOut = size.width / 2f - 26.dp.toPx()
        val ringW = 30.dp.toPx()
        val rMid = rOut - ringW / 2f
        fun ang(i: Int): Float = -90f + i * step
        fun pt(r: Float, deg: Float): Offset { val a = deg * PI.toFloat() / 180f; return Offset(cx + r * cos(a), cy + r * sin(a)) }

        // cepler
        for (i in 0 until n) {
            val num = Wheel.ORDER[i]
            drawArc(C.pocket(num), ang(i) - step / 2 + 0.4f, step - 0.8f, false, Offset(cx - rMid, cy - rMid), Size(rMid * 2, rMid * 2), style = Stroke(ringW))
            val p = pt(rMid, ang(i))
            txt(num.toString(), p.x, p.y + 3.5.dp.toPx(), 9.5f, Color.White, Paint.Align.CENTER, true)
            if (num == selected) drawCircle(C.gold, ringW * 0.48f, p, style = Stroke(2.dp.toPx()))
        }
        // bölgeler (iç ince halka)
        if (showRegion) {
            val rr = rOut - ringW - 5.dp.toPx()
            for (i in 0 until n) drawArc(REGION_COLORS[Regions.of[Wheel.ORDER[i]]], ang(i) - step / 2, step, false, Offset(cx - rr, cy - rr), Size(rr * 2, rr * 2), style = Stroke(4.dp.toPx()))
        }
        // sektör çizgileri ve etiketleri
        if (showSector) {
            for (s in 0 until sectors.count) {
                val b = sectors.bounds[s]
                val a0 = ang(b) - step / 2
                drawLine(Color(0xAAFFFFFF), pt(rOut - ringW - 3.dp.toPx(), a0), pt(rOut + 4.dp.toPx(), a0), 1.5.dp.toPx())
                val mid = ang(b) - step / 2 + (sectors.bounds[s + 1] - b) * step / 2
                val lp = pt(rOut + 15.dp.toPx(), mid)
                txt("S${s + 1}", lp.x, lp.y + 3.5.dp.toPx(), 10f, C.dim, Paint.Align.CENTER, true)
            }
        }
        // adaylar: komşu yayı + merkez halka + rank rozeti
        if (showCand) {
            for ((ri, c) in candidates.withIndex()) {
                val col = RANK_COLORS[ri % RANK_COLORS.size]
                val span = c.span
                val first = Wheel.POS[span[0]]
                val rr = rOut + 5.dp.toPx() + ri * 2.2.dp.toPx()
                drawArc(col, ang(first) - step / 2, span.size * step, false, Offset(cx - rr, cy - rr), Size(rr * 2, rr * 2), style = Stroke(3.5.dp.toPx()))
                val cp = pt(rMid, ang(Wheel.POS[c.n]))
                drawCircle(col, ringW * 0.5f, cp, style = Stroke(3.dp.toPx()))
                val bp = pt(rOut - ringW - 20.dp.toPx(), ang(Wheel.POS[c.n]))
                drawCircle(col, 9.dp.toPx(), bp)
                txt("${ri + 1}", bp.x, bp.y + 4.dp.toPx(), 11f, Color(0xFF111111), Paint.Align.CENTER, true)
            }
        }
        // SON 8 (1 = en yeni)
        if (showLast) {
            for ((k, num) in last8.withIndex()) {
                val p = pt(rOut - ringW - 12.dp.toPx() - (if (showRegion) 6.dp.toPx() else 0f), ang(Wheel.POS[num]))
                drawCircle(Color.White, 7.dp.toPx(), p)
                txt("${k + 1}", p.x, p.y + 3.5.dp.toPx(), 9f, Color(0xFF111111), Paint.Align.CENTER, true)
            }
        }
        txt(if (last8.isNotEmpty()) "son: ${last8[0]}" else "⚡", cx, cy + 5.dp.toPx(), 15f, C.text, Paint.Align.CENTER, true)
    }
}
