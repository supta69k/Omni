package com.example.omni.report

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.omni.domain.HealthReport
import java.io.File
import java.util.Locale

/**
 * Draws the [HealthReport] as an A4 PDF in Omni's own visual language — the lavender-on-track goal
 * bars the dashboard uses, ink text, the alt-row tray — via [android.graphics.pdf] (platform API,
 * zero dependencies).
 *
 * Page 1 carries the header and the five metric summaries with their goal bars; the daily table
 * follows and continues across as many pages as the month needs, repeating its header row. Every
 * page closes with the same footer, and the file lands in cache under [FILE_DIR] for a FileProvider
 * share — nothing leaves the device until the user sends it.
 */
class HealthReportPdfWriter(private val context: Context) {

    /** Writes the report and returns the PDF file (cache dir, [FILE_DIR]). */
    fun write(report: HealthReport): File {
        val document = PdfDocument()
        var cursor = MarginTop.toFloat()
        var pageNumber = 1
        var page = document.startPage(pageInfo(pageNumber))
        var canvas = page.canvas

        fun newPage(): Canvas {
            drawFooter(canvas, pageNumber)
            document.finishPage(page)
            pageNumber += 1
            page = document.startPage(pageInfo(pageNumber))
            canvas = page.canvas
            cursor = MarginTop.toFloat()
            // The NEW canvas must be handed back to the caller: the drawing functions hold their own
            // local `canvas`, and painting on the finished page's canvas is a native SIGSEGV.
            return canvas
        }

        cursor = drawHeader(canvas, report, cursor)
        cursor = drawSummaries(canvas, report, cursor, ::newPage)
        cursor = drawDailyTable(canvas, report, cursor, ::newPage)
        drawFooter(canvas, pageNumber)
        document.finishPage(page)

        val dir = File(context.cacheDir, FILE_DIR).apply { mkdirs() }
        val file = File(dir, "omni-health-report-${report.month}.pdf")
        file.outputStream().use { document.writeTo(it) }
        document.close()
        return file
    }

    private fun pageInfo(number: Int) =
        PdfDocument.PageInfo.Builder(PageWidth, PageHeight, number).create()

    // ---- Header ------------------------------------------------------------------------------------

    private fun drawHeader(canvas: Canvas, report: HealthReport, start: Float): Float {
        var y = start
        canvas.drawText("Omni", MarginLeft.toFloat(), y + WordmarkSize, wordmarkPaint)

        y += WordmarkSize + HeaderGap
        canvas.drawText("Health Report", MarginLeft.toFloat(), y + TitleSize, titlePaint)

        y += TitleSize + SubtitleGap
        canvas.drawText(
            "${report.displayName} · ${report.monthLabel}",
            MarginLeft.toFloat(),
            y + SubtitleSize,
            subtitlePaint,
        )

        y += SubtitleSize + MetaGap
        canvas.drawText(
            "Generated ${report.generatedAtLabel} · not medical advice",
            MarginLeft.toFloat(),
            y + MetaSize,
            metaPaint,
        )

        return y + MetaSize + SectionGap
    }

    // ---- Metric summaries --------------------------------------------------------------------------

    private fun drawSummaries(
        canvas: Canvas,
        report: HealthReport,
        start: Float,
        newPage: () -> Canvas,
    ): Float {
        // A local `var`, rebound on every page break: after `newPage()` the OLD page's canvas is
        // finished, and painting on it is the native crash this writer exists not to have.
        var canvas = canvas
        var y = start
        var sectionDrawn = false

        report.summaries.forEach { metric ->
            val blockHeight = MetricLabelSize + MetricValueGap + MetricValueSize + BarGap + BarHeight + BlockGap
            if (y + blockHeight > PageHeight - MarginBottom) {
                canvas = newPage()
                y = MarginTop.toFloat()
            }
            if (!sectionDrawn) {
                canvas.drawText("Monthly summary", MarginLeft.toFloat(), y + SectionSize, sectionPaint)
                y += SectionSize + SectionGap
                sectionDrawn = true
            }

            canvas.drawText(metric.label, MarginLeft.toFloat(), y + MetricLabelSize, labelPaint)

            y += MetricLabelSize + MetricValueGap
            val goalText = if (metric.goal > 0f) " / ${format(metric.goal)}${metric.unit} goal" else ""
            canvas.drawText(
                "${format(metric.average)}${metric.unit} avg$goalText · ${metric.goalDays}/${metric.loggedDays} days on goal",
                MarginLeft.toFloat(),
                y + MetricValueSize,
                valuePaint,
            )

            y += MetricValueSize + BarGap
            // The dashboard's own bar: track, then the lavender fill at the goal fraction.
            canvas.drawRect(
                RectF(MarginLeft.toFloat(), y, (PageWidth - MarginRight).toFloat(), y + BarHeight),
                trackPaint,
            )
            val fillWidth = (PageWidth - MarginLeft - MarginRight) * metric.progressFraction
            canvas.drawRect(
                RectF(MarginLeft.toFloat(), y, MarginLeft + fillWidth, y + BarHeight),
                fillPaint,
            )

            y += BarHeight + BlockGap
        }
        return y
    }

    // ---- Daily table -------------------------------------------------------------------------------

    private fun drawDailyTable(
        canvas: Canvas,
        report: HealthReport,
        start: Float,
        newPage: () -> Canvas,
    ): Float {
        // Same rule as [drawSummaries]: the local canvas is rebound on every page break, because
        // painting on a finished page's canvas is the native SIGSEGV this writer must never hit.
        var canvas = canvas
        var y = start
        if (y + SectionSize + SectionGap + TableHeaderHeight > PageHeight - MarginBottom) {
            canvas = newPage()
            y = MarginTop.toFloat()
        }
        canvas.drawText("Daily log", MarginLeft.toFloat(), y + SectionSize, sectionPaint)
        y += SectionSize + SectionGap

        y = drawTableHeader(canvas, y)
        report.days.forEachIndexed { index, day ->
            if (y + TableRowHeight > PageHeight - MarginBottom) {
                canvas = newPage()
                y = drawTableHeader(canvas, y)
            }
            if (index % 2 == 1) {
                canvas.drawRect(
                    RectF(MarginLeft.toFloat(), y, (PageWidth - MarginRight).toFloat(), y + TableRowHeight),
                    altRowPaint,
                )
            }
            columns.forEach { column ->
                canvas.drawText(column.value(day), column.x.toFloat(), y + TableRowTextBaseline, tablePaint)
            }
            y += TableRowHeight
        }
        return y
    }

    private fun drawTableHeader(canvas: Canvas, y: Float): Float {
        canvas.drawRect(
            RectF(MarginLeft.toFloat(), y, (PageWidth - MarginRight).toFloat(), y + TableHeaderHeight),
            headerRowPaint,
        )
        columns.forEach { column ->
            canvas.drawText(column.title, column.x.toFloat(), y + TableHeaderTextBaseline, tableHeaderPaint)
        }
        return y + TableHeaderHeight
    }

    // ---- Footer ------------------------------------------------------------------------------------

    private fun drawFooter(canvas: Canvas, pageNumber: Int) {
        val y = (PageHeight - MarginBottom + FooterGap).toFloat()
        canvas.drawText("Omni · not medical advice", MarginLeft.toFloat(), y, footerPaint)
        canvas.drawText("Page $pageNumber", (PageWidth - MarginRight).toFloat(), y, footerPaint)
    }

    private fun format(value: Float): String =
        if (value == value.toLong().toFloat()) {
            value.toLong().toString()
        } else {
            String.format(Locale.US, "%.1f", value)
        }

    // ---- Palette & metrics -------------------------------------------------------------------------

    private val wordmarkPaint = inkPaint(WordmarkSize, bold = true, color = Lavender)
    private val titlePaint = inkPaint(TitleSize, bold = true)
    private val subtitlePaint = inkPaint(SubtitleSize)
    private val metaPaint = inkPaint(MetaSize, color = Gray)
    private val sectionPaint = inkPaint(SectionSize, bold = true)
    private val labelPaint = inkPaint(MetricLabelSize, color = Gray)
    private val valuePaint = inkPaint(MetricValueSize)
    private val tablePaint = inkPaint(TableRowTextSize)
    private val tableHeaderPaint = whitePaint(TableHeaderTextSize, bold = true)
    private val footerPaint = inkPaint(FooterSize, color = Gray)

    private val trackPaint = Paint().apply { color = Track }
    private val fillPaint = Paint().apply { color = Lavender }
    private val altRowPaint = Paint().apply { color = AltRow }
    private val headerRowPaint = Paint().apply { color = Lavender }

    private fun inkPaint(size: Float, bold: Boolean = false, color: Int = Ink) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
        }

    private fun whitePaint(size: Float, bold: Boolean = false) = inkPaint(size, bold, color = Color.WHITE)

    private data class Column(val title: String, val x: Int, val value: (HealthReport.DayRow) -> String)

    private val columns = listOf(
        Column("Date", MarginLeft) { it.dayLabel },
        Column("Steps", StepsX) { it.steps.toString() },
        Column("Sleep", SleepX) { "${format(it.sleepHours)} h" },
        Column("Water", WaterX) { "${it.waterGlasses} gl" },
        Column("Fiber", FiberX) { "${format(it.fiberGrams)} g" },
        Column("Calories", CaloriesX) { it.calories.toString() },
    )

    companion object {
        /** A4 at 72 dpi. */
        const val PageWidth = 595
        const val PageHeight = 842

        const val MarginLeft = 40
        const val MarginRight = 40
        const val MarginTop = 48
        const val MarginBottom = 48

        const val WordmarkSize = 12f
        const val HeaderGap = 14f
        const val TitleSize = 24f
        const val SubtitleGap = 10f
        const val SubtitleSize = 13f
        const val MetaGap = 4f
        const val MetaSize = 9f
        const val SectionSize = 14f
        const val SectionGap = 10f

        const val MetricLabelSize = 10f
        const val MetricValueGap = 3f
        const val MetricValueSize = 12f
        const val BarGap = 4f
        const val BarHeight = 9f
        const val BlockGap = 14f

        const val TableHeaderHeight = 22f
        const val TableHeaderTextSize = 11f
        const val TableHeaderTextBaseline = 15f
        const val TableRowHeight = 20f
        const val TableRowTextSize = 10f
        const val TableRowTextBaseline = 14f
        const val FooterSize = 8f
        const val FooterGap = 14f

        const val FILE_DIR = "health-reports"

        // Column x positions across the 515pt content width.
        const val StepsX = 130
        const val SleepX = 230
        const val WaterX = 310
        const val FiberX = 390
        const val CaloriesX = 470

        private const val Ink = 0xFF302E2E.toInt()
        private const val Lavender = 0xFF8D84F9.toInt()
        private const val Track = 0xFFEEECF5.toInt()
        private const val AltRow = 0xFFF7F6FB.toInt()
        private const val Gray = 0xFF6C6C6C.toInt()
    }
}
