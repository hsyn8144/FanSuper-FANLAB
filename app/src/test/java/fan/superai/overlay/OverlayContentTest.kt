package fan.superai.overlay

import fan.superai.Echo
import fan.superai.engine.EngineState
import fan.superai.engine.Scores
import org.junit.Assert.*
import org.junit.Test

internal fun overlayTestState() = EngineState(
    count = 60, recent = listOf(2, 3, 1, 4, 2, 3), verdict = null,
    kotlinProbs = doubleArrayOf(.75, .125, .0625, .0625),
    pythonProbs = doubleArrayOf(.125, .125, .25, .5),
    kotlinStats = emptyList(), pythonStats = emptyList(), pythonInfo = emptyMap(), kalipInfo = emptyMap(),
    regime = "-", scores = Scores(0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
    lastSideHit = null, learning = false, busy = null
)

class OverlayContentTest {
    @Test fun numberAndSideComeFromTheSameCouncilDistribution() {
        val st = overlayTestState()
        assertEquals(CouncilPrediction("1/2", "%87", "T•K", "%84"), councilPrediction(st.kotlinProbs))
        assertEquals(CouncilPrediction("4/3", "%75", "Ç•B", "%68"), councilPrediction(st.pythonProbs))
    }

    @Test fun explicitSingleModeUsesOnlyTheFirstProbability() {
        assertEquals(CouncilPrediction("1", "%75", "T•K", "%84"),
            councilPrediction(overlayTestState().kotlinProbs, single = true))
    }

    @Test fun missingPythonNeverFallsBackToKotlinOrTheReferee() {
        assertEquals(CouncilPrediction(), councilPrediction(null))
    }

    @Test fun allFourNumbersHaveCorrectParityAndSize() {
        val sides = listOf("T•K", "Ç•K", "T•B", "Ç•B")
        for (i in 0..3) {
            val p = DoubleArray(4) { if (it == i) 1.0 else 0.0 }
            val before = p.copyOf()
            val display = councilPrediction(p, single = true)
            assertEquals("${i + 1}", display.number)
            assertEquals(sides[i], display.side)
            assertEquals("%100", display.sideConfidence)
            assertArrayEquals(before, p, 0.0) // Gösterim motor dağılımını değiştiremez.
        }
    }

    @Test fun tiesHaveStableNumberAndSideOrdering() {
        assertEquals(CouncilPrediction("1/2", "%50", "T•B", "%50"),
            councilPrediction(DoubleArray(4) { .25 }))
    }

    @Test fun emptyAndPartialHistoryDoNotInventRecords() {
        assertTrue(overlayRecent(null, Echo(0, emptyList())).isEmpty())
        assertEquals(listOf(3, 1), overlayRecent(null, Echo(0, listOf(1, 3))))
        assertEquals(listOf(2, 1), overlayRecent(overlayTestState().copy(count = 2, recent = listOf(1, 2)), Echo(0, emptyList())))
    }

    @Test fun partialEchoConsumptionDoesNotDuplicateRecords() {
        val st = overlayTestState()
        val echo = Echo(st.count, listOf(1, 4, 2))
        val expected = listOf(2, 4, 1, 3, 2, 4)
        assertEquals(expected, overlayRecent(st, echo))
        assertEquals(expected, overlayRecent(st.copy(count = 61, recent = (st.recent + 1).takeLast(6)), echo))
        assertEquals(expected, overlayRecent(st.copy(count = 62, recent = (st.recent + listOf(1, 4)).takeLast(6)), echo))
        val done = st.copy(count = 63, recent = (st.recent + echo.values).takeLast(6))
        assertEquals(expected, overlayRecent(done, echo))
        assertEquals(expected, overlayRecent(done, Echo(done.count, emptyList())))
        assertEquals(listOf(2, 3, 1, 4, 2, 3), st.recent)
    }

    @Test fun moreThanSixQueuedInputsStillShowExactlyTheLastSix() {
        val st = overlayTestState()
        val values = listOf(1, 1, 2, 3, 4, 4, 2, 1)
        val echo = Echo(st.count, values.takeLast(6), pendingCount = values.size)
        for (processed in 0..values.size) {
            val next = st.copy(count = st.count + processed, recent = (st.recent + values.take(processed)).takeLast(6))
            assertEquals(values.takeLast(6).asReversed(), overlayRecent(next, echo))
        }
    }

    @Test fun overlongHistoryIsAlwaysLimitedToSix() {
        val st = overlayTestState().copy(recent = listOf(1, 2, 3, 4, 1, 2, 3, 4))
        assertEquals(listOf(4, 3, 2, 1, 4, 3), overlayRecent(st, Echo(0, emptyList())))
    }
}
