package tiiehenry.android.app.snapshot.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import tiiehenry.android.app.snapshot.R
import kotlin.math.abs
import kotlin.math.min

/**
 * wrap_content 高度上限。超出后自身滚动；顶/底渐隐表示还能滚；
 * 按下先拦住父列表，滑到尽头再把竖滑交还外层。
 */
class MaxHeightRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : RecyclerView(context, attrs, defStyleAttr) {

    var maxHeightPx: Int = Int.MAX_VALUE
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }

    private val fadeLengthPx = resources.getDimensionPixelSize(R.dimen.group_app_grid_fade_length)
    private val fadePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var cachedFadeW = 0
    private var cachedFadeH = 0
    private var cachedFadeColor = 0
    private var topFadeShader: Shader? = null
    private var bottomFadeShader: Shader? = null

    init {
        isNestedScrollingEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        isVerticalScrollBarEnabled = true
        isScrollbarFadingEnabled = true
        scrollBarStyle = SCROLLBARS_INSIDE_OVERLAY
        setWillNotDraw(false)
        addOnItemTouchListener(ParentScrollHandoff())
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val max = maxHeightPx
        if (max == Int.MAX_VALUE) {
            super.onMeasure(widthSpec, heightSpec)
            return
        }
        val mode = View.MeasureSpec.getMode(heightSpec)
        val size = View.MeasureSpec.getSize(heightSpec)
        val cappedSpec = when (mode) {
            View.MeasureSpec.EXACTLY ->
                View.MeasureSpec.makeMeasureSpec(min(size, max), View.MeasureSpec.EXACTLY)
            View.MeasureSpec.AT_MOST ->
                View.MeasureSpec.makeMeasureSpec(min(size, max), View.MeasureSpec.AT_MOST)
            else ->
                View.MeasureSpec.makeMeasureSpec(max, View.MeasureSpec.AT_MOST)
        }
        super.onMeasure(widthSpec, cappedSpec)
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        drawScrollFades(canvas)
    }

    private fun drawScrollFades(canvas: Canvas) {
        val len = fadeLengthPx
        if (len <= 0 || width == 0 || height == 0) return
        val showTop = canScrollVertically(-1)
        val showBottom = canScrollVertically(1)
        if (!showTop && !showBottom) return

        val color = ContextCompat.getColor(context, R.color.surface)
        ensureFadeShaders(width, height, color, len)

        if (showTop) {
            fadePaint.shader = topFadeShader
            canvas.drawRect(0f, 0f, width.toFloat(), len.toFloat(), fadePaint)
        }
        if (showBottom) {
            fadePaint.shader = bottomFadeShader
            canvas.drawRect(0f, (height - len).toFloat(), width.toFloat(), height.toFloat(), fadePaint)
        }
        fadePaint.shader = null
    }

    private fun ensureFadeShaders(w: Int, h: Int, color: Int, len: Int) {
        if (w == cachedFadeW && h == cachedFadeH && color == cachedFadeColor &&
            topFadeShader != null && bottomFadeShader != null
        ) {
            return
        }
        cachedFadeW = w
        cachedFadeH = h
        cachedFadeColor = color
        val transparent = ColorUtils.setAlphaComponent(color, 0)
        val mid = ColorUtils.setAlphaComponent(color, 0x99)
        val colors = intArrayOf(color, mid, transparent)
        val stops = floatArrayOf(0f, 0.4f, 1f)
        topFadeShader = LinearGradient(
            0f, 0f, 0f, len.toFloat(), colors, stops, Shader.TileMode.CLAMP
        )
        val bottomTop = (h - len).toFloat()
        bottomFadeShader = LinearGradient(
            0f, bottomTop, 0f, h.toFloat(),
            intArrayOf(transparent, mid, color),
            stops,
            Shader.TileMode.CLAMP
        )
    }

    private inner class ParentScrollHandoff : SimpleOnItemTouchListener() {
        private var downY = 0f
        private var lastY = 0f
        private var decided = false

        override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downY = e.y
                    lastY = e.y
                    decided = false
                    if (canScrollInner()) {
                        rv.parent.requestDisallowInterceptTouchEvent(true)
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!canScrollInner()) {
                        rv.parent.requestDisallowInterceptTouchEvent(false)
                        return false
                    }
                    val dy = e.y - lastY
                    lastY = e.y
                    if (!decided) {
                        if (abs(e.y - downY) < touchSlop) return false
                        decided = true
                    }
                    if (abs(dy) < 1f) return false
                    val direction = if (dy > 0f) -1 else 1
                    rv.parent.requestDisallowInterceptTouchEvent(rv.canScrollVertically(direction))
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    rv.parent.requestDisallowInterceptTouchEvent(false)
                    decided = false
                }
            }
            return false
        }

        private fun canScrollInner(): Boolean =
            canScrollVertically(1) || canScrollVertically(-1)
    }
}
