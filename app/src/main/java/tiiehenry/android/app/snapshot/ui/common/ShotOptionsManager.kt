package tiiehenry.android.app.snapshot.ui.common

import android.content.Context
import android.view.ContextThemeWrapper
import androidx.core.view.children
import com.google.android.material.chip.Chip
import com.google.android.material.R as MaterialR
import tiiehenry.android.app.snapshot.config.CompressItems
import tiiehenry.android.app.snapshot.config.ShotConfig
import tiiehenry.android.app.snapshot.databinding.IncludeShotOptionsBinding

class ShotOptionsManager(
    private val binding: IncludeShotOptionsBinding,
    private val context: Context,
    private var shotConfig: ShotConfig
) {
    private var algorithmChips = mutableMapOf<String, Chip>()

    init {
        setupListeners()
    }

    fun loadConfig() {
        setEnabled(shotConfig.enabled)
        setAutoSnapshot(shotConfig.autoSnapshot)
        setUninstallArchived(shotConfig.uninstallArchived)
        setCompressItems(shotConfig.compressItems)
        setCompressAlgorithm(shotConfig.compressAlgorithm)
    }

    private fun setupListeners() {
        binding.switchEnabled.setOnCheckedChangeListener { _, isChecked ->
            updateViewsEnabled(isChecked)
        }

        binding.cbAutoSnapshot.setOnCheckedChangeListener { _, isChecked ->
            // 当自动存档选项改变时的处理
        }

        binding.cbUninstallArchived.setOnCheckedChangeListener { _, isChecked ->
            // 当卸载已归档版本选项改变时的处理
        }

        // 为压缩项目ChipGroup设置监听器
        setupCompressItemListeners()
    }

    private fun setupCompressItemListeners() {
        binding.chipApk.setOnCheckedChangeListener { _, isChecked ->
            updateCompressItems("apk", isChecked)
        }
        binding.chipData.setOnCheckedChangeListener { _, isChecked ->
            updateCompressItems("data", isChecked)
        }
        binding.chipUser.setOnCheckedChangeListener { _, isChecked ->
            updateCompressItems("user", isChecked)
        }
        binding.chipUserDe.setOnCheckedChangeListener { _, isChecked ->
            updateCompressItems("user_de", isChecked)
        }
        binding.chipObb.setOnCheckedChangeListener { _, isChecked ->
            updateCompressItems("obb", isChecked)
        }
        binding.chipMedia.setOnCheckedChangeListener { _, isChecked ->
            updateCompressItems("media", isChecked)
        }
    }

    private fun updateCompressItems(item: String, isSelected: Boolean) {
        val items = shotConfig.compressItems.toMutableSet()
        if (isSelected) {
            items.add(item)
        } else {
            items.remove(item)
        }
        shotConfig.compressItems = items
    }

    fun setEnabled(enabled: Boolean) {
        binding.switchEnabled.isChecked = enabled
        updateViewsEnabled(enabled)
    }

    fun getEnabled(): Boolean {
        return binding.switchEnabled.isChecked
    }

    private fun updateViewsEnabled(enabled: Boolean) {
        // 启用/禁用所有选项视图
        binding.cbAutoSnapshot.isEnabled = enabled
        binding.cbUninstallArchived.isEnabled = enabled
        binding.chipApk.isEnabled = enabled
        binding.chipData.isEnabled = enabled
        binding.chipUser.isEnabled = enabled
        binding.chipUserDe.isEnabled = enabled
        binding.chipObb.isEnabled = enabled
        binding.chipMedia.isEnabled = enabled
        binding.chipGroupCompressAlgorithm.children.forEach { it.isEnabled = enabled }
    }

    fun setAutoSnapshot(enabled: Boolean) {
        binding.cbAutoSnapshot.isChecked = enabled
    }

    fun setUninstallArchived(enabled: Boolean) {
        binding.cbUninstallArchived.isChecked = enabled
    }

    fun setCompressItems(items: Set<String>) {
        binding.chipApk.isChecked = CompressItems.COMPRESS_ITEM_APK in items
        binding.chipData.isChecked = CompressItems.COMPRESS_ITEM_DATA in items
        binding.chipUser.isChecked = CompressItems.COMPRESS_ITEM_USER in items
        binding.chipUserDe.isChecked = CompressItems.COMPRESS_ITEM_USER_DE in items
        binding.chipObb.isChecked = CompressItems.COMPRESS_ITEM_OBB in items
        binding.chipMedia.isChecked = CompressItems.COMPRESS_ITEM_MEDIA in items
    }

    fun setCompressAlgorithm(algorithm: String) {
        algorithmChips[algorithm]?.isChecked = true
    }

    fun getAutoSnapshot(): Boolean {
        return binding.cbAutoSnapshot.isChecked
    }

    fun getPermission(): Boolean = true  // 默认保存权限

    fun getUninstallArchived(): Boolean {
        return binding.cbUninstallArchived.isChecked
    }

    fun getCompressItems(): Set<String> {
        val items = mutableSetOf<String>()
        if (binding.chipApk.isChecked) items.add(CompressItems.COMPRESS_ITEM_APK)
        if (binding.chipData.isChecked) items.add(CompressItems.COMPRESS_ITEM_DATA)
        if (binding.chipUser.isChecked) items.add(CompressItems.COMPRESS_ITEM_USER)
        if (binding.chipUserDe.isChecked) items.add(CompressItems.COMPRESS_ITEM_USER_DE)
        if (binding.chipObb.isChecked) items.add(CompressItems.COMPRESS_ITEM_OBB)
        if (binding.chipMedia.isChecked) items.add(CompressItems.COMPRESS_ITEM_MEDIA)
        return items
    }

    fun getCompressAlgorithm(): String {
        return algorithmChips.entries.find { it.value.isChecked }?.key ?: ""
    }

    fun setCompressAlgorithmOptions(options: Array<String>) {
        binding.chipGroupCompressAlgorithm.removeAllViews()
        algorithmChips.clear()
        
        val context = binding.chipGroupCompressAlgorithm.context
        for (option in options) {
            val chip = Chip(ContextThemeWrapper(context, MaterialR.style.Widget_Material3_Chip_Filter)).apply {
                text = option.uppercase()
                isCheckable = true
                isCheckedIconVisible = true
                id = android.view.View.generateViewId()
            }
            binding.chipGroupCompressAlgorithm.addView(chip)
            algorithmChips[option] = chip
        }
        
        // 默认选中第一个
        if (options.isNotEmpty()) {
            algorithmChips[options.first()]?.isChecked = true
        }
    }
}