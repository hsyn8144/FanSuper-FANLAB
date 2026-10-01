package fan.superai.overlay

import android.app.Activity
import android.app.Application
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.TextView
import fan.superai.Echo
import fan.superai.data.AppSettings
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class OverlayViewTest {
    private val drags = mutableListOf<Pair<Int, Int>>()
    private val numbers = mutableListOf<Int>()
    private var deletes = 0
    private val activity = Robolectric.buildActivity(Activity::class.java).setup().visible()

    @After fun tearDown() {
        activity.pause().stop().destroy()
    }

    private fun overlay(horizontal: Boolean = false, recent: Boolean = true, scale: Float = 1f): OverlayView {
        return OverlayView(
            RuntimeEnvironment.getApplication(),
            AppSettings(overlayHorizontal = horizontal, showRecent = recent, overlayTextScale = scale),
            onNumber = { numbers += it }, onDelete = { deletes++ },
            onDrag = { dx, dy -> drags += dx to dy }
        ).apply {
            // Android posts click callbacks through the attached window. An unattached
            // view queues them until attachment, unlike the real service overlay.
            activity.get().setContentView(this, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            shadowOf(Looper.getMainLooper()).idle()
            measure(View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.AT_MOST))
            layout(0, 0, measuredWidth, measuredHeight)
        }
    }

    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()

    private fun text(view: View, label: String): TextView = descendants(view)
        .filterIsInstance<TextView>().first { it.text.toString() == label }

    private fun center(root: View, target: View): Pair<Float, Float> {
        var x = target.width / 2f
        var y = target.height / 2f
        var child = target
        while (child !== root) {
            x += child.left; y += child.top
            child = child.parent as View
        }
        return x to y
    }

    private fun event(view: View, action: Int, point: Pair<Float, Float>, downTime: Long) {
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, point.first, point.second, 0)
        try { view.dispatchTouchEvent(event) } finally { event.recycle() }
    }

    private fun drag(view: OverlayView, point: Pair<Float, Float>) {
        val time = SystemClock.uptimeMillis()
        event(view, MotionEvent.ACTION_DOWN, point, time)
        event(view, MotionEvent.ACTION_MOVE, point.first + 40f to point.second + 25f, time)
        event(view, MotionEvent.ACTION_UP, point.first + 40f to point.second + 25f, time)
    }

    @Test fun predictionRowsDragInBothLayouts() {
        for (horizontal in listOf(false, true)) {
            for (recent in listOf(false, true)) {
                val view = overlay(horizontal, recent)
                for (label in listOf("🔵 K", "🐍 Py", "🔵 Yan", "🐍 PyYan")) {
                    drags.clear()
                    drag(view, center(view, text(view, label)))
                    assertEquals(listOf(40 to 25), drags)
                }
            }
        }
    }

    @Test fun recentNumbersAndCardPaddingDrag() {
        for (horizontal in listOf(false, true)) {
            val view = overlay(horizontal)
            drags.clear()
            drag(view, center(view, text(view, "- - - - - -")))
            assertEquals(listOf(40 to 25), drags)
            drags.clear()
            drag(view, 1f to 1f)
            assertEquals(listOf(40 to 25), drags)
        }
    }

    @Test fun tapsStillEnterNumbersAndDeleteWithoutDragging() {
        for (horizontal in listOf(false, true)) {
            val view = overlay(horizontal)
            for (label in listOf("4", "3", "2", "1", if (horizontal) "⌫" else "DEL")) {
                val point = center(view, text(view, label))
                val time = SystemClock.uptimeMillis()
                event(view, MotionEvent.ACTION_DOWN, point, time)
                event(view, MotionEvent.ACTION_UP, point, time)
                shadowOf(Looper.getMainLooper()).idle()
            }
        }
        assertEquals(listOf(4, 3, 2, 1, 4, 3, 2, 1), numbers)
        assertEquals(2, deletes)
        assertTrue(drags.isEmpty())
    }

    @Test fun longPressKeepsOnlyFourPredictionsAndStillAllowsDragging() {
        val view = overlay()
        val before = descendants(view).size
        val point = center(view, text(view, "🔵 K"))
        val time = SystemClock.uptimeMillis()
        event(view, MotionEvent.ACTION_DOWN, point, time)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ViewConfiguration.getLongPressTimeout().toLong() + 1))
        event(view, MotionEvent.ACTION_MOVE, point.first + 40f to point.second + 25f, time)
        event(view, MotionEvent.ACTION_UP, point.first + 40f to point.second + 25f, time)
        assertEquals(before, descendants(view).size)
        assertEquals(listOf(40 to 25), drags)
        val labels = descendants(view).filterIsInstance<TextView>().map { it.text.toString() }
        assertFalse(labels.any { it.contains("Kalıp") || it.contains("Hakem") || it.contains("FAN") })
        assertTrue(numbers.isEmpty())
        assertEquals(0, deletes)
    }

    @Test fun cancelledTouchDoesNotCarryPositionIntoNextDrag() {
        val view = overlay()
        val point = center(view, text(view, "🔵 K"))
        val time = SystemClock.uptimeMillis()
        event(view, MotionEvent.ACTION_DOWN, point, time)
        event(view, MotionEvent.ACTION_CANCEL, point, time)
        drag(view, point.first + 2f to point.second + 2f)
        assertEquals(listOf(40 to 25), drags)
    }

    /**
     * Veri girişi düğmeleri her zaman kartın içinde kalmalı: yatay kart dar ekranda
     * ekran genişliğini aştığında sağdaki düğmeler ekran dışında kalıyordu.
     */
    @Test fun allDataButtonsStayInsideTheCard() {
        for (horizontal in listOf(false, true)) {
            val view = overlay(horizontal)
            val cardWidth = view.width
            assertTrue("kart ölçülmeli", cardWidth > 0)
            for (label in listOf("4", "3", "2", "1", if (horizontal) "⌫" else "DEL")) {
                val b = text(view, label)
                val p = center(view, b)
                assertTrue("$label kart içinde olmalı (x=${p.first}, kart=$cardWidth)",
                    p.first >= 0f && p.first <= cardWidth.toFloat())
                assertTrue("$label dokunulabilir olmalı (genişlik=${b.width}, yükseklik=${b.height})",
                    b.width >= 12 && b.height >= 20)
            }
        }
    }

    private fun tagged(view: View, tag: String): TextView = descendants(view)
        .filterIsInstance<TextView>().first { it.tag == tag }

    @Test fun fourRowsUseTheirOwnCouncilEvenWithoutARefereeVerdict() {
        for (horizontal in listOf(false, true)) {
            val view = overlay(horizontal)
            view.update(overlayTestState(), null)
            assertEquals("1/2", tagged(view, "kotlin.number.value").text.toString())
            assertEquals("%87", tagged(view, "kotlin.number.confidence").text.toString())
            assertEquals("4/3", tagged(view, "python.number.value").text.toString())
            assertEquals("%75", tagged(view, "python.number.confidence").text.toString())
            assertEquals("T•K", tagged(view, "kotlin.side.value").text.toString())
            assertEquals("%84", tagged(view, "kotlin.side.confidence").text.toString())
            assertEquals("Ç•B", tagged(view, "python.side.value").text.toString())
            assertEquals("%68", tagged(view, "python.side.confidence").text.toString())
        }
    }

    @Test fun missingPythonAndResetClearOldPredictions() {
        val view = overlay()
        view.update(overlayTestState(), null)
        view.update(overlayTestState().copy(pythonProbs = null), null)
        assertEquals("1/2", tagged(view, "kotlin.number.value").text.toString())
        for (key in listOf("python.number", "python.side")) {
            assertEquals("--", tagged(view, "$key.value").text.toString())
            assertEquals("--", tagged(view, "$key.confidence").text.toString())
        }
        view.update(null, null)
        for (key in listOf("kotlin.number", "python.number", "kotlin.side", "python.side")) {
            assertEquals("--", tagged(view, "$key.value").text.toString())
            assertEquals("--", tagged(view, "$key.confidence").text.toString())
        }
    }

    @Test fun recentSixAreNewestFirstInBothLayoutsBeforeAndAfterMotorCatchesUp() {
        for (horizontal in listOf(false, true)) {
            val view = overlay(horizontal)
            val st = overlayTestState()
            view.update(st, null)
            assertEquals("3 2 4 1 3 2", tagged(view, "recent").text.toString())
            val echo = Echo(st.count, listOf(1))
            view.update(st, "Hesaplanıyor", echo)
            assertEquals("1 3 2 4 1 3", tagged(view, "recent").text.toString())
            val next = st.copy(count = st.count + 1, recent = (st.recent + 1).takeLast(6))
            view.update(next, null, echo)
            assertEquals("1 3 2 4 1 3", tagged(view, "recent").text.toString())
            view.update(next, null)
            assertEquals("1 3 2 4 1 3", tagged(view, "recent").text.toString())
            // DEL motoru önceki duruma döndürdüğünde soldaki en yeni sayı kalkar.
            view.update(st, null)
            assertEquals("3 2 4 1 3 2", tagged(view, "recent").text.toString())
        }
    }

    @Test fun learningKeepsInputAvailableAndDoesNotFakePythonPrediction() {
        val view = overlay()
        view.update(overlayTestState().copy(count = 12, learning = true, pythonProbs = null), null)
        assertEquals("…", tagged(view, "kotlin.number.value").text.toString())
        assertEquals("12/50", tagged(view, "kotlin.number.confidence").text.toString())
        assertEquals("--", tagged(view, "python.number.value").text.toString())
        assertTrue(text(view, "1").isEnabled)
        assertTrue(text(view, "DEL").isEnabled)
    }

    @Test fun approvedVerticalLayoutHasRecentThenFullWidthStackedButtons() {
        val view = overlay()
        val recentY = center(view, tagged(view, "recent")).second
        val buttons = listOf("DEL", "4", "3", "2", "1").map { text(view, it) }
        assertTrue(center(view, buttons.first()).second > recentY)
        buttons.zipWithNext().forEach { (a, b) ->
            assertTrue(center(view, a).second < center(view, b).second)
            assertEquals(a.width, b.width)
            assertEquals(center(view, a).first, center(view, b).first, 0.01f)
        }
        assertEquals(view.width - view.paddingLeft - view.paddingRight, buttons.first().width)
    }

    @Test fun buttonBoundsFitAfterShortScreenRemeasureAtEveryTextScale() {
        for (scale in listOf(.85f, 1f, 1.2f)) {
            val view = overlay(scale = scale)
            val density = view.resources.displayMetrics.density
            val maxHeight = (300 * density).toInt()
            view.measure(View.MeasureSpec.makeMeasureSpec((320 * density).toInt(), View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST))
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)
            assertTrue(view.height <= maxHeight)
            for (label in listOf("DEL", "4", "3", "2", "1")) {
                val button = text(view, label)
                val c = center(view, button)
                assertTrue("$label üst sınır", c.second - button.height / 2f >= 0)
                assertTrue("$label alt sınır", c.second + button.height / 2f <= view.height)
                assertTrue("$label dokunma alanı: scale=$scale density=$density " +
                    "scaledDensity=${view.resources.displayMetrics.scaledDensity} " +
                    "screen=${view.resources.displayMetrics.heightPixels} card=${view.height} " +
                    "button=${button.height} children=" +
                    (0 until view.childCount).joinToString { "${view.getChildAt(it).height}" },
                    button.height >= 20 * density)
            }
        }
    }

    /** Kart ekran dışına sürüklenirse konum ekran içine kıstırılmalı. */
    @Test fun overlayPositionIsClampedToScreen() {
        val a = clampOverlayPos(5000, -200, 300, 120, 1080, 1920)
        assertEquals(780, a[0]); assertEquals(0, a[1])
        val b = clampOverlayPos(-50, 4000, 300, 120, 1080, 1920)
        assertEquals(0, b[0]); assertEquals(1800, b[1])
        val c = clampOverlayPos(10, 20, 2000, 3000, 1080, 1920)   // kart ekrandan büyük
        assertEquals(0, c[0]); assertEquals(0, c[1])
        val d = clampOverlayPos(40, 240, 300, 120, 1080, 1920)    // zaten içeride
        assertEquals(40, d[0]); assertEquals(240, d[1])
    }
}
