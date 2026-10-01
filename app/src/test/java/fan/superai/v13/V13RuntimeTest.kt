package fan.superai.v13

import android.app.Application
import fan.superai.engine.EngineConfig
import fan.superai.engine.FanEngine
import fan.superai.v13.db.FanDatabase
import fan.superai.v13.db.V13Store
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.random.Random

/** Uçtan uca yaşam döngüsü: ilk kurulum → canlı ekleme → geri alma → yeniden başlatma (yalnızca yeni kayıtlar). Python yok. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class V13RuntimeTest {
    private lateinit var db: FanDatabase
    private lateinit var store: V13Store
    private val recs = ArrayList<FanRecord>()

    @Before fun setUp() {
        db = FanDatabase.inMemory(RuntimeEnvironment.getApplication())
        store = V13Store(db)
        val r = Random(7)
        repeat(120) { recs.add(FanRecord.of((it + 1).toLong(), 1_700_000_000L + it * 20L, 1 + r.nextInt(4))) }
    }

    @After fun tearDown() { db.close() }

    private fun engine(): FanEngine {
        val e = FanEngine(EngineConfig(), null)
        recs.forEach { e.values.add(it.number - 1); e.times.add(it.timestamp) }
        e.rebuildAll()
        return e
    }

    private fun clock() = recs.last().timestamp + 1

    @Test fun fullLifecycle() {
        val engine = engine()
        val env = V13Env(engine, null, engine.cfg) { recs.toList() }
        val rt = V13Runtime(store) { clock() }
        val plan = rt.prepare(recs.toList())
        assertEquals("FULL", plan.kind)
        rt.finish(plan, env)
        assertEquals(rt.status.value.error, "READY", rt.status.value.phase)
        val first = rt.final.value
        assertNotNull(first)
        assertEquals(120, first!!.predictionSequence)
        assertTrue(first.number in 1..4)
        assertEquals(121, store.predictionTotal())          // 120 replay + 1 canlı kilit
        assertNotNull(rt.insights.value)
        assertEquals(120, rt.insights.value!!.stats.total)

        // canlı ekleme: kilitli tahmin değerlendirilir, yenisi kilitlenir
        val rec = FanRecord.of(121, recs.last().timestamp + 20, 3)
        recs.add(rec)
        engine.add(rec.number - 1, rec.timestamp)
        rt.onNewRecord(rec, env)
        assertEquals(rt.status.value.error, "READY", rt.status.value.phase)
        val second = rt.final.value!!
        assertEquals(121, second.predictionSequence)
        assertEquals(122, store.predictionTotal())
        val evaluated = store.page(0, 5).first { it.sequence == 120 }
        assertEquals(3, evaluated.actualNumber)
        assertEquals(first.predictionLockId, evaluated.lockId)   // değerlendirilen, KİLİTLİ olan tahmin
        assertEquals(121, rt.insights.value!!.stats.total)

        // geri alma: aynı kilitli tahmin geri gelir, sonuç sıfırlanır
        engine.undo(); recs.removeAt(recs.size - 1)
        rt.onUndo(recs.size, env)
        assertEquals(rt.status.value.error, "READY", rt.status.value.phase)
        assertEquals(first.predictionLockId, rt.final.value!!.predictionLockId)
        assertEquals(121, store.predictionTotal())
        assertNull(store.page(0, 1).first().actualNumber)

        // yeniden başlatma: durum güncel → yalnızca devam, aynı kilit
        val rt2 = V13Runtime(store) { clock() }
        val plan2 = rt2.prepare(recs.toList())
        assertEquals("RESUME", plan2.kind)
        rt2.finish(plan2, V13Env(engine, null, engine.cfg) { recs.toList() })
        assertEquals(first.predictionLockId, rt2.final.value!!.predictionLockId)

        // uygulama kapalıyken 3 yeni kayıt → yalnızca kuyruk işlenir
        repeat(3) { recs.add(FanRecord.of((recs.size + 1).toLong(), recs.last().timestamp + 20, 1 + it)) }
        val engine3 = engine()
        val rt3 = V13Runtime(store) { clock() }
        val plan3 = rt3.prepare(recs.toList())
        assertEquals("CATCHUP", plan3.kind)
        assertEquals(120, plan3.startAt)
        rt3.finish(plan3, V13Env(engine3, null, engine3.cfg) { recs.toList() })
        assertEquals(123, rt3.final.value!!.predictionSequence)
        assertEquals(123, rt3.insights.value!!.stats.total)
    }

    @Test fun researchReplayIsSandboxedAndReproducible() {
        val engine = engine()
        val env = V13Env(engine, null, engine.cfg) { recs.toList() }
        val rt = V13Runtime(store) { clock() }
        rt.finish(rt.prepare(recs.toList()), env)
        val live = rt.final.value!!.predictionLockId
        val liveSnap = rt.brain.snapshot()
        val a = rt.research(recs.toList(), engine.cfg, null)!!
        val b = rt.research(recs.toList(), engine.cfg, null)!!
        assertEquals(a.fingerprint, b.fingerprint)
        assertEquals(0L, a.result.leakViolations)
        assertTrue(a.summary.metrics.isNotEmpty())
        assertTrue(a.counterfactual.size == 4)
        assertEquals(live, rt.final.value!!.predictionLockId)       // canlı tahmin etkilenmedi
        assertTrue(liveSnap.contentEquals(rt.brain.snapshot()))     // canlı durum aynen
        assertTrue(store.replays(5).any { it.mode == "RESEARCH" })
    }
}
