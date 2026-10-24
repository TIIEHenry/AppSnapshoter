package tiiehenry.android.app.snapshot.ui.widget

import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import eightbitlab.com.blurview.BlurAlgorithm
import eightbitlab.com.blurview.BlurView
import eightbitlab.com.blurview.RenderEffectBlur
import eightbitlab.com.blurview.RenderScriptBlur
import tiiehenry.android.app.snapshot.R

/**
 * 悬浮底栏的毛玻璃层。
 *
 * 玻璃不是透明窗口：底栏本身是一块 82% 不透明的 surface 叠色，模糊只用来
 * 让下方内容透出一点轮廓，保证 12sp 标签和图标始终有足够对比度。
 */
object FloatingBottomNav {

    fun setup(activity: FragmentActivity, container: BlurView) {
        if (container.getTag(R.id.tag_floating_nav_setup_done) == true) return

        val contentRoot = activity.findViewById<ViewGroup>(R.id.coordinator)

        container.post {
            if (container.width <= 0 || container.height <= 0) return@post
            if (container.getTag(R.id.tag_floating_nav_setup_done) == true) return@post

            applyBlur(activity, container, contentRoot)
            container.setTag(R.id.tag_floating_nav_setup_done, true)
        }
    }

    private fun applyBlur(
        activity: FragmentActivity,
        container: BlurView,
        contentRoot: ViewGroup,
    ) {
        val blurRadius = activity.resources.getDimension(R.dimen.floating_nav_blur_radius)
        val algorithm = createBlurAlgorithm(activity)

        container.setBackgroundResource(R.drawable.bg_floating_nav_clip)
        container.outlineProvider = ViewOutlineProvider.BACKGROUND
        container.clipToOutline = true

        // 采样根用页面底色清屏，避免边缘出现未采样的黑边。
        val frameClear = ColorDrawable(ContextCompat.getColor(activity, R.color.background))
        container.setupWith(contentRoot, algorithm)
            .setFrameClearDrawable(frameClear)
            .setBlurRadius(blurRadius)
            .setBlurAutoUpdate(true)
            .setOverlayColor(ContextCompat.getColor(activity, R.color.bottom_nav_glass_fill))
    }

    private fun createBlurAlgorithm(activity: FragmentActivity): BlurAlgorithm {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            RenderEffectBlur()
        } else {
            RenderScriptBlur(activity)
        }
    }
}
