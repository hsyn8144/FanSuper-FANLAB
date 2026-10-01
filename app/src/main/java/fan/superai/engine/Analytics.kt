package fan.superai.engine

import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Fan LAB istatistikleri. Bu sinif UI'dan bagimsizdir ve yalnizca kronolojik
 * tahmin kayitlari uzerinde calisir. Amac model sayisini artirmak degil,
 * mevcut motorun gercekten ne kadar bilgi tasidigini olcmektir.
 */
data class ResearchReport(
    val n: Int,
    val top1: Double,
    val top2: Double,
    val logLoss: Double,
    val brier: Double,
    val entropy: Double,
    val calibrationGap: Double,
    val bootstrapLo: Double,
    val bootstrapHi: Double,
    val permutationP: Double,
    val permutationPercentile: Double,
    val randomBaseline: Double,
    val memberDisagreement: Double,
    val memberDiversity: Double,
    val walkForwardTop1: Double,
    val walkForwardTop2: Double,
    val recentTop1: Double,
    val recentLogLoss: Double,
    val status: String
)

object ResearchLab {
    fun analyze(logs: List<StepLog>, bootstrap: Int = 1000, permutations: Int = 1000, seed: Int = 8144): ResearchReport {
        if (logs.isEmpty()) return ResearchReport(
            n=0, top1=0.0, top2=0.0, logLoss=0.0, brier=0.0, entropy=0.0, calibrationGap=0.0,
            bootstrapLo=0.0, bootstrapHi=0.0, permutationP=1.0, permutationPercentile=0.0, randomBaseline=0.25,
            memberDisagreement=0.0, memberDiversity=0.0, walkForwardTop1=0.0, walkForwardTop2=0.0,
            recentTop1=0.0, recentLogLoss=0.0, status="Veri bekleniyor")
        val xs = logs
        val n = xs.size
        val top1 = xs.count { it.top1 }.toDouble()/n
        val top2 = xs.count { it.top2 }.toDouble()/n
        val ll = xs.sumOf { -ln(it.probs[it.actual].coerceAtLeast(1e-9)) }/n
        val brier = xs.sumOf { logBrier(it.probs, it.actual) }/n
        val entropy = xs.sumOf { entropy(it.probs) }/n
        val gap = calibrationGap(xs)
        val rnd = Random(seed)
        val boots = DoubleArray(bootstrap)
        repeat(bootstrap) {
            var hit=0
            repeat(n) { if (xs[rnd.nextInt(n)].top1) hit++ }
            boots[it]=hit.toDouble()/n
        }
        boots.sort()
        val lo=boots[(bootstrap*0.025).toInt().coerceIn(0,bootstrap-1)]
        val hi=boots[(bootstrap*0.975).toInt().coerceIn(0,bootstrap-1)]
        val actuals=xs.map { it.actual }
        val preds=xs.map { it.probs.argmax() }
        var ge=0
        repeat(permutations) {
            val a=actuals.shuffled(rnd)
            var hit=0
            for (i in 0 until n) if (preds[i]==a[i]) hit++
            if (hit.toDouble()/n >= top1) ge++
        }
        val p=(ge+1.0)/(permutations+1.0)
        val percentile=1.0-p
        val dis=memberDisagreement(xs)
        val diversity=memberDiversity(xs)
        val wf=walkForward(xs)
        val recent=xs.takeLast(minOf(100,n))
        val recentTop=recent.count { it.top1 }.toDouble()/recent.size
        val recentLl=recent.sumOf { -ln(it.probs[it.actual].coerceAtLeast(1e-9)) }/recent.size
        val status=when { n<50 -> "Ornek az"; percentile>=0.99 -> "Guclu istatistiksel ayrisma"; percentile>=0.95 -> "Anlamli sinyal"; else -> "Kanıt sinirli" }
        return ResearchReport(n,top1,top2,ll,brier,entropy,gap,lo,hi,p,percentile,0.25,dis,diversity,wf.first,wf.second,recentTop,recentLl,status)
    }

    private fun walkForward(xs: List<StepLog>): Pair<Double,Double> {
        // Tahminler motorun zaten kronolojik olarak, gercek sonuc gorulmeden
        // once uretilmis kayitlari oldugu icin bu seri leakage-free walk-forward
        // degerlendirme olarak kullanilir. Ilk silentFirst adimlari log'a girmez.
        if (xs.isEmpty()) return 0.0 to 0.0
        var h1=0; var h2=0
        xs.forEach {
            val o=it.probs.indices.sortedByDescending { k -> it.probs[k] }
            if (o[0]==it.actual) h1++
            if (o[0]==it.actual || o[1]==it.actual) h2++
        }
        return h1.toDouble()/xs.size to h2.toDouble()/xs.size
    }

    private fun memberDisagreement(xs: List<StepLog>): Double {
        var total=0; var disagree=0
        xs.forEach { x ->
            val a=x.memberProbs.map { it.argmax() }.distinct().size
            if (a>1) disagree++
            total++
        }
        return if(total==0) 0.0 else disagree.toDouble()/total
    }

    private fun memberDiversity(xs: List<StepLog>): Double {
        var sum=0.0; var count=0
        xs.forEach { x ->
            for(i in x.memberProbs.indices) for(j in i+1 until x.memberProbs.size) {
                sum += js(x.memberProbs[i], x.memberProbs[j]); count++
            }
        }
        return if(count==0) 0.0 else sum/count
    }

    private fun calibrationGap(xs: List<StepLog>): Double {
        val bins=Array(10) { mutableListOf<Boolean>() }
        xs.forEach {
            val c=it.probs.maxOrNull() ?: 0.25
            val b=((c*10).toInt()).coerceIn(0,9)
            bins[b].add(it.probs.argmax()==it.actual)
        }
        var weighted=0.0
        bins.forEachIndexed { i,b -> if(b.isNotEmpty()) weighted += b.size.toDouble()/xs.size* kotlin.math.abs(b.count{it}.toDouble()/b.size-(i+0.5)/10.0) }
        return weighted
    }

    private fun logBrier(p: DoubleArray, a: Int): Double {
        var s=0.0
        for(i in p.indices) { val y=if(i==a)1.0 else 0.0; s+=(p[i]-y)*(p[i]-y) }
        return s
    }

    private fun entropy(p: DoubleArray): Double = -p.sumOf { q -> val x=q.coerceAtLeast(1e-12); x*ln(x) }

    private fun js(a: DoubleArray,b: DoubleArray): Double {
        val m=DoubleArray(a.size){(a[it]+b[it])/2.0}
        return 0.5*kl(a,m)+0.5*kl(b,m)
    }
    private fun kl(a: DoubleArray,b: DoubleArray): Double = a.indices.sumOf { i -> val x=a[i].coerceAtLeast(1e-12); x*ln(x/b[i].coerceAtLeast(1e-12)) }
    private fun DoubleArray.argmax(): Int = indices.maxByOrNull { this[it] } ?: 0
}
