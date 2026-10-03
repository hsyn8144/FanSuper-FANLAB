package fan.lightningroulette.engine

import android.content.Context
import android.content.SharedPreferences
import fan.lightningroulette.core.BrainConfig
import fan.lightningroulette.core.Dir
import fan.lightningroulette.core.Sectors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Tüm ayarlar (SharedPreferences). Değişiklikte [version] artar → Compose yeniden çizer. */
class Settings(ctx: Context) {
    private val sp: SharedPreferences = ctx.getSharedPreferences("lr_settings", Context.MODE_PRIVATE)
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    private fun bump() { _version.value = _version.value + 1 }
    private fun getB(k: String, d: Boolean) = sp.getBoolean(k, d)
    private fun getI(k: String, d: Int) = sp.getInt(k, d)
    private fun getS(k: String, d: String) = sp.getString(k, d) ?: d
    private fun putB(k: String, v: Boolean) { sp.edit().putBoolean(k, v).apply(); bump() }
    private fun putI(k: String, v: Int) { sp.edit().putInt(k, v).apply(); bump() }
    private fun putS(k: String, v: String) { sp.edit().putString(k, v).apply(); bump() }

    // ── ilk açılış
    var disclaimerAccepted: Boolean get() = getB("disclaimer", false); set(v) = putB("disclaimer", v)
    var setupDone: Boolean get() = getB("setup_done", false); set(v) = putB("setup_done", v)

    // ── tahmin ve meclis
    var nCand: Int get() = getI("n_cand", 5).coerceIn(3, 5); set(v) = putI("n_cand", v.coerceIn(3, 5))
    /** 0 = otomatik (k1/k2/k3), 1..3 = sabit k */
    var kMode: Int get() = getI("k_mode", 0).coerceIn(0, 3); set(v) = putI("k_mode", v.coerceIn(0, 3))
    var dir: String get() = getS("dir", "bi"); set(v) = putS("dir", v)
    var diversity: Boolean get() = getB("diversity", true); set(v) = putB("diversity", v)
    /** Table kategorileri bit maskesi (COLOR, PARITY, HIGH/LOW, DOZEN, COLUMN) */
    var tableMask: Int get() = getI("table_mask", 31); set(v) = putI("table_mask", v and 31)
    /** cal = kalibre olasılık; model = Kotlin meclisi (kalibrasyon öncesi) */
    var pctMode: String get() = getS("pct_mode", "cal"); set(v) = putS("pct_mode", v)
    var weightAuto: Boolean get() = getB("w_auto", true); set(v) = putB("w_auto", v)
    var kotlinPct: Int get() = getI("k_pct", 50).coerceIn(20, 80); set(v) = putI("k_pct", v.coerceIn(20, 80))
    var pythonEnabled: Boolean get() = getB("py_on", true); set(v) = putB("py_on", v)
    var window: Int get() = getI("window", 300).coerceIn(50, 1000); set(v) = putI("window", v.coerceIn(50, 1000))
    var decayPm: Int get() = getI("decay_pm", 20).coerceIn(1, 100); set(v) = putI("decay_pm", v.coerceIn(1, 100))
    var sectorBounds: String get() = getS("sector_bounds", Sectors.DEFAULT_BOUNDS.joinToString(",")); set(v) = putS("sector_bounds", v)

    // ── overlay
    var ovEnabled: Boolean get() = getB("ov_on", false); set(v) = putB("ov_on", v)
    /** vertical | horizontal | compact | text | icon */
    var ovMode: String get() = getS("ov_mode", "vertical"); set(v) = putS("ov_mode", v)
    var ovScale: Int get() = getI("ov_scale", 100).coerceIn(60, 140); set(v) = putI("ov_scale", v.coerceIn(60, 140))
    var ovAlpha: Int get() = getI("ov_alpha", 92).coerceIn(60, 100); set(v) = putI("ov_alpha", v.coerceIn(60, 100))
    var ovNext: Int get() = getI("ov_next", 5).coerceIn(3, 5); set(v) = putI("ov_next", v.coerceIn(3, 5))
    var ovKeyboard: Boolean get() = getB("ov_kb", true); set(v) = putB("ov_kb", v)
    var ovWarn: Boolean get() = getB("ov_warn", true); set(v) = putB("ov_warn", v)
    var ovVibrate: Boolean get() = getB("ov_vib", true); set(v) = putB("ov_vib", v)
    var ovSnap: Boolean get() = getB("ov_snap", true); set(v) = putB("ov_snap", v)
    var ovX: Int get() = getI("ov_x", -1); set(v) = putI("ov_x", v)
    var ovY: Int get() = getI("ov_y", -1); set(v) = putI("ov_y", v)

    // ── Python / LAB / performans
    var labCheckpoint: Int get() = getI("lab_ckpt", 100).coerceIn(25, 1000); set(v) = putI("lab_ckpt", v.coerceIn(25, 1000))
    var labPauseOnSaver: Boolean get() = getB("lab_pause", true); set(v) = putB("lab_pause", v)
    var labForeground: Boolean get() = getB("lab_fg", true); set(v) = putB("lab_fg", v)
    var labPython: Boolean get() = getB("lab_py", false); set(v) = putB("lab_py", v)
    var pageSize: Int get() = getI("page_size", 100).coerceIn(10, 1000); set(v) = putI("page_size", v.coerceIn(10, 1000))

    // ── veri
    var exportFormat: String get() = getS("export_fmt", "csv"); set(v) = putS("export_fmt", v)
    var autoBackup: Boolean get() = getB("auto_backup", true); set(v) = putB("auto_backup", v)
    var backupKeep: Int get() = getI("backup_keep", 7).coerceIn(1, 30); set(v) = putI("backup_keep", v.coerceIn(1, 30))
    var championVersion: String get() = getS("champion", "v1.0.0"); set(v) = putS("champion", v)

    fun sectors(): Sectors = Sectors.fromBounds(sectorBounds.split(",").mapNotNull { it.trim().toIntOrNull() }) ?: Sectors.DEFAULT
    fun kChoices(): IntArray = if (kMode == 0) intArrayOf(1, 2, 3) else intArrayOf(kMode)

    /** Canlı model yapılandırması. Python kapalıysa kotlinWeight = 1 (Python hiç çağrılmaz). */
    fun brainConfig(): BrainConfig = BrainConfig(
        window = window, decay = decayPm / 1000.0, nCand = nCand, kChoices = kChoices(), dir = Dir.of(dir),
        kotlinWeight = if (!pythonEnabled) 1.0 else if (weightAuto) 0.5 else kotlinPct / 100.0,
        minSample = 50, sectors = sectors(), diversity = diversity
    )
}
