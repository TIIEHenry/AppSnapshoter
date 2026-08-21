package tiiehenry.android.app.snapshot.main.launch.groupset

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.ViewOutlineProvider
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.widget.ImageViewCompat
import androidx.fragment.app.FragmentManager
import tiiehenry.android.app.snapshot.R
import tiiehenry.android.app.snapshot.SnapshotViewModel
import tiiehenry.android.app.snapshot.databinding.ItemGroupSetBinding
import tiiehenry.android.app.snapshot.main.launch.ArchiveListItem

object GroupSetHeaderBinder {

    fun bind(
        binding: ItemGroupSetBinding,
        item: ArchiveListItem.SetHeader,
        snapshotViewModel: SnapshotViewModel,
        fragmentManager: FragmentManager,
        opaqueBackdrop: Int? = null,
    ) {
        val set = item.set
        val accent = item.accentColor
        binding.setTitle.text = item.name
        binding.setCount.text = binding.root.context.getString(
            R.string.group_set_count_format,
            item.groupCount,
        )
        binding.setExpandIcon.rotation = if (item.expanded) 0f else -90f
        ImageViewCompat.setImageTintList(
            binding.setExpandIcon,
            ColorStateList.valueOf(accent),
        )
        // 中间与分组卡片同色（@color/surface）；吸顶时同样用不透明 surface，避免列表透出
        val fill = opaqueBackdrop
            ?: ContextCompat.getColor(binding.root.context, R.color.surface)
        applyPressableStrokeBackground(binding, fill, accent)

        binding.root.setOnClickListener {
            snapshotViewModel.setGroupSetCollapsed(set.id, collapsed = item.expanded)
        }
        binding.root.setOnLongClickListener {
            GroupSetSettingFragment.newInstance(set.id).show(fragmentManager, GroupSetSettingFragment.TAG)
            true
        }
        binding.btnTune.setOnClickListener {
            GroupSetSettingFragment.newInstance(set.id).show(fragmentManager, GroupSetSettingFragment.TAG)
        }
        binding.btnRefresh.setOnClickListener {
            snapshotViewModel.refreshGroupSet(set.id) { count ->
                Toast.makeText(
                    binding.root.context,
                    binding.root.context.getString(R.string.group_set_refresh_result, count),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    /**
     * 强调色一圈描边 + 中间 [R.color.surface]（与分组卡片同底）。按压缩中间叠 reveal。
     */
    private fun applyPressableStrokeBackground(
        binding: ItemGroupSetBinding,
        fillColor: Int,
        accent: Int,
    ) {
        val strokePx = binding.root.resources.getDimensionPixelSize(R.dimen.group_set_header_hairline)
        val radius = binding.root.resources.getDimension(R.dimen.fluent_corner_radius_overlay)
        val pressedOverlay = ContextCompat.getColor(binding.root.context, R.color.fluent_reveal_pressed)
        val pressedFill = ColorUtils.compositeColors(pressedOverlay, fillColor)
        val selector = StateListDrawable().apply {
            addState(
                intArrayOf(android.R.attr.state_pressed),
                strokedRect(pressedFill, accent, strokePx, radius),
            )
            addState(intArrayOf(), strokedRect(fillColor, accent, strokePx, radius))
        }
        binding.root.background = selector
        binding.root.isClickable = true
        binding.root.isFocusable = true
        binding.root.clipToOutline = true
        binding.root.outlineProvider = ViewOutlineProvider.BACKGROUND
    }

    private fun strokedRect(
        fillColor: Int,
        strokeColor: Int,
        strokePx: Int,
        radius: Float,
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(fillColor)
            setStroke(strokePx, strokeColor)
        }
    }
}
