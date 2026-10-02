import fan.lightningroulette.core.*
import java.io.File

/** Örnek veri setini (2000 sentetik spin) üretir: app/src/main/assets/sample_2000.csv */
fun main(args: Array<String>) {
    val out = File(args.getOrElse(0) { "app/src/main/assets/sample_2000.csv" })
    out.parentFile.mkdirs()
    out.writeText(Exporter.csv(SampleData.generate()), Charsets.UTF_8)
    println("yazıldı: ${out.path} (${out.length()} bayt)")
}
