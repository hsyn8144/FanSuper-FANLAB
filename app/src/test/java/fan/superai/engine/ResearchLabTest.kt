package fan.superai.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResearchLabTest {
    private fun logs(n: Int): List<StepLog> = (0 until n).map { i ->
        val a=i%4
        val p=DoubleArray(4){0.1}
        p[a]=0.7
        StepLog(true,true,true,true,true,true,true,true,true,true,a,p,p,p,listOf(p,p.copyOf(),p.copyOf()))
    }
    @Test fun metrics_are_finite() {
        val r=ResearchLab.analyze(logs(120), bootstrap=100, permutations=100)
        assertEquals(120,r.n)
        assertTrue(r.logLoss.isFinite())
        assertTrue(r.brier.isFinite())
        assertTrue(r.bootstrapLo<=r.bootstrapHi)
        assertTrue(r.walkForwardTop1>0.9)
    }
}
