package fan.lightningroulette.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.LinearLayout
import android.widget.TextView
import fan.lightningroulette.core.Candidate
import fan.lightningroulette.core.S
import fan.lightningroulette.core.TableCall
import fan.lightningroulette.core.TableCats
import fan.lightningroulette.core.Wheel
import fan.lightningroulette.engine.Engine
import fan.lightningroulette.engine.EngineUi
import fan.lightningroulette.engine.Settings
import kotlin.math.abs

/**
 * Overlay kartı (Ekran 58–68). Modlar: vertical · horizontal · compact · text · icon.
 * Kartın dışındaki dokunuşlar arkadaki uygulamaya geçer (pencere WRAP_CONTENT + NOT_TOUCH_MODAL).
 * Girdi: tek kutu (yalnızca 0–36), 3×4 klavye; DEL boşken ikinci kez basılırsa son kayıtlı spin silinir; çift ENTER yoksayılır.
 */
@SuppressLint("ViewConstructor", "SetTextI18n", "ClickableViewAccessibility")
class OverlayView(
    ctx: Context, private val st: Settings,
    private val onDrag: (dx: Int, dy: Int) -> Unit, private val onDragEnd: () -> Unit,
    private val onClose: () -> Unit, private val onMenu: (View) -> Unit
) : LinearLayout(ctx) {
    private val sc = st.ovScale / 100f
    private fun dp(v: Float) = (v * sc * resources.displayMetrics.density).toInt()
    private fun dpx(v: Float) = (v * resources.displayMetrics.density).toInt()
    private val mode = st.ovMode
    private val cardColor = Color.argb((st.ovAlpha * 255 / 100), 13, 40, 96)
    private val label = Color.parseColor("#BBDEFB")
    private val orange = Color.parseColor("#FFB74D")
    private val gold = Color.parseColor("#FFC531")
    private val green = Color.parseColor("#81C784")
    private val red = Color.parseColor("#EF5350")

    private var buf = ""
    private var armedAt = 0L
    private var lastEnter = 0L
    private var keyboardOpen = st.ovKeyboard && mode != "compact" && mode != "text"
    private var minimized = false
    private var last: EngineUi = EngineUi()
    private var info: String = ""
    private val touchSlop = ViewConfiguration.get(ctx).scaledTouchSlop
    private var lx = 0f; private var ly = 0f; private var tracking = false; private var dragging = false

    private val dragListener = OnTouchListener { _, e ->
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { lx = e.rawX; ly = e.rawY; tracking = true; dragging = false }
            MotionEvent.ACTION_MOVE -> if (tracking) {
                val dx = e.rawX - lx; val dy = e.rawY - ly
                if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) dragging = true
                if (dragging) { onDrag(dx.toInt(), dy.toInt()); lx = e.rawX; ly = e.rawY }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { val was = dragging; tracking = false; dragging = false; if (was) onDragEnd() }
        }
        dragging
    }

    init {
        orientation = VERTICAL
        setOnTouchListener(dragListener)
        render()
    }

    fun update(ui: EngineUi) {
        last = ui
        if (ui.toast != null) info = ui.toast.take(60)
        render()
    }

    fun toggleMinimize() { minimized = !minimized; render() }
    fun setKeyboard(open: Boolean) { keyboardOpen = open; render() }

    private fun vibrate() {
        if (!st.ovVibrate) return
        try {
            val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(14, VibrationEffect.DEFAULT_AMPLITUDE)) else @Suppress("DEPRECATION") v.vibrate(14)
        } catch (_: Exception) { }
    }

    private fun bg(color: Int, radius: Float, stroke: Int = 0): GradientDrawable = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat(); if (stroke != 0) setStroke(dpx(1.5f), stroke) }

    private fun tv(text: String, size: Float, color: Int = Color.WHITE, bold: Boolean = false, mono: Boolean = false): TextView = TextView(context).apply {
        this.text = text; textSize = size * sc; setTextColor(color)
        typeface = if (mono) (if (bold) Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) else Typeface.MONOSPACE) else if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        includeFontPadding = false
    }

    private fun chip(n: Int, size: Float = 22f, ring: Boolean = false): TextView = tv(n.toString(), 10f, Color.WHITE, true, true).apply {
        gravity = Gravity.CENTER
        val c = if (n == 0) Color.parseColor("#2E7D32") else if (Wheel.RED[n]) Color.parseColor("#D32F2F") else Color.parseColor("#212121")
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(c); setStroke(dpx(if (ring) 2f else 1f), if (ring) gold else Color.parseColor("#44FFFFFF")) }
        layoutParams = LayoutParams(dp(size), dp(size)).apply { marginEnd = dp(3f) }
    }

    private fun row(vararg views: View): LinearLayout = LinearLayout(context).apply {
        orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        for (v in views) addView(v)
    }

    private fun warnList(): List<String> = if (st.ovWarn) last.flags.filter { !it.startsWith("Yüksek") } else emptyList()

    /** pctMode=cal: final/kalibre dağılım; model: Kotlin meclisinin ham olasılığı. */
    private fun candidateProbability(c: Candidate): Double =
        if (st.pctMode == "model") last.pred?.pKotlin?.getOrNull(c.n) ?: c.p else c.p

    private fun tableCategoryLabel(cat: Int): String = when (cat) {
        TableCats.COLOR -> "RENK"
        TableCats.PARITY -> "TEK/ÇFT"
        TableCats.HIGHLOW -> "ALT/ÜST"
        TableCats.DOZEN -> "DÜZİNE"
        else -> "SÜTUN"
    }

    private fun tableOptionLabel(cat: Int, i: Int): String = when {
        i == TableCats.classes(cat) -> "0"
        cat == TableCats.COLOR -> if (i == 0) "KIRMIZI" else "SİYAH"
        cat == TableCats.PARITY -> if (i == 0) "ÇİFT" else "TEK"
        cat == TableCats.HIGHLOW -> if (i == 0) "1–18" else "19–36"
        cat == TableCats.DOZEN -> listOf("1–12", "13–24", "25–36")[i]
        else -> "S${i + 1}"
    }

    // ───────── başlık çubuğu: uyarı · kilit · küçült · kapat (sürükleme tutacağı)
    private fun titleBar(): View {
        val bar = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setOnTouchListener(dragListener) }
        bar.addView(tv("⚡ LR", 12f, Color.WHITE, true).apply { layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f) })
        val w = warnList()
        if (w.isNotEmpty()) bar.addView(tv("⚠", 13f, orange, true).apply { setPadding(dp(5f), 0, dp(5f), 0); setOnClickListener { info = w.take(3).joinToString(" · "); render() } })
        bar.addView(tv(if (last.pred != null) "🔒" else "⏳", 12f).apply { setPadding(dp(4f), 0, dp(4f), 0) })
        bar.addView(tv(if (minimized || mode == "compact") "▴" else "▾", 13f, label, true).apply {
            setPadding(dp(6f), dp(2f), dp(6f), dp(2f))
            setOnClickListener { if (mode == "compact" || mode == "text") { keyboardOpen = !keyboardOpen; render() } else toggleMinimize() }
        })
        bar.addView(tv("✕", 13f, label, true).apply { setPadding(dp(6f), dp(2f), dp(2f), dp(2f)); setOnClickListener { onClose() } })
        return bar
    }

    private fun nextRow(): View {
        val p = last.pred
        val box = LinearLayout(context).apply { orientation = VERTICAL }
        box.addView(tv("NEXT · P(cep) / P(k komşu aralığı)", 8.5f, label, true))
        val r = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        if (p == null) r.addView(tv("öğreniyor ${minOf(last.spinCount, last.minSample)}/${last.minSample} spin", 11f, label))
        else for ((i, c) in p.candidates.withIndex()) { if (i >= st.ovNext) break
            val col = LinearLayout(context).apply { orientation = VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(2f) } }
            col.addView(chip(c.n, 23f, i == 0))
            col.addView(tv(S.pc(candidateProbability(c) * 100, 0), 7.5f, orange, true, mono = true))
            col.addView(tv("${c.kLabel} · ${S.pc(c.mass * 100, 0)}", 6.7f, label, mono = true))
            r.addView(col)
        }
        box.addView(r)
        return box
    }

    private fun tableBox(twoCols: Boolean): View {
        val p = last.pred
        val box = LinearLayout(context).apply { orientation = VERTICAL }
        box.addView(tv("TABLE · tüm taraflar / 0 dahil", 8.5f, label, true))
        if (p == null) { box.addView(tv("—", 11f, label)); return box }
        val items = p.table.filter { (st.tableMask shr it.cat) and 1 == 1 }
        val categoryWidth = if (twoCols) 31f else 39f

        fun compactOptionLabel(c: TableCall, i: Int): String {
            if (!twoCols || i == TableCats.classes(c.cat)) return tableOptionLabel(c.cat, i)
            return when (c.cat) {
                TableCats.COLOR -> if (i == 0) "KRM" else "SYH"
                TableCats.PARITY -> if (i == 0) "ÇFT" else "TEK"
                TableCats.HIGHLOW -> if (i == 0) "1–18" else "19–36"
                TableCats.DOZEN -> listOf("1–12", "13–24", "25–36")[i]
                else -> "S${i + 1}"
            }
        }

        fun line(c: TableCall): View = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val catLabel = when {
                !twoCols -> tableCategoryLabel(c.cat)
                c.cat == TableCats.PARITY -> "TEK/Ç"
                c.cat == TableCats.HIGHLOW -> "ALT/ÜST"
                c.cat == TableCats.DOZEN -> "DÜZİNE"
                c.cat == TableCats.COLUMN -> "SÜTUN"
                else -> "RENK"
            }
            addView(tv(catLabel, if (twoCols) 6.5f else 7.5f, label, true).apply {
                gravity = Gravity.CENTER_VERTICAL
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }, LayoutParams(dp(categoryWidth), dp(31f)))
            val k = TableCats.classes(c.cat)
            for (i in 0..k) {
                val selected = i == c.cls
                val zero = i == k
                val fill = when {
                    c.cat == TableCats.COLOR && i == 0 -> Color.parseColor("#B71C1C")
                    c.cat == TableCats.COLOR && i == 1 -> Color.parseColor("#212121")
                    zero -> Color.parseColor("#2E7D32")
                    selected -> Color.argb(90, 255, 197, 49)
                    else -> Color.argb(90, 24, 36, 59)
                }
                val chip = tv("${compactOptionLabel(c, i)}\n${S.pc(c.probability(i) * 100, 0)}",
                    if (twoCols) 6.5f else 7.5f, if (selected) gold else Color.WHITE, selected, mono = !twoCols).apply {
                    gravity = Gravity.CENTER
                    maxLines = 2
                    setPadding(dp(1f), 0, dp(1f), 0)
                    background = bg(fill, 4f, if (selected) Color.parseColor("#FFFFC531") else 0)
                    layoutParams = LayoutParams(0, dp(31f), 1f).apply { setMargins(dp(0.5f), 0, dp(0.5f), 0) }
                    contentDescription = "${tableOptionLabel(c.cat, i)} ${S.pc(c.probability(i) * 100, 1)}${if (selected) ", modelin seçimi" else ""}"
                }
                addView(chip)
            }
        }

        if (twoCols) {
            val cols = LinearLayout(context).apply { orientation = HORIZONTAL }
            val a = LinearLayout(context).apply { orientation = VERTICAL; layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(5f) } }
            val b = LinearLayout(context).apply { orientation = VERTICAL; layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f) }
            for ((i, c) in items.withIndex()) (if (i < 3) a else b).addView(line(c))
            cols.addView(a); cols.addView(b); box.addView(cols)
        } else for (c in items) box.addView(line(c))
        box.addView(tv("Model: decay + son 50 + geçiş · %60 model / %40 rulet tabanı. 0 dış bahislerin dışında.", 6.8f, label))
        return box
    }

    private fun lastBox(): View {
        val box = LinearLayout(context).apply { orientation = VERTICAL }
        box.addView(tv("SON 8", 9f, label, true))
        val r = LinearLayout(context).apply { orientation = HORIZONTAL }
        if (last.last8.isEmpty()) r.addView(tv("- - - - - - - -", 11f, label, false, true))
        else for (n in last.last8) r.addView(chip(n, 19f))
        box.addView(r)
        return box
    }

    private fun inputBox(): View {
        val v = buf.toIntOrNull()
        val invalid = buf.isNotEmpty() && (v == null || v !in 0..36)
        val t = tv(if (buf.isEmpty()) "sonuç" else if (invalid) "$buf  0–36 DIŞI!" else buf, if (buf.isEmpty()) 14f else 17f, if (invalid) red else if (buf.isEmpty()) label else Color.WHITE, true, true).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(5f), 0, dp(5f))
            background = bg(if (invalid) Color.argb(70, 239, 83, 80) else Color.argb(90, 0, 0, 0), 8f, if (invalid) red else Color.parseColor("#55FFFFFF"))
        }
        return t
    }

    private fun key(k: String, wDp: Float, hDp: Float): TextView {
        val enter = k == "ENTER"
        val b = buf.toIntOrNull()
        val validNow = b != null && b in 0..36
        val col = if (enter) (if (validNow) Color.parseColor("#2E7D32") else Color.parseColor("#555B66")) else if (k == "DEL") Color.parseColor("#37474F") else Color.parseColor("#1565C0")
        return tv(if (k == "ENTER") "↵" else k, if (k.length > 1 && k != "ENTER") 11f else 15f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = bg(col, 7f)
            layoutParams = LayoutParams(dp(wDp), dp(hDp)).apply { setMargins(dp(1.5f), dp(1.5f), dp(1.5f), dp(1.5f)) }
            setOnClickListener {
                vibrate()
                when (k) {
                    "DEL" -> if (buf.isNotEmpty()) { buf = buf.dropLast(1); info = "" }
                           else {
                               val now = System.currentTimeMillis()
                               if (now - armedAt < 3000) { armedAt = 0; Engine.undo(); info = "son spin geri alındı" }
                               else if (last.spinCount > 0) { armedAt = now; info = "silmek için 3 sn içinde DEL’e tekrar bas" }
                           }
                    "ENTER" -> {
                        val now = System.currentTimeMillis()
                        if (validNow) {
                            if (now - lastEnter > 450) { lastEnter = now; Engine.enter(b!!, last.spinCount); buf = ""; info = "" }
                            else info = "çift ENTER yoksayıldı"
                        }
                    }
                    else -> { var t = buf + k; if (t.length == 2 && t[0] == '0') t = t.substring(1); if (t.length <= 2) buf = t; info = "" }
                }
                render()
            }
        }
    }

    private fun keypad(wDp: Float, hDp: Float): View {
        val g = LinearLayout(context).apply { orientation = VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        for (r in listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("DEL", "0", "ENTER"))) {
            val rr = LinearLayout(context).apply { orientation = HORIZONTAL }
            for (k in r) rr.addView(key(k, wDp, hDp))
            g.addView(rr)
        }
        return g
    }

    private fun footer(): View {
        val parts = ArrayList<String>()
        if (info.isNotEmpty()) parts.add(info)
        else {
            if (last.pyStatus == "ERROR") parts.add("Kotlin-only (Python ERROR)") else if (last.pyStatus == "OFF") parts.add("Kotlin-only")
            if (last.pred == null) parts.add("öğreniyor ${minOf(last.spinCount, last.minSample)}/${last.minSample}")
            if (last.pred != null) parts.add(last.pred!!.code)
        }
        return tv(parts.joinToString(" · "), 8.5f, if (info.isNotEmpty()) gold else label)
    }

    private fun pad(v: View, top: Float = 3f): View { v.setPadding(0, dp(top), 0, 0); return v }

    private fun render() {
        removeAllViews()
        if (mode == "icon") { renderIcon(); return }
        background = bg(cardColor, 12f, Color.parseColor("#331976D2"))
        setPadding(dp(8f), dp(6f), dp(8f), dp(6f))
        val horizontal = mode == "horizontal"
        val widthDp = if (horizontal) 430f else if (mode == "text") 250f else 190f
        addView(titleBar())
        if (minimized && mode != "compact" && mode != "text") { addView(pad(footer())); minWidth(widthDp); return }
        when {
            mode == "text" -> {
                val p = last.pred
                val t = StringBuilder()
                if (p == null) t.append("NEXT: öğreniyor ${minOf(last.spinCount, last.minSample)}/${last.minSample}\n")
                else {
                    t.append("NEXT (cep% / k-aralık%): ")
                        .append(p.candidates.take(st.ovNext).joinToString("  ") { c ->
                            "${c.n} ${S.pc(candidateProbability(c) * 100, 0)} ${c.kLabel}/${S.pc(c.mass * 100, 0)}"
                        }).append('\n')
                    t.append("TABLE · tüm alternatifler:\n")
                    for (c in p.table) if ((st.tableMask shr c.cat) and 1 == 1) {
                        t.append("  ").append(tableCategoryLabel(c.cat)).append(' ')
                        for (i in 0..TableCats.classes(c.cat)) {
                            t.append(tableOptionLabel(c.cat, i)).append(':').append(S.pc(c.probability(i) * 100, 0)).append(' ')
                        }
                        t.append("★ ").append(c.pick).append('\n')
                    }
                    t.append("Table yüzdesi: decay + son 50 + geçiş; %60 model/%40 rulet tabanı. 0 ayrı.\n")
                }
                t.append("SON 8: ").append(if (last.last8.isEmpty()) "-" else last.last8.joinToString(" | "))
                addView(pad(tv(t.toString(), 11f, Color.WHITE, false, true)))
                if (keyboardOpen) { addView(pad(inputBox(), 4f)); addView(pad(keypad(52f, 28f), 2f)) }
                addView(pad(footer()))
            }
            horizontal -> {
                val body = LinearLayout(context).apply { orientation = HORIZONTAL }
                val left = LinearLayout(context).apply { orientation = VERTICAL; layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8f) } }
                left.addView(nextRow()); left.addView(pad(tableBox(true))); left.addView(pad(lastBox())); left.addView(pad(inputBox(), 4f))
                val right = LinearLayout(context).apply { orientation = VERTICAL; layoutParams = LayoutParams(dp(124f), LayoutParams.WRAP_CONTENT); gravity = Gravity.CENTER_HORIZONTAL }
                right.addView(keypad(38f, 26f))
                body.addView(left); body.addView(right)
                addView(pad(body)); addView(pad(footer()))
            }
            else -> { // vertical / compact
                addView(pad(nextRow())); addView(pad(tableBox(false))); addView(pad(lastBox()))
                if (mode != "compact" || keyboardOpen) { addView(pad(inputBox(), 4f)); addView(pad(keypad(56f, 30f), 2f)) }
                else addView(pad(tv("⌨ klavyeyi aç (▴)", 10f, label)))
                addView(pad(footer()))
            }
        }
        minWidth(widthDp)
    }

    private fun minWidth(widthDp: Float) { minimumWidth = dp(widthDp) }

    private fun renderIcon() {
        val p = last.pred
        val ring = if (last.pyStatus == "ERROR") red else if (warnList().isNotEmpty()) orange else if (p == null) Color.parseColor("#667085") else Color.parseColor("#42A5F5")
        val b = tv("⚡", 20f, gold, true).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.argb((st.ovAlpha * 255 / 100), 13, 40, 96)); setStroke(dpx(3f), ring) }
            layoutParams = LayoutParams(dp(48f), dp(48f))
            setOnLongClickListener { onMenu(this); true }
            setOnClickListener { st.ovMode = "compact" }
            setOnTouchListener(dragListener)
        }
        background = null; setPadding(0, 0, 0, 0)
        addView(b)
    }
}
