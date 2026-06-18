package tiiehenry.android.app.snapshot.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import tiiehenry.android.app.snapshot.R
import tiiehenry.android.app.snapshot.app.tag.AppTag

/**
 * 标签过滤器布局
 * 用于显示和选择标签进行过滤
 */
class TagsFilterLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val chipGroup: ChipGroup
    private val selectedTagIds = mutableSetOf<String>()
    private var onTagSelectionChangedListener: ((Set<String>) -> Unit)? = null
    private var suppressSelectionCallback = false

    init {
        orientation = VERTICAL
        val view = LayoutInflater.from(context).inflate(R.layout.layout_tags_filter, this, true)
        chipGroup = view.findViewById(R.id.chip_group_tags)
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

    /**
     * 设置标签列表
     * @param tags 标签列表
     * @param clearSelection 是否清除之前的选中状态，默认为true
     */
    fun setTags(tags: List<AppTag>, clearSelection: Boolean = true) {
        // 如果需要，先清除选中状态
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
        } finally {
            suppressSelectionCallback = false
        }

    }

    /**
     * 创建Chip视图
     */
    private fun createChip(tag: AppTag): Chip {
        val chipContext = ContextThemeWrapper(context, R.style.Widget_AppSnapshot_Chip_Tag)
        return Chip(chipContext).apply {
            id = View.generateViewId()
            text = tag.name
            isCheckable = true
            isChecked = selectedTagIds.contains(tag.id)
        }
    }

    /**
     * 获取选中的标签ID集合
     */
    fun getSelectedTagIds(): Set<String> {
        return selectedTagIds.toSet()
    }

    /**
     * 设置选中的标签ID
     */
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

    /**
     * 清除所有选中
     */
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

    /**
     * 设置标签选择变化监听器
     */
    fun setOnTagSelectionChangedListener(listener: (Set<String>) -> Unit) {
        onTagSelectionChangedListener = listener
    }
}