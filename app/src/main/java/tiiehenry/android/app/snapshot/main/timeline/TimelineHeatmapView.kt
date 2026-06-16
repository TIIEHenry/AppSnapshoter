package tiiehenry.android.app.snapshot.main.timeline

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import tiiehenry.android.app.snapshot.R
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class TimelineHeatmapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private data class DayData(val date: LocalDate, val count: Int)

    private var days: List<DayData> = emptyList()
    private var maxCount = 1

    private val cellSize = 28f
    private val cellGap = 3f
    private val cornerRadius = 4f
    private val labelHeight = 24f
    private val topPadding = 8f

    private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 20f
        color = 0xFF888888.toInt()
        textAlign = Paint.Align.CENTER
    }

    private val colorEmpty = 0xFFE8E8E8.toInt()
    private val colorLow = 0xFFC8E6C9.toInt()
    private val colorMedium = 0xFF66BB6A.toInt()
    private val colorHigh = 0xFF2E7D32.toInt()
    private val colorMax = 0xFF1B5E20.toInt()

    private val dateFormatter = DateTimeFormatter.ofPattern("M/d")

    fun setData(entries: List<TimelineEntry>, startDate: LocalDate, endDate: LocalDate) {
        val zone = ZoneId.systemDefault()
        val countByDay = mutableMapOf<LocalDate, Int>()
        var current = startDate
        while (!current.isAfter(endDate)) {
            countByDay[current] = 0
            current = current.plusDays(1)
        }
        for (entry in entries) {
            for (time in entry.matchingArchiveTimes) {
                val day = java.time.Instant.ofEpochMilli(time).atZone(zone).toLocalDate()
                if (day in countByDay) {
                    countByDay[day] = (countByDay[day] ?: 0) + 1
                }
            }
        }
        days = countByDay.entries.sortedBy { it.key }.map { DayData(it.key, it.value) }
        maxCount = days.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
        requestLayout()
        invalidate()
    }

    fun clear() {
        days = emptyList()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = resolveSize(suggestedMinimumWidth, widthMeasureSpec)
        val totalHeight = (topPadding + cellSize + cellGap + labelHeight + 8f).toInt()
        setMeasuredDimension(width, resolveSize(totalHeight, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (days.isEmpty()) return

        val totalCellWidth = cellSize + cellGap
        val availableWidth = width.toFloat()
        val cols = ((availableWidth + cellGap) / totalCellWidth).toInt().coerceAtLeast(1)
        val actualCols = cols.coerceAtMost(days.size)

        val startX = (width - actualCols * totalCellWidth + cellGap) / 2

        for ((i, day) in days.withIndex()) {
            if (i >= actualCols) break
            val x = startX + i * totalCellWidth
            val y = topPadding

            cellPaint.color = getColorForCount(day.count)
            rect.set(x, y, x + cellSize, y + cellSize)
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, cellPaint)

            if (i % 7 == 0 || i == days.size - 1) {
                canvas.drawText(
                    day.date.format(dateFormatter),
                    x + cellSize / 2,
                    y + cellSize + cellGap + 16f,
                    labelPaint
                )
            }
        }
    }

    private fun getColorForCount(count: Int): Int {
        if (count == 0) return colorEmpty
        val ratio = count.toFloat() / maxCount
        return when {
            ratio < 0.25f -> colorLow
            ratio < 0.5f -> colorMedium
            ratio < 0.75f -> colorHigh
            else -> colorMax
        }
    }
}
