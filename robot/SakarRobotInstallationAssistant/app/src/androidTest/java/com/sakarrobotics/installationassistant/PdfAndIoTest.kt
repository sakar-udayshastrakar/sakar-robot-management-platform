package com.sakarrobotics.installationassistant

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.sakarrobotics.installation.core.AssistantServices
import com.sakarrobotics.installation.core.data.SimulatedMappingRepository
import com.sakarrobotics.installation.core.logic.ReportDocumentBuilder
import com.sakarrobotics.installation.core.logic.SimulationNotice
import com.sakarrobotics.installation.core.model.ChecklistState
import com.sakarrobotics.installation.core.model.InstallationReport
import com.sakarrobotics.installation.core.model.ReportChecklistItem
import com.sakarrobotics.installation.core.model.ReportFloor
import com.sakarrobotics.installation.core.model.ReportMap
import com.sakarrobotics.installation.core.storage.InMemoryKeyValueStore
import com.sakarrobotics.installationassistant.export.DocumentIo
import com.sakarrobotics.installationassistant.export.ReportExporter
import com.sakarrobotics.installationassistant.export.ReportSharer
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class PdfExportTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = File(context.cacheDir, "reports")
        dir.deleteRecursively()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun realisticReport(): InstallationReport {
        val s = AssistantServices(InMemoryKeyValueStore(), CoroutineScope(Job()), mappingTickMillis = 0, diagnosticsLatencyMillis = 0)
        s.installations.startNew()
        s.installations.acknowledgeOverview()
        s.installations.setSelectedFloors(listOf("floor-ground"))
        s.mapping.startSession("floor-ground")
        (s.mapping as SimulatedMappingRepository).advance(200)
        s.mapping.saveMap("Lobby sweep")
        s.installations.updateChecklist("site-walk", ChecklistState.DONE, "Walked with the site owner")
        s.installations.updateChecklist("wifi", ChecklistState.BLOCKED, "Dead spot near the lift")
        return s.generateReport()
    }

    private fun bigReport(checklistItems: Int, id: String = "RPT-BIG"): InstallationReport = InstallationReport(
        id = id, installationId = "INST-BIG", generatedAtMillis = 1_700_000_000_000L,
        floors = (1..12).map { f -> ReportFloor("Floor $f", (1..3).map { ReportMap("Map $f.$it", "Simulated - complete", 90) }) },
        mappingStatus = "Simulated mapping: 12 of 12 selected floor(s) have a saved simulated map.",
        diagnosticsSummary = "Readiness (simulated): OK - 0 warning(s), 0 failure(s); Sensors (simulated): OK - 0 warning(s), 0 failure(s)",
        checklist = (1..checklistItems).map {
            ReportChecklistItem(
                "Checklist item number $it with a reasonably long title to force wrapping across the line", it % 2 == 0,
                "Done", "A note explaining the situation at length so that this entry needs more than one line on the page $it."
            )
        },
        warnings = (1..30).map { "WARNING: Checklist item still pending: item $it" },
        simulationNotice = SimulationNotice.FULL,
        completionStatus = "Workflow incomplete (simulated) - 3 of 6 steps complete; checklist 0 of $checklistItems resolved."
    )

    private fun render(pdf: File, page: Int): Bitmap {
        ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { renderer ->
                renderer.openPage(page).use { p ->
                    val bmp = Bitmap.createBitmap(p.width, p.height, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    return bmp
                }
            }
        }
    }

    private fun pageCountOf(pdf: File): Int =
        ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { fd -> PdfRenderer(fd).use { it.pageCount } }

    @Test
    fun generatesAValidPdfContainingEverythingTheReportPromises() {
        val report = realisticReport()
        val result = ReportExporter(dir).export(report)
        val exported = result.getOrThrow()

        assertTrue(exported.file.exists())
        assertEquals("%PDF-", exported.file.inputStream().use { s -> ByteArray(5).also { s.read(it) }.toString(Charsets.US_ASCII) })
        assertTrue(exported.file.length() > 2_000)
        assertTrue(exported.render.pageCount >= 1)

        val text = exported.render.drawnLines
        assertTrue(text.contains("SIMULATION ONLY — NOT VALIDATED ON PHYSICAL ROBOT"))
        assertTrue(text.any { it == "Installation ID: ${report.installationId}" })
        assertTrue(text.any { it.startsWith("Generated: ") })
        assertTrue(text.any { it.startsWith("Completion status: ") })
        assertTrue(text.contains("Ground floor"))
        assertTrue(text.any { it.contains("Lobby sweep") })
        assertTrue(text.any { it.contains("Simulated mapping:") })
        assertTrue(text.any { it.contains("Site walkthrough completed") })
        assertTrue(text.any { it.contains("Dead spot near the lift") })
        assertTrue(text.any { it.contains("Outstanding warnings") })
        assertTrue(text.any { it.startsWith("Page 1 of ${exported.render.pageCount}") })
    }

    @Test
    fun theSystemPdfRendererCanOpenItAndTheBannerIsActuallyPainted() {
        val exported = ReportExporter(dir).export(realisticReport()).getOrThrow()
        assertEquals(exported.render.pageCount, pageCountOf(exported.file))

        val bmp = render(exported.file, 0)
        assertEquals(595, bmp.width)
        // Orange brand band along the very top, orange-tinted banner box underneath it.
        val band = bmp.getPixel(300, 8)
        assertTrue("brand band is orange", Color.red(band) > 200 && Color.green(band) in 60..140 && Color.blue(band) < 60)
        val bannerBox = bmp.getPixel(60, 50)
        assertTrue("banner box is tinted, not white", bannerBox != Color.WHITE)
        // Some dark text pixels exist in the body.
        var dark = 0
        for (y in 100 until 700 step 2) for (x in 40 until 550 step 2) {
            val c = bmp.getPixel(x, y)
            if (Color.red(c) < 90 && Color.green(c) < 90 && Color.blue(c) < 120) dark++
        }
        assertTrue("page body has text", dark > 200)
    }

    @Test
    fun aLongReportFlowsOntoSeveralPagesAndEveryPageKeepsItsBannerAndFooter() {
        val exported = ReportExporter(dir).export(bigReport(checklistItems = 120)).getOrThrow()
        val pages = exported.render.pageCount
        assertTrue("expected several pages, got $pages", pages >= 3)
        assertEquals(pages, pageCountOf(exported.file))

        val lines = exported.render.drawnLines
        assertEquals("banner on every page", pages, lines.count { it == "SIMULATION ONLY — NOT VALIDATED ON PHYSICAL ROBOT" })
        (1..pages).forEach { n -> assertTrue("Page $n of $pages", lines.contains("Page $n of $pages")) }
        // Nothing was lost to pagination.
        assertTrue(lines.any { it.contains("Checklist item number 1 ") })
        assertTrue(lines.any { it.contains("Checklist item number 120 ") })
        assertTrue(lines.any { it.contains("item 30") })

        // The last page is not blank.
        val last = render(exported.file, pages - 1)
        var ink = 0
        for (y in 90 until 400) for (x in 40 until 555) {
            val c = last.getPixel(x, y)
            if ((Color.red(c) + Color.green(c) + Color.blue(c)) / 3 < 200) ink++ // any non-white text colour
        }
        assertTrue("last page has content (ink pixels: $ink)", ink > 50)
    }

    @Test
    fun anUnbreakableVeryLongWordDoesNotCrashOrOverflow() {
        val report = bigReport(3).copy(warnings = listOf("WARNING: " + "X".repeat(600)), id = "RPT-LONG")
        val exported = ReportExporter(dir).export(report).getOrThrow()
        assertTrue(exported.render.drawnLines.count { it.startsWith("X") || it.all { c -> c == 'X' } } >= 2)
    }

    @Test
    fun reportsFromThePreviousVersionWithoutACompletionStatusStillExport() {
        val old = realisticReport().copy(completionStatus = null)
        val exported = ReportExporter(dir).export(old).getOrThrow()
        assertTrue(exported.render.drawnLines.any { it.contains("not recorded") })
    }

    @Test
    fun exportingTheSameReportTwiceReplacesTheFileInsteadOfPilingUp() {
        val report = realisticReport()
        val exporter = ReportExporter(dir)
        val first = exporter.export(report).getOrThrow().file
        val second = exporter.export(report).getOrThrow().file
        assertEquals(first, second)
        assertEquals(1, dir.listFiles()!!.count { it.name.endsWith(".pdf") })
        assertFalse(dir.listFiles()!!.any { it.name.endsWith(".part") })
    }

    @Test
    fun theCacheKeepsOnlyAHandfulOfRecentPdfs() {
        val exporter = ReportExporter(dir)
        repeat(15) { exporter.export(bigReport(3, id = "RPT-$it")).getOrThrow() }
        assertTrue(dir.listFiles()!!.count { it.name.endsWith(".pdf") } <= 10)
    }

    @Test
    fun anUnusableExportFolderBecomesAnErrorNotACrash() {
        val notAFolder = File(context.cacheDir, "i-am-a-file").apply { delete(); writeText("x") }
        try {
            val result = ReportExporter(notAFolder).export(realisticReport())
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()!!.message!!.startsWith("The PDF could not be created"))
        } finally {
            notAFolder.delete()
        }
    }

    @Test
    fun theDocumentModelAndThePdfAgreeOnTheBanner() {
        val report = realisticReport()
        val doc = ReportDocumentBuilder.build(report)
        val exported = ReportExporter(dir).export(report).getOrThrow()
        assertTrue(exported.render.drawnLines.contains(doc.banner))
    }

    // ------------------------------------------------------------------ sharing plumbing

    @Test
    fun theFileProviderServesTheGeneratedPdfAndNothingElse() {
        val exported = ReportExporter(dir).export(realisticReport()).getOrThrow()
        val uri = FileProvider.getUriForFile(context, ReportSharer.authority(context), exported.file)
        assertEquals("content", uri.scheme)
        val head = context.contentResolver.openInputStream(uri)!!.use { s -> ByteArray(5).also { s.read(it) }.toString(Charsets.US_ASCII) }
        assertEquals("%PDF-", head)

        // The app's private data files are outside the shared folder and cannot be turned into a URI.
        val secret = File(context.filesDir, "demo-data/floors.v1.json").apply { parentFile!!.mkdirs(); writeText("[]") }
        try {
            FileProvider.getUriForFile(context, ReportSharer.authority(context), secret)
            fail("private data must not be shareable")
        } catch (expected: IllegalArgumentException) {
            // good
        } finally {
            secret.delete()
        }
    }

    @Test
    fun fileNamesAreSafeAndCarryNoPersonalData() {
        val name = ReportExporter.fileName(realisticReport().copy(installationId = "INST/../x y", id = "RPT:1"))
        assertEquals("installation-report-INST_.._x_y-RPT_1.pdf", name)
    }
}

class DocumentIoTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var dir: File
    private val io get() = DocumentIo(context.contentResolver)

    @Before
    fun setUp() {
        dir = File(context.cacheDir, "docio").apply { deleteRecursively(); mkdirs() }
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun textRoundTripsThroughAContentResolverUri() {
        val uri = Uri.fromFile(File(dir, "a.json"))
        val text = "{\"name\":\"Café — été\"}"
        assertTrue(io.writeText(uri, text).isSuccess)
        assertEquals(text, io.readText(uri).getOrThrow())
    }

    @Test
    fun writingReplacesPreviousContentInsteadOfAppending() {
        val uri = Uri.fromFile(File(dir, "a.json"))
        io.writeText(uri, "a very long first version of the file")
        io.writeText(uri, "short")
        assertEquals("short", io.readText(uri).getOrThrow())
    }

    @Test
    fun aByteOrderMarkIsIgnored() {
        val file = File(dir, "bom.json").apply { writeBytes(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "{}".toByteArray()) }
        assertEquals("{}", io.readText(Uri.fromFile(file)).getOrThrow())
    }

    @Test
    fun notTextIsRefusedWithAClearMessage() {
        val file = File(dir, "bin").apply { writeBytes(byteArrayOf(0xFF.toByte(), 0x00, 0xC3.toByte(), 0x28)) }
        val r = io.readText(Uri.fromFile(file))
        assertTrue(r.isFailure)
        assertTrue(r.exceptionOrNull()!!.message!!.contains("not a text file"))
    }

    @Test
    fun oversizedFilesAreRefusedWithoutReadingThemWhole() {
        val file = File(dir, "huge").apply { outputStream().use { o -> val chunk = ByteArray(1 shl 20) { 'a'.code.toByte() }; repeat(6) { o.write(chunk) } } }
        val r = io.readText(Uri.fromFile(file))
        assertTrue(r.isFailure)
        assertTrue(r.exceptionOrNull()!!.message!!.contains("too large"))
    }

    @Test
    fun missingFilesAndUnwritableLocationsFailGracefully() {
        assertTrue(io.readText(Uri.fromFile(File(dir, "nope"))).isFailure)
        assertTrue(io.writeText(Uri.fromFile(File(dir, "no/such/dir/x.json")), "x").isFailure)
        assertNotNull(io.readText(Uri.fromFile(File(dir, "nope"))).exceptionOrNull()!!.message)
    }
}

/**
 * Leaves sample PDFs in `cache/pdf-samples` so a person can look at the real layout (the other tests check
 * content and structure but not how it looks). Nothing asserts on pixels here beyond "it renders".
 */
class PdfSamplesTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun fullInstallation(): InstallationReport {
        val s = AssistantServices(InMemoryKeyValueStore(), CoroutineScope(Job()), mappingTickMillis = 0, diagnosticsLatencyMillis = 0)
        s.installations.startNew()
        s.installations.acknowledgeOverview()
        s.diagnostics.setScenario(com.sakarrobotics.installation.core.model.SimulationScenario.DEGRADED)
        kotlinx.coroutines.runBlocking {
            s.installations.recordReadiness(s.diagnostics.runReadiness())
            s.installations.recordSensors(s.diagnostics.runSensors())
        }
        s.floors.addFloor("Mezzanine")
        s.installations.setSelectedFloors(listOf("floor-ground", "floor-first"))
        listOf("floor-ground" to "Lobby and corridor", "floor-first" to "Upper level").forEach { (floor, name) ->
            s.mapping.startSession(floor)
            (s.mapping as SimulatedMappingRepository).advance(200)
            s.mapping.saveMap(name)
        }
        s.installations.updateChecklist("site-walk", ChecklistState.DONE, "Walked the route with the site owner")
        s.installations.updateChecklist("charger", ChecklistState.DONE, "")
        s.installations.updateChecklist("access", ChecklistState.NOT_APPLICABLE, "Single storey access only")
        s.installations.updateChecklist("wifi", ChecklistState.BLOCKED, "Dead spot near the lift; needs an access point")
        s.installations.markValidationRun()
        return s.generateReport()
    }

    @Test
    fun writeSamplePdfsForVisualReview() {
        val dir = File(context.cacheDir, "pdf-samples").apply { deleteRecursively(); mkdirs() }
        val exporter = ReportExporter(dir)
        val one = exporter.export(fullInstallation()).getOrThrow()
        assertTrue(one.file.length() > 2_000)
        val long = exporter.export(
            fullInstallation().copy(
                id = "RPT-LONG-SAMPLE",
                checklist = (1..60).map { ReportChecklistItem("Sample checklist item $it", it % 3 != 0, "Done", if (it % 4 == 0) "A longer note that wraps onto a second line to show how notes are laid out on the page." else "") },
                warnings = (1..25).map { "WARNING: Sample outstanding warning number $it" }
            )
        ).getOrThrow()
        assertTrue(long.render.pageCount >= 2)

        // PNG previews rendered by the platform's own PDF renderer (2x), for looking at the real result.
        listOf(one.file to "sample-report", long.file to "sample-long").forEach { (pdf, prefix) ->
            ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer ->
                    for (i in 0 until renderer.pageCount) renderer.openPage(i).use { page ->
                        val bmp = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, android.graphics.Matrix().apply { setScale(2f, 2f) }, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        File(dir, "$prefix-page${i + 1}.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
                    }
                }
            }
        }
    }
}
