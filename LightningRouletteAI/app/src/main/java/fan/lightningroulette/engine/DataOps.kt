package fan.lightningroulette.engine

import fan.lightningroulette.core.*
import fan.lightningroulette.data.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Callable
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** Veri işlemleri (Veri sekmesi + Ayarlar): içe/dışa aktarma, paket, yedek, silme etkisi, sıfırlama. Motor iş parçacığından çağrılır. */
object DataOps {
    private val dao: LrDao get() = Engine.dao
    private fun stamp(): String = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())

    fun currentSpins(): List<Spin> {
        val d = Engine.dataset ?: return emptyList()
        return dao.spins(d.id).map { Spin(it.id, it.value, it.tsMs, it.source, it.tsType) }
    }

    // ───────── spinler listesi (en yeni → en eski; sayfalı)
    fun page(filter: String, query: String, page: Int, size: Int): List<SpinE> {
        val d = Engine.dataset ?: return emptyList()
        val q = query.trim().removePrefix("#")
        val num = q.toIntOrNull()
        return when {
            query.trim().startsWith("#") && num != null -> dao.spins(d.id).filter { it.id == num.toLong() }
            num != null && num in 0..36 -> dao.spinsByValue(d.id, num, size, page * size)
            filter == "LIVE" || filter == "IMPORT" || filter == "SAMPLE" -> dao.spinsBySource(d.id, filter, size, page * size)
            else -> dao.spinsPage(d.id, size, page * size)
        }
    }

    fun spinCount(): Int = Engine.dataset?.let { dao.spinCount(it.id) } ?: 0

    /** Seçili spinler silinirse etkilenecekler (onay diyaloğu için). */
    class Impact(val spins: Int, val firstIndex: Int, val predictions: Int, val evaluations: Int, val total: Int)
    fun impactOf(ids: Set<Long>): Impact {
        val d = Engine.dataset ?: return Impact(0, 0, 0, 0, 0)
        val total = dao.spinCount(d.id)
        val first = ids.minOfOrNull { dao.spinIndex(d.id, it) } ?: total
        val preds = dao.lastPredictions(d.id, 100000).count { it.refCount >= first - 1 }
        return Impact(ids.size, first, preds, preds, total)
    }

    /** Silme/düzenleme: bağımlı tahmin, değerlendirme ve model durumu geçersiz kılınır; tam yeniden hesaplama başlar. */
    fun deleteSpins(ids: Set<Long>): String {
        val d = Engine.dataset ?: return "dataset yok"
        val first = ids.minOfOrNull { dao.spinIndex(d.id, it) } ?: return "seçim boş"
        Engine.db.runInTransaction(Callable {
            for (id in ids) dao.deleteSpin(id)
            invalidateFrom(d.id, first - 1)
        })
        Engine.store?.log("WARN", null, "SPINS_DELETED", "${ids.size} spin · ilk indeks $first → tam replay")
        Engine.reloadAfterDataChange(true, "${ids.size} spin silindi · model yeniden hesaplandı")
        return "ok"
    }

    fun editSpin(id: Long, value: Int): String {
        val d = Engine.dataset ?: return "dataset yok"
        if (value !in 0..36) return "${Codes.REC}: 0–36 dışı"
        val idx = dao.spinIndex(d.id, id)
        Engine.db.runInTransaction(Callable { dao.updateSpinValue(id, value); invalidateFrom(d.id, idx - 1) })
        Engine.store?.log("WARN", null, "SPIN_EDITED", "#$id → $value · tam replay")
        Engine.reloadAfterDataChange(true, "Spin #$id düzenlendi · model yeniden hesaplandı")
        return "ok"
    }

    private fun invalidateFrom(datasetId: Long, refAfter: Int) {
        val r = maxOf(refAfter, -1)
        dao.deleteEvaluationsAfter(datasetId, r)
        dao.deletePredictionsAfter(datasetId, r)
        dao.deleteStatePrefix("$datasetId:brain")
        dao.deleteStatePrefix("lab.")
        try { Engine.py?.reset() } catch (_: Exception) { }
    }

    // ───────── içe aktarma
    fun analyse(text: String, fileName: String, order: String, mode: String): ImportReport =
        Importer.analyse(text, fileName, null, order, mode, if (mode == "APPEND") currentSpins() else emptyList())

    fun applyImport(rep: ImportReport, fileName: String, mode: String, name: String): String {
        if (rep.newCount == 0) return "içe aktarılacak yeni kayıt yok"
        val now = System.currentTimeMillis()
        val batchSyn = rep.syntheticTs
        if (mode == "NEW_DATASET" || Engine.dataset == null) {
            val id = Engine.createDataset(name.ifBlank { "İçe aktarma · $fileName" }, "IMPORT:$fileName", batchSyn, rep.spins.map { it.copy(source = "IMPORT") }, "format ${rep.format} · ${rep.newCount} yeni")
            dao.insertBatch(ImportBatchE(0, id, fileName, rep.format, rep.total, rep.valid, rep.invalid, rep.duplicate, rep.newCount, now, batchSyn))
            Engine.finishSetup()
            Engine.openSession()
            Engine.notify("${rep.newCount} kayıt içe aktarıldı (yeni dataset)")
            return "ok"
        }
        val d = Engine.dataset ?: return "dataset yok"
        val spins = rep.spins
        Engine.db.runInTransaction(Callable {
            val bid = dao.insertBatch(ImportBatchE(0, d.id, fileName, rep.format, rep.total, rep.valid, rep.invalid, rep.duplicate, rep.newCount, now, batchSyn))
            for (chunk in spins.chunked(500)) dao.insertSpins(chunk.map { SpinE(0, d.id, it.value, it.ts, it.tsType, "IMPORT", bid) })
            val all = dao.spins(d.id).map { Spin(it.id, it.value, it.tsMs, it.source, it.tsType) }
            val hash = Importer.datasetHash(all)
            val nd = d.copy(version = d.version + 1, hash = hash, synthetic = d.synthetic && batchSyn)
            dao.updateDataset(nd)
            dao.insertVersion(DatasetVersionE(0, d.id, nd.version, all.size, hash, now, "ekleme · $fileName · +${spins.size}"))
        })
        Engine.openSessionDataset()
        Engine.reloadAfterDataChange(false, "${rep.newCount} yeni kayıt eklendi · yalnızca yeni kayıtlar işlendi")
        return "ok"
    }

    // ───────── dışa aktarma
    fun exportSpins(format: String): Pair<String, ByteArray> {
        val spins = currentSpins(); val d = Engine.dataset
        val base = "lightning_spins_${(d?.name ?: "dataset").replace(Regex("[^A-Za-z0-9]+"), "_")}_${stamp()}"
        return when (format) {
            "json" -> "$base.json" to Exporter.json(spins, mapOf("dataset" to (d?.name ?: ""), "version" to (d?.version ?: 1), "synthetic" to (d?.synthetic ?: false))).toByteArray(Charsets.UTF_8)
            "txt" -> "$base.txt" to Exporter.txt(spins).toByteArray(Charsets.UTF_8)
            else -> "$base.csv" to Exporter.csv(spins).toByteArray(Charsets.UTF_8)
        }
    }

    /** Tek paket (.lrexport = GZIP JSON): dataset + spinler + tahminler (son 2 000) + deneyler + model metadata. */
    fun exportPackage(scope: Set<String>): Pair<String, ByteArray> {
        val m = LinkedHashMap<String, Any?>()
        m["format"] = "LRAI-PACK-1"; m["createdAt"] = System.currentTimeMillis(); m["app"] = "1.0.0"
        val ds = dao.datasets()
        m["datasets"] = ds.map { mapOf("id" to it.id, "name" to it.name, "createdAt" to it.createdAt, "source" to it.source, "synthetic" to it.synthetic, "active" to it.active, "version" to it.version, "hash" to it.hash, "note" to it.note) }
        if ("spins" in scope) m["spins"] = ds.flatMap { d -> dao.spins(d.id).map { listOf(d.id, it.value, it.tsMs, it.tsType, it.source) } }
        if ("predictions" in scope) m["predictions"] = ds.flatMap { d -> dao.lastPredictions(d.id, 2000).reversed().map { listOf(d.id, it.refCount, it.createdAt, it.json, it.pyStatus, it.modelVersion) } }
        if ("experiments" in scope) m["experiments"] = dao.experiments().reversed().map { mapOf("code" to it.code, "kind" to it.kind, "datasetId" to it.datasetId, "datasetHash" to it.datasetHash, "hypothesis" to it.hypothesis, "params" to it.paramsJson, "paramHash" to it.paramHash, "seed" to it.seed, "status" to it.status, "cls" to it.cls, "delta" to it.deltaPp, "n" to it.n, "reason" to it.reason, "result" to it.resultJson, "createdAt" to it.createdAt, "finishedAt" to it.finishedAt) }
        if ("models" in scope) m["models"] = dao.modelVersions().reversed().map { mapOf("version" to it.version, "createdAt" to it.createdAt, "champion" to it.champion, "params" to it.paramsJson, "note" to it.note) }
        val bo = ByteArrayOutputStream()
        GZIPOutputStream(bo).use { it.write(Json.stringify(m).toByteArray(Charsets.UTF_8)) }
        return "lightning_paket_${stamp()}.lrexport" to bo.toByteArray()
    }

    private fun readPack(bytes: ByteArray): Map<String, Any?> {
        val raw = try { GZIPInputStream(ByteArrayInputStream(bytes)).use { String(it.readBytes(), Charsets.UTF_8) } } catch (e: Exception) { String(bytes, Charsets.UTF_8) }
        val m = Json.parse(raw).jmap()
        if (m["format"].jstr() != "LRAI-PACK-1") throw LrError(Codes.EXP, "paket biçimi tanınmadı")
        return m
    }

    /** Round-trip testi: paketi yeniden oku, spin sayısı ve içerik özeti (hash) eşleşmeli (Prompt §18). */
    fun verifyRoundTrip(bytes: ByteArray): String {
        val m = readPack(bytes)
        val ds = dao.datasets()
        val spins = m["spins"].jlist()
        val ok = ArrayList<String>(); var good = true
        for (d in ds) {
            val rows = spins.filter { it.jlist().getOrNull(0).jlong() == d.id }
            val list = rows.mapIndexed { i, r -> val l = r.jlist(); Spin(i.toLong(), l[1].jint(), l[2].jlong(), l[4].jstr(), l[3].jstr()) }
            val orig = dao.spins(d.id).map { Spin(it.id, it.value, it.tsMs, it.source, it.tsType) }
            val same = list.size == orig.size && Importer.datasetHash(list) == Importer.datasetHash(orig)
            if (!same) good = false
            ok.add("${d.name}: ${list.size}/${orig.size} spin · hash " + (if (same) "eşleşti ✓" else "FARKLI ✗"))
        }
        // metin biçimleri de aynı yoldan doğrulanır
        val cur = currentSpins()
        for (f in listOf("csv", "json", "txt")) {
            val t = when (f) { "csv" -> Exporter.csv(cur); "json" -> Exporter.json(cur); else -> Exporter.txt(cur) }
            val r = Importer.analyse(t, "x.$f", null, "AUTO", "NEW_DATASET")
            val same = r.spins.map { it.value } == cur.map { it.value } && r.invalid == 0
            if (!same) good = false
            ok.add("$f: ${r.spins.size}/${cur.size} · " + (if (same) "✓" else "✗"))
        }
        return (if (good) "ROUND-TRIP BAŞARILI\n" else "ROUND-TRIP HATASI (${Codes.EXP})\n") + ok.joinToString("\n")
    }

    /** Geri yükle: paketteki datasetler YENİ olarak eklenir (mevcut veri silinmez). */
    fun restorePackage(bytes: ByteArray): String {
        val m = readPack(bytes)
        val ds = m["datasets"].jlist().map { it.jmap() }
        val spins = m["spins"].jlist(); val preds = m["predictions"].jlist()
        val idMap = HashMap<Long, Long>()
        var nSpins = 0
        Engine.db.runInTransaction(Callable {
            for (d in ds) {
                val old = d["id"].jlong()
                val nid = dao.insertDataset(DatasetE(0, d["name"].jstr("geri yüklenen") + " (geri yükleme)", System.currentTimeMillis(), d["source"].jstr("RESTORE"), d["synthetic"].jbool(), false, d["version"].jint(1), d["hash"].jstr(), d["note"].jstr()))
                idMap[old] = nid
                dao.insertVersion(DatasetVersionE(0, nid, d["version"].jint(1), 0, d["hash"].jstr(), System.currentTimeMillis(), "geri yükleme"))
            }
            for (chunk in spins.chunked(500)) dao.insertSpins(chunk.mapNotNull { r -> val l = r.jlist(); val nid = idMap[l[0].jlong()] ?: return@mapNotNull null; nSpins++; SpinE(0, nid, l[1].jint(), l[2].jlong(), l[3].jstr("REAL"), l[4].jstr("IMPORT"), 0) })
            for (r in preds) { val l = r.jlist(); val nid = idMap[l[0].jlong()] ?: continue; dao.insertPrediction(PredictionE(0, nid, l[1].jint(), l[2].jlong(), l[3].jstr(), l[4].jstr("OFF"), l[5].jstr("v1.0.0"))) }
            for (e in m["experiments"].jlist().map { it.jmap() }) {
                val code = e["code"].jstr()
                val exists = dao.experiments().any { it.code == code }
                val nid = idMap[e["datasetId"].jlong()] ?: continue
                dao.insertExperiment(ExperimentE(0, if (exists) "$code-R${System.nanoTime() % 100000}" else code, e["kind"].jstr("RUN"), nid, e["datasetHash"].jstr(), e["hypothesis"].jstr(), e["params"].jstr(), e["paramHash"].jstr(), e["seed"].jlong(42), "lr-1.0.0",
                    e["status"].jstr("DONE"), e["cls"].jstr(), e["delta"].jnum(), e["n"].jint(), e["reason"].jstr(), e["result"].jstr(), "", 0, 100, e["createdAt"].jlong(), 0, e["finishedAt"].jlong()))
            }
            for (v in m["models"].jlist().map { it.jmap() }) dao.insertModelVersion(ModelVersionE(0, v["version"].jstr(), v["createdAt"].jlong(), false, v["params"].jstr(), v["note"].jstr()))
        })
        Engine.store?.log("INFO", null, "RESTORE_DONE", "${ds.size} dataset · $nSpins spin")
        return "${ds.size} dataset, $nSpins spin geri yüklendi (pasif olarak eklendi; Veri › Sürümler’den aktif yap)"
    }

    // ───────── dataset
    fun datasets(): List<DatasetE> = dao.datasets()

    fun activate(id: Long) {
        val d = dao.dataset(id) ?: return
        Engine.db.runInTransaction(Callable { dao.deactivateAll(); dao.updateDataset(d.copy(active = true)) })
        Engine.setDataset(dao.dataset(id))
        Engine.openSession()
    }

    fun deleteSample(): String {
        val samples = dao.datasets().filter { it.synthetic && it.source.startsWith("SAMPLE") }
        if (samples.isEmpty()) return "örnek veri yok"
        Engine.db.runInTransaction(Callable { for (d in samples) wipeDataset(d.id) })
        val active = dao.activeDataset() ?: dao.datasets().lastOrNull()?.also { dao.updateDataset(it.copy(active = true)) }
        Engine.setDataset(active)
        if (active == null) { Engine.settings.setupDone = false; Engine.restartBoot() } else Engine.openSession()
        return "örnek veri silindi"
    }

    private fun wipeDataset(id: Long) {
        dao.deleteEvaluationsAfter(id, -1); dao.deletePredictionsOf(id); dao.deleteSpinsOf(id); dao.deleteVersionsOf(id); dao.deleteStatePrefix("$id:"); dao.deleteDataset(id)
    }

    /** Tüm veriyi sıfırla (geri alınamaz). */
    fun resetAll(): String {
        Engine.db.runInTransaction(Callable {
            dao.deleteAllEvaluations(); dao.deleteAllPredictions(); dao.deleteAllSpins(); dao.deleteAllVersions(); dao.deleteAllDatasets()
            dao.deleteAllBatches(); dao.deleteAllStates(); dao.deleteAllModelVersions(); dao.deleteAllExperiments(); dao.deleteAllLogs()
        })
        try { Engine.py?.reset() } catch (_: Exception) { }
        Engine.setDataset(null)
        Engine.settings.setupDone = false
        Engine.restartBoot()
        return "tüm veri sıfırlandı"
    }

    // ───────── yedek (veritabanı + model metadata tek dosya; günlük otomatik)
    fun backupDir(): File = File(Engine.context.filesDir, "backups").also { it.mkdirs() }

    fun autoBackupIfDue(keep: Int = 7) {
        val dir = backupDir()
        val files = dir.listFiles()?.sortedBy { it.lastModified() } ?: emptyList()
        if (files.isNotEmpty() && System.currentTimeMillis() - files.last().lastModified() < 24L * 3600 * 1000) return
        val (name, bytes) = exportPackage(setOf("spins", "predictions", "experiments", "models"))
        File(dir, name).writeBytes(bytes)
        val all = dir.listFiles()?.sortedBy { it.lastModified() } ?: return
        for (f in all.dropLast(keep)) f.delete()
        Engine.store?.log("INFO", null, "AUTO_BACKUP", name)
    }
}
