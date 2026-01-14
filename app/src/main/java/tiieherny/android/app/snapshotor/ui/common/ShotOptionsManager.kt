package tiieherny.android.app.snapshotor.ui.common

import android.view.LayoutInflater
import android.widget.CheckBox
import android.widget.TextView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import tiieherny.android.app.snapshotor.databinding.IncludeShotOptionsBinding
import tiieherny.android.app.snapshotor.config.ShotConfig

class ShotOptionsManager(
    private val binding: IncludeShotOptionsBinding,
    private var shotConfig: ShotConfig
) {

    init {
        setupListeners()
    }

    fun loadConfig() {
        setAutoSnapshot(shotConfig.autoSnapshot)
        setPermission(shotConfig.permission)
        setUninstallArchived(shotConfig.uninstallArchived)
        setCompressItems(shotConfig.compressItems)
        setCompressAlgorithm(shotConfig.compressAlgorithm)
    }

    private fun setupListeners() {
        binding.cbAutoSnapshot.setOnCheckedChangeListener { _, isChecked ->
            // 当自动存档选项改变时的处理
        }

        binding.cbSavePermission.setOnCheckedChangeListener { _, isChecked ->
            // 当保存权限选项改变时的处理
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
        binding.chipExternalData.setOnCheckedChangeListener { _, isChecked ->
            updateCompressItems("external_data", isChecked)
        }
    }

    private fun updateCompressItems(item: String, isSelected: Boolean) {
        val currentItems = shotConfig.compressItems.toMutableSet()
        if (isSelected) {
            currentItems.add(item)
        } else {
            currentItems.remove(item)
        }
        shotConfig.compressItems = currentItems
    }

    fun setAutoSnapshot(enabled: Boolean) {
        binding.cbAutoSnapshot.isChecked = enabled
    }

    fun setPermission(enabled: Boolean) {
        binding.cbSavePermission.isChecked = enabled
    }

    fun setUninstallArchived(enabled: Boolean) {
        binding.cbUninstallArchived.isChecked = enabled
    }

    fun setCompressItems(items: Set<String>) {
        binding.chipApk.isChecked = items.contains("apk")
        binding.chipData.isChecked = items.contains("data")
        binding.chipUser.isChecked = items.contains("user")
        binding.chipUserDe.isChecked = items.contains("user_de")
        binding.chipObb.isChecked = items.contains("obb")
        binding.chipExternalData.isChecked = items.contains("external_data")
    }

    fun setCompressAlgorithm(algorithm: String) {
        binding.autoCompleteCompressAlgorithm.setText(algorithm)
    }

    fun getAutoSnapshot(): Boolean {
        return binding.cbAutoSnapshot.isChecked
    }

    fun getPermission(): Boolean {
        return binding.cbSavePermission.isChecked
    }

    fun getUninstallArchived(): Boolean {
        return binding.cbUninstallArchived.isChecked
    }

    fun getCompressItems(): Set<String> {
        val items = mutableSetOf<String>()
        if (binding.chipApk.isChecked) items.add("apk")
        if (binding.chipData.isChecked) items.add("data")
        if (binding.chipUser.isChecked) items.add("user")
        if (binding.chipUserDe.isChecked) items.add("user_de")
        if (binding.chipObb.isChecked) items.add("obb")
        if (binding.chipExternalData.isChecked) items.add("external_data")
        return items
    }

    fun getCompressAlgorithm(): String {
        return binding.autoCompleteCompressAlgorithm.text.toString()
    }

    fun setCompressAlgorithmOptions(options: Array<String>) {
        val adapter = android.widget.ArrayAdapter(
            binding.autoCompleteCompressAlgorithm.context,
            android.R.layout.simple_dropdown_item_1line,
            options
        )
        binding.autoCompleteCompressAlgorithm.setAdapter(adapter)
    }
}