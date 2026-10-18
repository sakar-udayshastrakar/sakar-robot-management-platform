package com.sakarrobotics.installationassistant.export

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.sakarrobotics.installation.core.logic.DocLine
import com.sakarrobotics.installation.core.logic.LineStyle
import com.sakarrobotics.installation.core.logic.ReportDocument
import java.io.IOException
import java.io.OutputStream

/** What was drawn: lets callers (and tests) check the PDF without parsing it. */
data class PdfRenderResult(val pageCount: Int, val drawnLines: List<String>)

/**
 * Draws a [ReportDocument] into an A4 PDF using the platform [PdfDocument] (no third-party library, no
 * network). Every page carries the SIMULATION ONLY banner at the top and the full notice at the bottom, so
 * no single page can be separated from its disclaimer.
 */
class PdfReportRenderer {

    private class Placed(val text: String, val x: Float, val y: Float, val paint: Paint)

    private class Page {
        val items = mutableListOf<Placed>()
    }

    private val orange = Color.parseColor("#FF6100")
    private val orangeSoft = Color.parseColor("#FFE0CC")
    private val ink = Color.parseColor("#0F172A")
    private val muted = Color.parseColor("#475569")
    private val warning = Color.parseColor("#92400E")

    private fun paint(size: Float, color: Int, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = if (bold) Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) else Typeface.SANS_SERIF
    }

    private val titlePaint = paint(22f, ink, bold = true)
    private val subtitlePaint = paint(11f, muted)
    private val headingPaint = paint(13.5f, orange, bold = true)
    private val bodyPaint = paint(10.5f, ink)
    private val mutedPaint = paint(10f, muted)
    private val warningPaint = paint(10.5f, warning)
    private val requiredPaint = paint(10.5f, ink)
    private val bannerPaint = paint(12.5f, Color.parseColor("#7C2D12"), bold = true)
    private val footerPaint = paint(8.5f, muted)
    private val rulePaint = Paint().apply { color = orange; strokeWidth = 1.2f }
    private val bandPaint = Paint().apply { color = orange }
    private val boxFill = Paint().apply { color = orangeSoft }
    private val boxStroke = Paint().apply { color = orange; style = Paint.Style.STROKE; strokeWidth = 1.5f }
    private val brandPaint = paint(11f, Color.WHITE, bold = true)

    private fun paintFor(style: LineStyle) = when (style) {
        LineStyle.HEADING -> headingPaint
        LineStyle.BODY -> bodyPaint
        LineStyle.MUTED -> mutedPaint
        LineStyle.WARNING -> warningPaint
        LineStyle.ITEM_REQUIRED -> requiredPaint
        LineStyle.ITEM_OPTIONAL -> mutedPaint
    }

    /** Throws [IOException] if the PDF cannot be written. */
    @Throws(IOException::class)
    fun render(doc: ReportDocument, out: OutputStream): PdfRenderResult {
        val pages = layout(doc)
        val pdf = PdfDocument()
        val drawn = mutableListOf<String>()
        try {
            pages.forEachIndexed { index, page ->
                val info = PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, index + 1).create()
                val pdfPage = pdf.startPage(info)
                drawChrome(pdfPage.canvas, doc, index + 1, pages.size, drawn)
                page.items.forEach { item ->
                    pdfPage.canvas.drawText(item.text, item.x, item.y, item.paint)
                    drawn += item.text
                }
                pdf.finishPage(pdfPage)
            }
            pdf.writeTo(out)
            out.flush()
        } finally {
            pdf.close()
        }
        return PdfRenderResult(pages.size, drawn)
    }

    // ------------------------------------------------------------------ layout

    private fun layout(doc: ReportDocument): List<Page> {
        val pages = mutableListOf(Page())
        var y = FIRST_PAGE_TOP
        val maxWidth = (PAGE_W - 2 * MARGIN).toFloat()

        fun newPage() {
            pages += Page()
            y = CONTINUATION_TOP
        }

        fun place(text: String, paint: Paint, lineHeight: Float, indentX: Float = 0f) {
            if (y + lineHeight > BODY_BOTTOM) newPage()
            pages.last().items += Placed(text, MARGIN + indentX, y, paint)
            y += lineHeight
        }

        place(doc.title, titlePaint, 28f)
        place(doc.subtitle, subtitlePaint, 20f)
        y += 6f

        doc.sections.forEach { section ->
            // Keep a heading together with at least its first line.
            if (y + 22f + 15f > BODY_BOTTOM) newPage()
            y += 8f
            place(section.title, headingPaint, 18f)
            section.lines.forEach { line -> placeWrapped(line, maxWidth, ::place) }
        }
        return pages
    }

    private fun placeWrapped(line: DocLine, maxWidth: Float, place: (String, Paint, Float, Float) -> Unit) {
        val paint = paintFor(line.style)
        val indentChars = line.text.takeWhile { it == ' ' }.length
        val indent = indentChars * 3.2f
        val body = line.text.trimStart()
        wrap(body, paint, maxWidth - indent).forEachIndexed { i, part ->
            place(part, paint, 14.5f, if (i == 0) indent else indent + 8f)
        }
    }

    /** Greedy word wrap; words longer than a line are split by character. */
    private fun wrap(text: String, paint: Paint, width: Float): List<String> {
        if (text.isEmpty()) return listOf("")
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (word in text.split(' ')) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= width) {
                current = StringBuilder(candidate)
                continue
            }
            if (current.isNotEmpty()) lines += current.toString()
            var rest = word
            while (paint.measureText(rest) > width) {
                val fit = paint.breakText(rest, true, width, null).coerceAtLeast(1)
                lines += rest.substring(0, fit)
                rest = rest.substring(fit)
            }
            current = StringBuilder(rest)
        }
        if (current.isNotEmpty()) lines += current.toString()
        return lines
    }

    // ------------------------------------------------------------------ per-page chrome

    private fun drawChrome(canvas: Canvas, doc: ReportDocument, pageNumber: Int, pageCount: Int, drawn: MutableList<String>) {
        canvas.drawRect(0f, 0f, PAGE_W.toFloat(), 26f, bandPaint)
        canvas.drawText("Sakar Robotics", MARGIN.toFloat(), 17f, brandPaint)
        val product = "Robot Installation Assistant"
        canvas.drawText(product, PAGE_W - MARGIN - brandPaint.measureText(product), 17f, brandPaint)

        // Prominent simulation banner on every page.
        val box = RectF(MARGIN.toFloat(), 36f, (PAGE_W - MARGIN).toFloat(), 64f)
        canvas.drawRoundRect(box, 6f, 6f, boxFill)
        canvas.drawRoundRect(box, 6f, 6f, boxStroke)
        val bannerX = box.centerX() - bannerPaint.measureText(doc.banner) / 2f
        canvas.drawText(doc.banner, bannerX, 54f, bannerPaint)
        drawn += doc.banner

        // Footer: full notice, page number.
        canvas.drawLine(MARGIN.toFloat(), FOOTER_TOP, (PAGE_W - MARGIN).toFloat(), FOOTER_TOP, rulePaint)
        var fy = FOOTER_TOP + 12f
        wrap(doc.footerNotice, footerPaint, (PAGE_W - 2 * MARGIN).toFloat()).take(3).forEach {
            canvas.drawText(it, MARGIN.toFloat(), fy, footerPaint)
            drawn += it
            fy += 10.5f
        }
        val pageText = "Page $pageNumber of $pageCount"
        canvas.drawText(pageText, PAGE_W - MARGIN - footerPaint.measureText(pageText), PAGE_H - 22f, footerPaint)
        drawn += pageText
    }

    private companion object {
        const val PAGE_W = 595
        const val PAGE_H = 842
        const val MARGIN = 40
        const val FIRST_PAGE_TOP = 96f
        const val CONTINUATION_TOP = 90f
        const val FOOTER_TOP = 770f
        const val BODY_BOTTOM = 760f
    }
}
