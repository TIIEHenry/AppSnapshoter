package tiiehenry.android.app.snapshot.ui.common

import android.content.Context
import com.google.android.material.chip.Chip
import tiiehenry.android.app.snapshot.config.CompressItems
import tiiehenry.android.app.snapshot.config.ShotConfig
import tiiehenry.android.app.snapshot.databinding.IncludeShotOptionsBinding

class ShotOptionsManager(
    private val binding: IncludeShotOptionsBinding,
    private val context: Context,
    private var shotConfig: ShotConfig,
    private val showEnabledSwitch: Boolean = true
) {
    private var algorithmChips = mutableMapOf<String, Chip>()

    init {
        setupEnabledSwitch()
        setupListeners()
    }

    private fun setupEnabledSwitch() {
        if (!showEnabledSwitch) {
            binding.switchEnabled.visibility = android.view.View.GONE
            // 隐藏开关时，默认启用所有选项
            setEnabled(true)
        }
    }

    fun loadConfig() {
        setEnabled(shotConfig.enabled)
        setCompressItems(shotConfig.items)
    }

    private fun setupListeners() {
        binding.switchEnabled.setOnCheckedChangeListener { _, isChecked ->
            updateViewsEnabled(isChecked)
        }

        // 为压缩项目 ChipGroup 设置监听器
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
        val items = shotConfig.items.toMutableSet()
        if (isSelected) {
            items.add(item)
        } else {
            items.remove(item)
        }
        shotConfig.items = items
    }

    fun setEnabled(enabled: Boolean) {
        binding.switchEnabled.isChecked = enabled
        updateViewsEnabled(enabled)
    }

    fun getEnabled(): Boolean {
        return binding.switchEnabled.isChecked
    }

    private fun updateViewsEnabled(enabled: Boolean) {
        // 启用/禁用所有选项视图（当隐藏启用开关时，始终启用）
        val effectiveEnabled = if (showEnabledSwitch) enabled else true
        binding.chipApk.isEnabled = effectiveEnabled
        binding.chipData.isEnabled = effectiveEnabled
        binding.chipUser.isEnabled = effectiveEnabled
        binding.chipUserDe.isEnabled = effectiveEnabled
        binding.chipObb.isEnabled = effectiveEnabled
        binding.chipMedia.isEnabled = effectiveEnabled
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

    fun getPermission(): Boolean = true  // 默认保存权限

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
        return ""  // ShotOptionsManager 不再管理压缩算法
    }

    fun setCompressAlgorithmOptions(options: Array<String>) {
        // ShotOptionsManager 不再管理压缩算法
    }
}