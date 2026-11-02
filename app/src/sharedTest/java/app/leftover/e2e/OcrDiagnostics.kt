package app.leftover.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import app.leftover.data.ScreenshotReader
import app.leftover.domain.PaymentScreenshotParser
import java.io.File
import java.time.LocalDate
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Developer tool, not a test: reads every image in the qa app's files/ocr-in folder (app-private) with the real text reader and writes
 * the raw text and what the parser made of it to files/ocr-out. Run on a phone with `-e ocr true`. Never part of normal runs.
 */
@ScreenshotsOnly
@RunWith(AndroidJUnit4::class)
class OcrDiagnostics {
    @Test fun readEveryImage() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val inDir = File(context.filesDir, "ocr-in")
        val outDir = File(context.filesDir, "ocr-out").apply { mkdirs() }
        inDir.listFiles().orEmpty().filter { it.isFile }.forEach { image ->
            val text = ScreenshotReader.read(context, image)?.text
            val parsed = text?.let { PaymentScreenshotParser.parse(it, LocalDate.now()) }
            File(outDir, image.name + ".txt").writeText("RAW:\n$text\n\nPARSED:\n$parsed\n")
        }
    }
}
