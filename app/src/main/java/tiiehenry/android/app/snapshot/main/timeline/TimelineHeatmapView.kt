package tiiehenry.android.app.snapshot.main.timeline

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import tiiehenry.android.app.snapshot.R
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class TimelineHeatmapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private data class DayData(val date: LocalDate, val count: Int)

    private var days: List<DayData> = emptyList()
    private var maxCount = 1
    private var onDayClickListener: ((LocalDate) -> Unit)? = null

    private val cellSize = 24f
    private val cellGap = 3f
    private val cornerRadius = 4f
    private val labelHeight = 20f
    private val topPadding = 8f

    private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 18f
        textAlign = Paint.Align.CENTER
    }

    private val colorEmpty = ContextCompat.getColor(context, R.color.timeline_heatmap_empty)
    private val colorLow = ContextCompat.getColor(context, R.color.timeline_heatmap_low)
    private val colorMedium = ContextCompat.getColor(context, R.color.timeline_heatmap_medium)
    private val colorHigh = ContextCompat.getColor(context, R.color.timeline_heatmap_high)
    private val colorMax = ContextCompat.getColor(context, R.color.timeline_heatmap_max)

    private val dateFormatter = DateTimeFormatter.ofPattern("M/d", Locale.getDefault())
    private val monthFormatter = DateTimeFormatter.ofPattern("M月", Locale.getDefault())

    fun setOnDayClickListener(listener: ((LocalDate) -> Unit)?) {
        onDayClickListener = listener
    }

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
        val dayCount = days.size.coerceAtLeast(1)
        val totalCellWidth = cellSize + cellGap
        val contentWidth = (dayCount * totalCellWidth - cellGap).toInt() + paddingLeft + paddingRight
        val width = resolveSize(contentWidth, widthMeasureSpec)
        val totalHeight = (topPadding + cellSize + cellGap + labelHeight + 8f).toInt()
        setMeasuredDimension(width, resolveSize(totalHeight, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (days.isEmpty()) return

        labelPaint.color = ContextCompat.getColor(context, R.color.timeline_heatmap_label)
        val totalCellWidth = cellSize + cellGap
        val startX = paddingLeft.toFloat()

        var lastMonthLabel: Int? = null
        for ((i, day) in days.withIndex()) {
            val x = startX + i * totalCellWidth
            val y = topPadding

            cellPaint.color = getColorForCount(day.count)
            rect.set(x, y, x + cellSize, y + cellSize)
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, cellPaint)

            val month = day.date.monthValue
            val showLabel = i == 0 || i == days.size - 1 ||
                day.date.dayOfMonth == 1 ||
                lastMonthLabel != month
            if (showLabel) {
                lastMonthLabel = month
                val label = if (day.date.dayOfMonth == 1 && i > 0) {
                    day.date.format(monthFormatter)
                } else {
                    day.date.format(dateFormatter)
                }
                canvas.drawText(
                    label,
                    x + cellSize / 2,
                    y + cellSize + cellGap + 14f,
                    labelPaint
                )
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (onDayClickListener == null || days.isEmpty()) return super.onTouchEvent(event)
        if (event.action == MotionEvent.ACTION_UP) {
            val totalCellWidth = cellSize + cellGap
            val index = ((event.x - paddingLeft) / totalCellWidth).toInt()
            if (index in days.indices) {
                onDayClickListener?.invoke(days[index].date)
                return true
            }
        }
        return super.onTouchEvent(event)
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
