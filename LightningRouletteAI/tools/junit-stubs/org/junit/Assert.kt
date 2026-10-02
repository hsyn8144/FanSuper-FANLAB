package org.junit

object Assert {
    @JvmStatic fun assertTrue(c: Boolean) { if (!c) throw AssertionError("assertTrue başarısız") }
    @JvmStatic fun assertTrue(m: String, c: Boolean) { if (!c) throw AssertionError(m) }
    @JvmStatic fun assertFalse(c: Boolean) { if (c) throw AssertionError("assertFalse başarısız") }
    @JvmStatic fun assertFalse(m: String, c: Boolean) { if (c) throw AssertionError(m) }
    @JvmStatic fun assertEquals(a: Any?, b: Any?) { if (a != b) throw AssertionError("beklenen <$a> gerçek <$b>") }
    @JvmStatic fun assertEquals(m: String, a: Any?, b: Any?) { if (a != b) throw AssertionError("$m: beklenen <$a> gerçek <$b>") }
    @JvmStatic fun assertEquals(a: Long, b: Long) { if (a != b) throw AssertionError("beklenen <$a> gerçek <$b>") }
    @JvmStatic fun assertEquals(a: Double, b: Double, eps: Double) { if (Math.abs(a - b) > eps) throw AssertionError("beklenen <$a> gerçek <$b> (±$eps)") }
    @JvmStatic fun assertEquals(m: String, a: Double, b: Double, eps: Double) { if (Math.abs(a - b) > eps) throw AssertionError("$m: beklenen <$a> gerçek <$b> (±$eps)") }
    @JvmStatic fun assertNotNull(a: Any?) { if (a == null) throw AssertionError("null olmamalıydı") }
    @JvmStatic fun assertNull(a: Any?) { if (a != null) throw AssertionError("null olmalıydı: $a") }
    @JvmStatic fun fail(m: String) { throw AssertionError(m) }
}
