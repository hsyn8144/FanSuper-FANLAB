package fan.superai.v13

import android.app.Application
import fan.superai.engine.EngineConfig
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

/** Room kalıcılığı: kayıtlar, durum parçaları, tahmin geçmişi, geri alma, açılış planı (devam / kuyruk / tam). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class V13StoreTest {
    private lateinit var db: FanDatabase
    private lateinit var store: V13Store

    @Before fun setUp() {
        db = FanDatabase.inMemory(RuntimeEnvironment.getApplication())
        store = V13Store(db)
    }

    @After fun tearDown() { db.close() }

    private fun records(n: Int, seed: Int = 4): List<FanRecord> {
        val r = Random(seed)
        return List(n) { FanRecord.of((it + 1).toLong(), 1_700_000_000L + it * 20L, 1 + r.nextInt(4)) }
    }

    private fun learned(recs: List<FanRecord>): FanBrain {
        val b = FanBrain()
        ReplayEngine.run(recs, b, EngineConfig(), null)
        b.predict(emptyList(), recs.last().timestamp + 1)
        return b
    }

    @Test fun recordsRoundTripAndSync() {
        val recs = records(30)
        store.syncRecords(recs.take(20))
        assertEquals(20, store.recordCount())
        store.syncRecords(recs)                       // yalnızca kuyruk eklenir
        assertEquals(30, store.recordCount())
        val back = store.records()
        assertEquals(recs.map { it.number }, back.map { it.number })
        assertEquals(recs.last().combinedSide, back.last().combinedSide)
        store.syncRecords(recs.take(25))              // fazlalık silinir
        assertEquals(25, store.recordCount())
    }

    @Test fun statePersistsAndRestoresIdentically() {
        val recs = records(90)
        val b = learned(recs)
        store.saveParts(b.exportParts(), b.count)
        assertEquals(90, store.savedCount())
        val parts = store.loadParts()
        val c = FanBrain(); c.importParts(parts)
        assertEquals(90, c.count)
        assertEquals(b.open!!.prediction.predictionLockId, c.open!!.prediction.predictionLockId)
        assertEquals(b.stats.total, c.stats.total)
        // değişmeyen parçalar ikinci kez yazılmaz (ama hata da vermez)
        store.saveParts(b.exportParts(), b.count)
        assertEquals(90, store.savedCount())
    }

    @Test fun predictionRowsResolveRollbackAndPage() {
        val recs = records(40)
        val brain = FanBrain()
        for ((i, r) in recs.withIndex()) {
            val p = brain.predict(emptyList(), r.timestamp - 1)
            store.savePrediction(V13Store.LIVE, p, true)
            val ev = brain.onActual(r)
            store.resolve(V13Store.LIVE, ev)
        }
        assertEquals(40, store.predictionTotal())
        val page = store.page(0, 15)
        assertEquals(15, page.size)
        assertEquals(39, page.first().sequence)
        assertNotNull(page.first().actualNumber)
        assertEquals(recs.last().number, page.first().actualNumber)
        assertEquals(15, store.page(15, 15).size)
        assertNotNull(store.explanation(5))
        store.rollbackTo(30)
        assertEquals(31, store.predictionTotal())     // 0..30
        val at30 = store.page(0, 1).first()
        assertEquals(30, at30.sequence)
        assertNull(at30.actualNumber)                 // yalnızca actual kolonları sıfırlanır
        assertTrue(at30.lockId.startsWith("L30-"))
    }

    @Test fun undoSnapshotsKeepOnlyLastEight() {
        val brain = FanBrain()
        val recs = records(30)
        for (r in recs) {
            brain.predict(emptyList(), r.timestamp - 1)
            val before = brain.count
            brain.onActual(r)
            brain.lastUndoSnapshot()?.let { store.saveUndo(it.first, it.second) }
            assertEquals(before + 1, brain.count)
        }
        assertNotNull(store.loadUndo(29))
        assertNotNull(store.loadUndo(22))
        assertNull(store.loadUndo(20))
        val snap = store.loadUndo(25)!!
        val b = FanBrain(); b.restore(snap)
        assertEquals(25, b.count)
        assertNotNull(b.open)
    }

    @Test fun startupPlanResumeCatchupOrFull() {
        val rt = V13Runtime(store)
        val recs = records(80)
        assertEquals("FULL", rt.prepare(recs).kind)                         // ilk kurulum
        val b = learned(recs)
        store.saveParts(b.exportParts(), b.count)
        assertEquals("RESUME", rt.prepare(recs).kind)                       // aynı veri → yalnızca devam
        val more = recs + records(5, seed = 9).mapIndexed { i, r -> FanRecord.of((81 + i).toLong(), 1_800_000_000L + i, r.number) }
        val plan = rt.prepare(more)
        assertEquals("CATCHUP", plan.kind)                                  // yalnızca yeni kayıtlar
        assertEquals(80, plan.startAt)
        val altered = recs.dropLast(1) + FanRecord.of(80, recs.last().timestamp, if (recs.last().number == 1) 2 else 1)
        assertEquals("FULL", rt.prepare(altered).kind)                      // veriyle uyuşmayan durum → tam replay
        assertEquals("FULL", rt.prepare(recs, forceFull = true).kind)       // manuel Full Replay
    }

    @Test fun corruptedStateFallsBackToFullReplay() {
        val rt = V13Runtime(store)
        val recs = records(60)
        val b = learned(recs)
        val parts = b.exportParts().toMutableList()
        parts[0] = StatePart(parts[0].table, parts[0].key, byteArrayOf(1, 2, 3))
        store.saveParts(parts, b.count)
        assertEquals("FULL", rt.prepare(recs).kind)
    }
}
