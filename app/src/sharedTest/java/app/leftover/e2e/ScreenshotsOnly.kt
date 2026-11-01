package app.leftover.e2e

/** Marks tools that make website screenshots. Normal phone runs leave them out (see app/build.gradle.kts). */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class ScreenshotsOnly
