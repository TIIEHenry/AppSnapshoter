package tiiehenry.android.app.snapshot.main.launch.config

import android.content.Context
import android.view.View
import com.google.android.material.chip.Chip
import tiiehenry.android.app.snapshot.R
import tiiehenry.android.app.snapshot.config.ActionConfig
import tiiehenry.android.app.snapshot.databinding.IncludeActionConfigBinding

/**
 * 动作配置管理器
 * 管理自动存档和打包算法的 UI 和配置
 */
class ActionConfigManager(
    private val binding: IncludeActionConfigBinding,
    private val context: Context,
    private var actionConfig: ActionConfig,
    private val showEnabledSwitch: Boolean = true
) {
    private var algorithmChips = mutableMapOf<String, Chip>()

    init {
        setupEnabledSwitch()
        setupListeners()
    }

    private fun setupEnabledSwitch() {
        if (!showEnabledSwitch) {
            binding.switchEnabled.visibility = View.GONE
            // 隐藏开关时，默认启用所有选项
            setEnabled(true)
        }
    }

    fun loadConfig() {
        setEnabled(actionConfig.enabled)
        setAutoSnapshot(actionConfig.isAutoSnapshot)
        setUninstallArchived(actionConfig.isUninstallArchived)
        setCompressAlgorithm(actionConfig.compressAlgorithm)
        setCompressLevel(actionConfig.getCompressLevel())
    }

    private fun setupListeners() {
        binding.switchEnabled.setOnCheckedChangeListener { _, isChecked ->
            updateViewsEnabled(isChecked)
        }

        binding.cbAutoSnapshot.setOnCheckedChangeListener { _, isChecked ->
            // 当自动存档选项改变时的处理
            actionConfig.isAutoSnapshot = isChecked
        }
        
        binding.cbUninstallArchived.setOnCheckedChangeListener { _, isChecked ->
            // 当卸载已归档版本选项改变时的处理
            actionConfig.isUninstallArchived = isChecked
        }

        binding.sliderCompressLevel.addOnChangeListener { slider, value, fromUser ->
            if (fromUser) {
                val level = value.toInt()
                actionConfig.setCompressLevel(level)
                updateCompressLevelLabel(level)
            }
        }
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
        binding.cbAutoSnapshot.isEnabled = effectiveEnabled
        binding.cbUninstallArchived.isEnabled = effectiveEnabled
        for (i in 0 until binding.chipGroupCompressAlgorithm.childCount) {
            binding.chipGroupCompressAlgorithm.getChildAt(i).isEnabled = effectiveEnabled
        }
        binding.sliderCompressLevel.isEnabled = effectiveEnabled
        binding.tvCompressLevelLabel.isEnabled = effectiveEnabled
    }

    fun setAutoSnapshot(enabled: Boolean) {
        binding.cbAutoSnapshot.isChecked = enabled
    }
    
    fun setUninstallArchived(enabled: Boolean) {
        binding.cbUninstallArchived.isChecked = enabled
    }

    fun setCompressAlgorithm(algorithm: String) {
        algorithmChips[algorithm]?.isChecked = true
    }

    fun getAutoSnapshot(): Boolean {
        return binding.cbAutoSnapshot.isChecked
    }
    
    fun getUninstallArchived(): Boolean {
        return binding.cbUninstallArchived.isChecked
    }

    fun getCompressAlgorithm(): String {
        return algorithmChips.entries.find { it.value.isChecked }?.key ?: ""
    }

    fun saveToActionConfig(config: ActionConfig) {
        config.enabled = getEnabled()
        config.isAutoSnapshot = getAutoSnapshot()
        config.isUninstallArchived = getUninstallArchived()
        config.compressAlgorithm = getCompressAlgorithm()
        config.setCompressLevel(getCompressLevel())
    }

    fun getCompressLevel(): Int {
        return binding.sliderCompressLevel.value.toInt()
    }

    private fun updateCompressLevelLabel(level: Int) {
        val ctx = binding.tvCompressLevelLabel.context
        val levelText = when (level) {
            1 -> ctx.getString(R.string.compress_level_1)
            3 -> ctx.getString(R.string.compress_level_3)
            5 -> ctx.getString(R.string.compress_level_5)
            7 -> ctx.getString(R.string.compress_level_7)
            9 -> ctx.getString(R.string.compress_level_9)
            else -> ctx.getString(R.string.compress_level_unknown)
        }
        binding.tvCompressLevelLabel.text = ctx.getString(R.string.compress_level_label, level, levelText)
    }

    fun setCompressLevel(level: Int) {
        binding.sliderCompressLevel.value = level.toFloat()
        updateCompressLevelLabel(level)
    }

    fun setCompressAlgorithmOptions(options: Array<String>) {
        binding.chipGroupCompressAlgorithm.removeAllViews()
        algorithmChips.clear()
        
        val context = binding.chipGroupCompressAlgorithm.context
        for (option in options) {
            val chip = Chip(context).apply {
                text = option.uppercase()
                isCheckable = true
                isCheckedIconVisible = true
                id = View.generateViewId()
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
