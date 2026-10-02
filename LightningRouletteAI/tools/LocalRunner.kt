import java.lang.reflect.InvocationTargetException

/** Yerel test koşucusu: @Test işaretli yöntemleri yansıma ile çalıştırır. Kullanım: LocalRunnerKt <sınıf adları...> */
fun main(args: Array<String>) {
    var pass = 0; var fail = 0
    for (cn in args) {
        val c = Class.forName(cn)
        val inst = c.getDeclaredConstructor().newInstance()
        for (m in c.declaredMethods.sortedBy { it.name }) {
            if (m.annotations.none { it.annotationClass.qualifiedName == "org.junit.Test" }) continue
            val t0 = System.nanoTime()
            try {
                m.invoke(inst); pass++
                println("  ✓ ${c.simpleName}.${m.name}  (${(System.nanoTime() - t0) / 1_000_000} ms)")
            } catch (e: InvocationTargetException) {
                fail++
                println("  ✗ ${c.simpleName}.${m.name}: ${e.targetException}")
                e.targetException.stackTrace.take(4).forEach { println("      at $it") }
            }
        }
    }
    println("SONUÇ: $pass geçti, $fail kaldı")
    if (fail > 0) System.exit(1)
}
