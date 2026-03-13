package tiiehenry.android.app.snapshot.ui.common

import android.view.View
import tiiehenry.android.app.snapshot.config.VersionRetentionConfig
import tiiehenry.android.app.snapshot.databinding.IncludeVersionRetentionBinding

/**
 * 版本保留配置管理器
 * 用于管理版本保留策略的 UI 交互
 */
class VersionRetentionManager(
    private val binding: IncludeVersionRetentionBinding,
    private var config: VersionRetentionConfig,
    private val showEnabledSwitch: Boolean = true
) {
    init {
        setupEnabledSwitch()
        setupListeners()
    }

    private fun setupEnabledSwitch() {
        if (!showEnabledSwitch) {
            binding.switchVersionRetentionEnabled.visibility = View.GONE
            // 隐藏开关时，默认启用内容区域
            binding.layoutVersionRetentionContent.visibility = View.VISIBLE
            setEnabled(true)
        }
    }

    private fun setupListeners() {
        // 总开关: 启用单独控制
        binding.switchVersionRetentionEnabled.setOnCheckedChangeListener { _, isChecked ->
            binding.layoutVersionRetentionContent.visibility = if (isChecked) View.VISIBLE else View.GONE
            updateViewsEnabled(isChecked)
        }

        // 条件A: 最大保留版本数
        binding.cbMaxVersionEnabled.setOnCheckedChangeListener { _, isChecked ->
            binding.tilMaxVersionCount.isEnabled = isChecked
        }

        // 条件B: 同版本最低保留数
        binding.cbMinSameVersionEnabled.setOnCheckedChangeListener { _, isChecked ->
            binding.tilMinSameVersionCount.isEnabled = isChecked
        }

        // 条件C: 额外保留
        binding.cbExtraRetentionEnabled.setOnCheckedChangeListener { _, isChecked ->
            binding.tilExtraRetentionCount.isEnabled = isChecked
            binding.tilExtraRetentionDays.isEnabled = isChecked
        }
    }

    /**
     * 根据总开关状态更新所有视图的启用状态
     */
    private fun updateViewsEnabled(enabled: Boolean) {
        binding.cbMaxVersionEnabled.isEnabled = enabled
        binding.cbMinSameVersionEnabled.isEnabled = enabled
        binding.cbExtraRetentionEnabled.isEnabled = enabled

        // 只有在总开关启用时才根据子开关状态启用输入框
        binding.tilMaxVersionCount.isEnabled = enabled && binding.cbMaxVersionEnabled.isChecked
        binding.tilMinSameVersionCount.isEnabled = enabled && binding.cbMinSameVersionEnabled.isChecked
        binding.tilExtraRetentionCount.isEnabled = enabled && binding.cbExtraRetentionEnabled.isChecked
        binding.tilExtraRetentionDays.isEnabled = enabled && binding.cbExtraRetentionEnabled.isChecked
    }

    /**
     * 从配置加载到 UI
     */
    fun loadConfig() {
        // 总开关: 启用单独控制
        binding.switchVersionRetentionEnabled.isChecked = config.enabled
        // 当隐藏开关时，始终显示内容区域；否则根据 enabled 状态显示
        val contentVisible = if (showEnabledSwitch) config.enabled else true
        binding.layoutVersionRetentionContent.visibility = if (contentVisible) View.VISIBLE else View.GONE
        updateViewsEnabled(contentVisible)

        // 条件A: 最大保留版本数
        val maxEnabled = config.isMaxVersionCountEnabled
        binding.cbMaxVersionEnabled.isChecked = maxEnabled
        if (config.maxVersionCount != null) {
            binding.etMaxVersionCount.setText(config.maxVersionCount.toString())
        }

        // 条件B: 同版本最低保留数
        val minSameEnabled = config.isMinSameVersionCountEnabled
        binding.cbMinSameVersionEnabled.isChecked = minSameEnabled
        if (config.minSameVersionCount != null) {
            binding.etMinSameVersionCount.setText(config.minSameVersionCount.toString())
        }

        // 条件C: 额外保留
        val extraEnabled = config.isExtraRetentionEnabled
        binding.cbExtraRetentionEnabled.isChecked = extraEnabled
        if (config.extraRetentionCount != null) {
            binding.etExtraRetentionCount.setText(config.extraRetentionCount.toString())
        }
        if (config.extraRetentionDays != null) {
            binding.etExtraRetentionDays.setText(config.extraRetentionDays.toString())
        }
    }

    /**
     * 从 UI 保存到配置对象
     */
    fun saveToConfig(): VersionRetentionConfig {
        // 总开关: 启用单独控制（当隐藏开关时，始终设为 true）
        config.enabled = if (showEnabledSwitch) binding.switchVersionRetentionEnabled.isChecked else true

        // 条件A: 最大保留版本数
        config.maxVersionCount = if (binding.cbMaxVersionEnabled.isChecked) {
            binding.etMaxVersionCount.text.toString().toIntOrNull() ?: 0
        } else {
            null
        }

        // 条件B: 同版本最低保留数
        config.minSameVersionCount = if (binding.cbMinSameVersionEnabled.isChecked) {
            binding.etMinSameVersionCount.text.toString().toIntOrNull() ?: 0
        } else {
            null
        }

        // 条件C: 额外保留
        config.extraRetentionCount = if (binding.cbExtraRetentionEnabled.isChecked) {
            binding.etExtraRetentionCount.text.toString().toIntOrNull() ?: 0
        } else {
            null
        }
        config.extraRetentionDays = if (binding.cbExtraRetentionEnabled.isChecked) {
            binding.etExtraRetentionDays.text.toString().toIntOrNull() ?: 0
        } else {
            0
        }

        return config
    }

    /**
     * 更新配置对象引用
     */
    fun updateConfig(newConfig: VersionRetentionConfig) {
        config = newConfig
    }

    /**
     * 设置所有控件的启用状态
     */
    fun setEnabled(enabled: Boolean) {
        binding.cbMaxVersionEnabled.isEnabled = enabled
        binding.cbMinSameVersionEnabled.isEnabled = enabled
        binding.cbExtraRetentionEnabled.isEnabled = enabled
        
        // 只有在开关启用时才启用输入框
        binding.tilMaxVersionCount.isEnabled = enabled && binding.cbMaxVersionEnabled.isChecked
        binding.tilMinSameVersionCount.isEnabled = enabled && binding.cbMinSameVersionEnabled.isChecked
        binding.tilExtraRetentionCount.isEnabled = enabled && binding.cbExtraRetentionEnabled.isChecked
        binding.tilExtraRetentionDays.isEnabled = enabled && binding.cbExtraRetentionEnabled.isChecked
    }
}
