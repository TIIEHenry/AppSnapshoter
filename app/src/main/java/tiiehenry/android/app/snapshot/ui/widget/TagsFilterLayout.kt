package tiiehenry.android.app.snapshot.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import tiiehenry.android.app.snapshot.R
import tiiehenry.android.app.snapshot.app.tag.AppTag

/**
 * 标签过滤器布局
 * 默认单行横向滚动，点击展开后换行显示全部标签
 */
class TagsFilterLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val chipContainer: FrameLayout
    private val scrollContainer: HorizontalScrollView
    private val chipGroup: ChipGroup
    private val toggleButton: ImageButton
    private val selectedTagIds = mutableSetOf<String>()
    private var onTagSelectionChangedListener: ((Set<String>) -> Unit)? = null
    private var suppressSelectionCallback = false
    private var isExpanded = false

    init {
        orientation = VERTICAL
        val view = LayoutInflater.from(context).inflate(R.layout.layout_tags_filter, this, true)
        chipContainer = view.findViewById(R.id.tags_chip_container)
        scrollContainer = view.findViewById(R.id.tags_scroll_container)
        chipGroup = view.findViewById(R.id.chip_group_tags)
        toggleButton = view.findViewById(R.id.btn_toggle_tags)
        toggleButton.setOnClickListener {
            isExpanded = !isExpanded
            updateExpandState()
        }
        chipGroup.setOnCheckedStateChangeListener { _, checkedChipIds ->
            if (suppressSelectionCallback) return@setOnCheckedStateChangeListener
            selectedTagIds.clear()
            checkedChipIds.forEach { chipId ->
                chipIdToTagId[chipId]?.let { selectedTagIds.add(it) }
            }
            onTagSelectionChangedListener?.invoke(selectedTagIds.toSet())
        }
    }

    // ChipGroup 要求每个可勾选 Chip 拥有唯一 id，否则无法正确切换选中状态
    private val chipIdToTagId = mutableMapOf<Int, String>()
    private val tagIdToChipId = mutableMapOf<String, Int>()

    fun setTags(tags: List<AppTag>, clearSelection: Boolean = true) {
        if (clearSelection) {
            selectedTagIds.clear()
        }

        suppressSelectionCallback = true
        try {
            chipGroup.removeAllViews()
            chipIdToTagId.clear()
            tagIdToChipId.clear()

            for (tag in tags) {
                val chip = createChip(tag)
                chipGroup.addView(chip)
                chipIdToTagId[chip.id] = tag.id
                tagIdToChipId[tag.id] = chip.id
            }

            isExpanded = false
            updateExpandState()
        } finally {
            suppressSelectionCallback = false
        }
    }

    private fun updateExpandState() {
        val needsToggle = chipGroup.childCount > 1
        if (!needsToggle) {
            toggleButton.visibility = View.GONE
            applyExpandedLayout(expanded = true)
            return
        }

        toggleButton.visibility = View.VISIBLE
        if (isExpanded) {
            toggleButton.setImageResource(R.drawable.ic_chevron_up)
            toggleButton.contentDescription = context.getString(R.string.tags_filter_collapse)
        } else {
            toggleButton.setImageResource(R.drawable.ic_chevron_down)
            toggleButton.contentDescription = context.getString(R.string.tags_filter_expand)
        }
        applyExpandedLayout(expanded = isExpanded)
    }

    private fun applyExpandedLayout(expanded: Boolean) {
        chipGroup.isSingleLine = !expanded
        if (expanded) {
            if (chipGroup.parent == scrollContainer) {
                scrollContainer.removeView(chipGroup)
                chipContainer.addView(
                    chipGroup,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
            }
            scrollContainer.visibility = View.GONE
        } else {
            if (chipGroup.parent != scrollContainer) {
                chipContainer.removeView(chipGroup)
                scrollContainer.addView(
                    chipGroup,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
            }
            scrollContainer.visibility = View.VISIBLE
            scrollContainer.post { scrollContainer.scrollTo(0, 0) }
        }
    }

    private fun createChip(tag: AppTag): Chip {
        val chipContext = ContextThemeWrapper(context, R.style.Widget_AppSnapshot_Chip_Tag)
        return Chip(chipContext).apply {
            id = View.generateViewId()
            text = tag.name
            isCheckable = true
            isChecked = selectedTagIds.contains(tag.id)
        }
    }

    fun getSelectedTagIds(): Set<String> = selectedTagIds.toSet()

    fun setSelectedTagIds(tagIds: Set<String>) {
        selectedTagIds.clear()
        selectedTagIds.addAll(tagIds)

        suppressSelectionCallback = true
        try {
            tagIdToChipId.forEach { (tagId, chipId) ->
                chipGroup.findViewById<Chip>(chipId)?.isChecked = selectedTagIds.contains(tagId)
            }
        } finally {
            suppressSelectionCallback = false
        }
    }

    fun clearSelection() {
        selectedTagIds.clear()
        suppressSelectionCallback = true
        try {
            chipGroup.clearCheck()
        } finally {
            suppressSelectionCallback = false
        }
        onTagSelectionChangedListener?.invoke(emptySet())
    }

    fun setOnTagSelectionChangedListener(listener: (Set<String>) -> Unit) {
        onTagSelectionChangedListener = listener
    }
}
