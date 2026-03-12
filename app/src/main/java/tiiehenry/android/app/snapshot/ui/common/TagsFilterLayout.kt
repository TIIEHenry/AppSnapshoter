package tiiehenry.android.app.snapshot.ui.common

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import tiiehenry.android.app.snapshot.R
import tiiehenry.android.app.snapshot.app.AppTag
import tiiehenry.android.app.snapshot.app.TagType

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

    init {
        orientation = VERTICAL
        val view = LayoutInflater.from(context).inflate(R.layout.layout_tags_filter, this, true)
        chipGroup = view.findViewById(R.id.chip_group_tags)

    }

    // 保存tag id到chip的映射
    private val tagIdToChip = mutableMapOf<String, Chip>()

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
        
        chipGroup.removeAllViews()
        tagIdToChip.clear()

        for (tag in tags) {
            val chip = createChip(tag)
            chipGroup.addView(chip)
            tagIdToChip[tag.id] = chip
        }

    }

    /**
     * 创建Chip视图
     */
    private fun createChip(tag: AppTag): Chip {
        return Chip(context).apply {
            text = tag.name
            isCheckable = true
            isChecked = selectedTagIds.contains(tag.id)

            // 根据标签类型设置不同样式
            when (tag.type) {
                TagType.BUILTIN -> {
                    setChipBackgroundColorResource(R.color.chip_builtin_background)
                    setTextColor(ContextCompat.getColor(context, R.color.chip_builtin_text))
                    chipStrokeWidth = if (!isChecked) 0f else 2f
                    setChipStrokeColorResource(R.color.chip_builtin_text)
                }
                TagType.GROUP -> {
                    setChipBackgroundColorResource(R.color.chip_group_background)
                    setTextColor(ContextCompat.getColor(context, R.color.chip_group_text))
                    chipStrokeWidth = if (!isChecked) 0f else 2f
                    setChipStrokeColorResource(R.color.chip_group_text)
                }
            }

            setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    selectedTagIds.add(tag.id)
                    chipStrokeWidth = 2f
                } else {
                    selectedTagIds.remove(tag.id)
                    chipStrokeWidth = 0f
                }
                onTagSelectionChangedListener?.invoke(selectedTagIds.toSet())
            }
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

        // 更新所有chip的状态
        tagIdToChip.forEach { (id, chip) ->
            chip.isChecked = selectedTagIds.contains(id)
        }

    }

    /**
     * 清除所有选中
     */
    fun clearSelection() {
        selectedTagIds.clear()

        tagIdToChip.values.forEach { chip ->
            chip.isChecked = false
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
