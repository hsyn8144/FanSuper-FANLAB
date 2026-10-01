package fan.superai.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.LinearLayout
import android.widget.TextView
import fan.superai.Echo
import fan.superai.data.AppSettings
import fan.superai.engine.EngineState
import kotlin.math.abs

/**
 * Onaylanan v9.6 mavi kartı, Kalıp satırı olmadan:
 *   🔵 K      Kotlin rakam tahmini
 *   🐍 Py     Python rakam tahmini
 *   🔵 Yan    Kotlin yan tahmini
 *   🐍 PyYan  Python yan tahmini
 *   son 6 sayı (en yeni solda)
 *   DEL / 4 / 3 / 2 / 1 (alt alta)
 *
 * Yatay ayarı korunur: aynı dört satır solda, düğmeler sağda.
 * Meclis/hakem detayları uygulamada kalır. Tahmin alanı ve kart kenarları
 * sürüklenebilir; veri düğmeleri sürükleme dinleyicisine bağlanmaz.
 */
@SuppressLint("ViewConstructor", "SetTextI18n", "ClickableViewAccessibility")
class OverlayView(
    ctx: Context,
    private val s: AppSettings,
    private val onNumber: (Int) -> Unit,
    private val onDelete: () -> Unit,
    private val onDrag: (dx: Int, dy: Int) -> Unit
) : LinearLayout(ctx) {
    private val sc = s.overlayTextScale
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun scaledDp(v: Int) = dp((v * sc).toInt())
    private val labelColor = Color.parseColor("#BBDEFB")
    private val confColor = Color.parseColor("#FFB74D")
    private val green = Color.parseColor("#81C784")
    private val purple = Color.parseColor("#CE93D8")

    private data class PredictionRow(val value: TextView, val confidence: TextView)
    private lateinit var kotlinNumber: PredictionRow
    private lateinit var pythonNumber: PredictionRow
    private lateinit var kotlinSide: PredictionRow
    private lateinit var pythonSide: PredictionRow
    private var recent: TextView? = null
    private lateinit var status: TextView
    private val recentEmpty = "- - - - - -"

    private val touchSlop = ViewConfiguration.get(ctx).scaledTouchSlop
    private var lastX = 0f
    private var lastY = 0f
    private var tracking = false
    private var dragging = false
    // Dinleyici, init içinde görünümler kurulmadan önce oluşturulmalı.
    private val dragListener = OnTouchListener { v, e ->
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = e.rawX; lastY = e.rawY
                tracking = true; dragging = false
            }
            MotionEvent.ACTION_MOVE -> if (tracking) {
                val dx = e.rawX - lastX
                val dy = e.rawY - lastY
                if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) dragging = true
                if (dragging) {
                    onDrag(dx.toInt(), dy.toInt())
                    lastX = e.rawX; lastY = e.rawY
                }
            }
            MotionEvent.ACTION_UP -> {
                if (tracking && !dragging) v.performClick()
                tracking = false; dragging = false
            }
            MotionEvent.ACTION_CANCEL -> { tracking = false; dragging = false }
        }
        true
    }

    init {
        orientation = if (s.overlayHorizontal) HORIZONTAL else VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        // Sayılar, RTL telefonlarda da en yenisi solda kalacak şekilde gösterilir.
        layoutDirection = LAYOUT_DIRECTION_LTR
        background = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(Color.argb((s.overlayAlpha * 255).toInt(), 0x15, 0x65, 0xC0))
        }
        setPadding(dp(10), dp(10), dp(10), dp(10))
        setOnTouchListener(dragListener)
        val panel = buildPredictionPanel()
        if (s.overlayHorizontal) {
            addView(panel, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            addView(buttons(true), LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                leftMargin = dp(8)
            })
        } else {
            addView(panel, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            // Ağırlık kısa ekranlarda düğme grubunun daralmasını sağlar. Dört tahmin
            // satırı yerinde kalır, alttaki 1 düğmesi ekran dışına itilmez.
            addView(buttons(false), LayoutParams(LayoutParams.MATCH_PARENT, scaledDp(220), 1f))
        }
    }

    /** Sabit kompakt genişlik: tahmin değiştikçe kartın eni/konumu oynamaz. */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val dm = resources.displayMetrics
        fun limit(spec: Int, screen: Int): Int = if (MeasureSpec.getMode(spec) == MeasureSpec.UNSPECIFIED)
            screen else minOf(screen, MeasureSpec.getSize(spec))
        val width = minOf(scaledDp(if (s.overlayHorizontal) 380 else 148), limit(widthMeasureSpec, dm.widthPixels))
        val height = limit(heightMeasureSpec, dm.heightPixels)
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST)
        )
    }

    private fun tv(text: String, size: Float, color: Int, mono: Boolean = false) = TextView(context).apply {
        this.text = text
        textSize = size * sc
        setTextColor(color)
        typeface = if (mono) Typeface.MONOSPACE else Typeface.DEFAULT
        isSingleLine = true
        includeFontPadding = false
        ellipsize = TextUtils.TruncateAt.END
    }

    private fun LinearLayout.predictionRow(
        key: String, label: String, description: String, labelColor: Int, valueColor: Int, confidenceColor: Int
    ): PredictionRow {
        val value = tv("--", 15f, valueColor, mono = true).apply { tag = "$key.value" }
        val confidence = tv("--", 10f, confidenceColor, mono = true).apply {
            tag = "$key.confidence"
            gravity = Gravity.END
        }
        addView(LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isBaselineAligned = false
            setPadding(0, dp(1), 0, dp(1))
            contentDescription = description
            addView(tv(label, 10f, labelColor).apply {
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 0, dp(6), 0)
            })
            addView(value, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            addView(confidence)
            setOnTouchListener(dragListener)
        // Emoji yedek fontlarının satır yüksekliğini şişirip veri düğmelerini
        // ezmesini önle. 15sp değer + dikey boşluk, kullanıcı ölçeğiyle birlikte büyür.
        }, LayoutParams(LayoutParams.MATCH_PARENT, scaledDp(24)))
        return PredictionRow(value, confidence)
    }

    private fun buildPredictionPanel() = LinearLayout(context).apply {
        orientation = VERTICAL
        setOnTouchListener(dragListener)
        kotlinNumber = predictionRow("kotlin.number", "🔵 K", "Kotlin rakam tahmini",
            Color.parseColor("#64B5F6"), Color.WHITE, confColor)
        pythonNumber = predictionRow("python.number", "🐍 Py", "Python rakam tahmini",
            green, Color.parseColor("#A5D6A7"), green)
        kotlinSide = predictionRow("kotlin.side", "🔵 Yan", "Kotlin yan tahmini", purple, purple, purple)
        pythonSide = predictionRow("python.side", "🐍 PyYan", "Python yan tahmini",
            green, Color.parseColor("#A5D6A7"), green)
        status = tv("", 9f, confColor).apply {
            gravity = Gravity.CENTER
            visibility = GONE
            setOnTouchListener(dragListener)
        }
        addView(status, LayoutParams(LayoutParams.MATCH_PARENT, scaledDp(16)))
        if (s.showRecent) {
            recent = tv(recentEmpty, 9f, labelColor, mono = true).apply {
                tag = "recent"
                gravity = Gravity.CENTER
                contentDescription = "Son 6 veri, en yeni solda"
                setPadding(0, dp(4), 0, dp(6))
                setOnTouchListener(dragListener)
            }
            addView(recent, LayoutParams(LayoutParams.MATCH_PARENT, scaledDp(24)))
        }
    }

    private fun button(label: String, color: String, compact: Boolean, action: () -> Unit) =
        tv(label, if (label == "DEL" || label == "⌫") 12f else 14f, Color.WHITE).apply {
            gravity = Gravity.CENTER
            // Eski karttaki tam genişlikli, düz renkli düğmeler.
            setBackgroundColor(Color.parseColor(color))
            isClickable = true
            isFocusable = true
            contentDescription = if (label == "DEL" || label == "⌫") "Son veriyi sil" else "$label ekle"
            setOnClickListener {
                if (s.vibrate) performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                action()
            }
            if (compact) textSize = 12f * sc
            setOnTouchListener { v, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> v.alpha = 0.55f
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.alpha = 1f
                }
                false // Tıklamayı düğmenin kendisi işler; sürükleme veri girişi üretmez.
            }
        }

    private fun buttons(compact: Boolean) = LinearLayout(context).apply {
        orientation = if (compact) HORIZONTAL else VERTICAL
        val room = resources.displayMetrics.widthPixels - scaledDp(148) - dp(28) - dp(20)
        val width = minOf(scaledDp(38), (room / 5).coerceAtLeast(dp(18)))
        val items = listOf(
            button(if (compact) "⌫" else "DEL", "#78909C", compact) { onDelete() },
            button("4", "#F44336", compact) { onNumber(4) },
            button("3", "#FF9800", compact) { onNumber(3) },
            button("2", "#2196F3", compact) { onNumber(2) },
            button("1", "#4CAF50", compact) { onNumber(1) }
        )
        items.forEach { b ->
            addView(b, if (compact) LayoutParams(width, scaledDp(38)).apply {
                setMargins(dp(2), 0, dp(2), 0)
            } else LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply {
                setMargins(0, dp(2), 0, dp(2))
            })
        }
    }

    @JvmOverloads
    fun update(st: EngineState?, busy: String?, echo: Echo = Echo(0, emptyList())) {
        status.text = busy ?: ""
        status.visibility = if (busy == null) GONE else VISIBLE
        fun bind(number: PredictionRow, side: PredictionRow, probs: DoubleArray?) {
            val p = councilPrediction(probs, single = s.pairMode == 2)
            val learning = st?.learning == true && probs != null
            number.value.text = if (learning) "…" else p.number
            number.confidence.text = if (learning) "${st?.count}/${s.silentFirst}" else p.numberConfidence
            side.value.text = p.side
            side.confidence.text = p.sideConfidence
        }
        bind(kotlinNumber, kotlinSide, st?.kotlinProbs)
        bind(pythonNumber, pythonSide, st?.pythonProbs)
        val values = overlayRecent(st, echo)
        recent?.text = if (values.isEmpty()) recentEmpty else values.joinToString(" ")
    }
}
