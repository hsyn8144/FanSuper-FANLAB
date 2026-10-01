package fan.superai.v13

/** Python rakam üyelerinin canlı son tahminleri. */
class PyNumberInfo(val ids: List<String>, val names: List<String>, val preds: List<DoubleArray>)

/** Bir tam/kuyruk replay'de yakalanan rakam üyesi dağılımları: [start] kayıt indeksinden itibaren, adım × üye. */
class PyCapture(val ids: List<String>, val start: Int, val steps: List<List<DoubleArray>>)

class PySideStep(val members: List<PySideMember>)

/** Yan meclisi yakalaması: [start] kayıt indeksinden itibaren adım başına üyeler; [next]: bir sonraki tahmin. */
class PySideCapture(val start: Int, val steps: List<PySideStep>, val next: PySideStep)

object PyAdapter {
    fun numberOutputs(info: PyNumberInfo?): List<ModelOutput> {
        if (info == null) return emptyList()
        val out = ArrayList<ModelOutput>()
        for (i in info.ids.indices) {
            val p = info.preds.getOrNull(i) ?: continue
            out += ModelOutput("py_${info.ids[i]}", "Python · ${info.names.getOrElse(i) { info.ids[i] }}", Group.PYTHON, number = p)
        }
        return out
    }

    fun sideOutputs(s: PySideStep?): List<ModelOutput> =
        s?.members?.map { ModelOutput(it.id, "Python yan · ${it.name}", Group.PYTHON, bs = it.bs, oe = it.oe, comb = it.comb) } ?: emptyList()

    /** [n] kayıt için adım başına Python verisi (yakalama yoksa null). */
    fun replayData(n: Int, num: PyCapture?, side: PySideCapture?, names: Map<String, String>): PythonReplayData {
        val steps = ArrayList<PythonStepData?>(n)
        for (i in 0 until n) {
            var ids: List<String> = emptyList(); var nm: List<String> = emptyList(); var dists: List<DoubleArray> = emptyList()
            if (num != null && i >= num.start && i - num.start < num.steps.size) {
                ids = num.ids; nm = num.ids.map { names[it] ?: it }; dists = num.steps[i - num.start]
            }
            var sm: List<PySideMember> = emptyList()
            if (side != null && i >= side.start && i - side.start < side.steps.size) sm = side.steps[i - side.start].members
            steps += if (ids.isEmpty() && sm.isEmpty()) null else PythonStepData(ids, nm, dists, sm)
        }
        return PythonReplayData(steps)
    }
}
