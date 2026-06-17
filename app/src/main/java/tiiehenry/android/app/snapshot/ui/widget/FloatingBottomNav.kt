package tiiehenry.android.app.snapshot.ui.widget

import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import eightbitlab.com.blurview.BlurAlgorithm
import eightbitlab.com.blurview.BlurView
import eightbitlab.com.blurview.RenderEffectBlur
import eightbitlab.com.blurview.RenderScriptBlur
import tiiehenry.android.app.snapshot.R

object FloatingBottomNav {

    fun setup(
        activity: FragmentActivity,
        container: BlurView,
        bottomNavigation: BottomNavigationView,
    ) {
        val contentRoot = activity.findViewById<ViewGroup>(R.id.coordinator)
        val listener = object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (container.width <= 0 || container.height <= 0) return
                container.viewTreeObserver.removeOnGlobalLayoutListener(this)
                apply(activity, container, bottomNavigation, contentRoot)
            }
        }
        container.viewTreeObserver.addOnGlobalLayoutListener(listener)
        container.post {
            if (container.width > 0 && container.height > 0) {
                container.viewTreeObserver.removeOnGlobalLayoutListener(listener)
                apply(activity, container, bottomNavigation, contentRoot)
            }
        }
    }

    fun applyCompactWidth(container: BlurView, bottomNavigation: BottomNavigationView) {
        val menuSize = bottomNavigation.menu.size().coerceAtLeast(1)
        val res = container.resources
        val itemWidth = res.getDimensionPixelSize(R.dimen.design_bottom_navigation_item_max_width)
        val horizontalPadding = res.getDimensionPixelSize(R.dimen.floating_nav_horizontal_padding) * 2
        val targetWidth = itemWidth * menuSize + horizontalPadding

        bottomNavigation.layoutParams = bottomNavigation.layoutParams.apply {
            width = targetWidth
        }
        container.layoutParams = container.layoutParams.apply {
            width = targetWidth
        }
    }

    private fun apply(
        activity: FragmentActivity,
        container: BlurView,
        bottomNavigation: BottomNavigationView,
        contentRoot: ViewGroup,
    ) {
        val blurRadius = activity.resources.getDimension(R.dimen.floating_nav_blur_radius)
        val algorithm = createBlurAlgorithm(activity)

        container.setBackgroundResource(R.drawable.bg_floating_nav_clip)
        container.outlineProvider = ViewOutlineProvider.BACKGROUND
        container.clipToOutline = true

        val frameClear = ColorDrawable(ContextCompat.getColor(activity, R.color.background))
        container.setupWith(contentRoot, algorithm)
            .setFrameClearDrawable(frameClear)
            .setBlurRadius(blurRadius)
            .setBlurAutoUpdate(true)
            .setOverlayColor(ContextCompat.getColor(activity, R.color.floating_nav_glass_fill))

        applyCompactWidth(container, bottomNavigation)
    }

    private fun createBlurAlgorithm(activity: FragmentActivity): BlurAlgorithm {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            RenderEffectBlur()
        } else {
            RenderScriptBlur(activity)
        }
    }
}
