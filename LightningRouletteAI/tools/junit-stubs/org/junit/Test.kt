package org.junit

/** Yalnızca YEREL derleme için: gerçek JUnit CI'da kullanılır (bu klasör Gradle'a dahil değildir). */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class Test
