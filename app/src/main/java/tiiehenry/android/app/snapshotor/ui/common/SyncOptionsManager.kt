package tiiehenry.android.app.snapshotor.ui.common

import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import tiiehenry.android.app.snapshotor.databinding.IncludeGroupSyncOptionsBinding
import tiiehenry.android.app.snapshotor.model.PairedDevice
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import tiiehenry.android.app.snapshotor.config.GlobalConfig
import tiiehenry.android.app.snapshotor.config.SyncConfig

class SyncOptionsManager(
    private val binding: IncludeGroupSyncOptionsBinding,
    private var syncConfig: SyncConfig // 添加对配置对象的引用，不为空
) {
    private var pairedDeviceAdapter: PairedDeviceAdapter? = null

    var onEnableSyncToTargetChanged: (() -> Unit)? = null
    var onEnableSyncToSystemChanged: (() -> Unit)? = null

    init {
        setupListeners()
        initializePairedDevicesRecyclerView()
        setupSystemTagInput()
    }

    fun loadConfig() {
        setEnableSyncToTarget(syncConfig.enableSyncToTarget)
        setEnableSyncToSystem(syncConfig.enableSyncToSystem)
        setSyncType(syncConfig.syncType)
        // 设置同步目标设备列表
        setSyncTargets(syncConfig.syncTargets)

        // 设置同步系统列表
        setSyncSystems(syncConfig.syncSystems)
    }

    private fun setupListeners() {
        binding.cbEnableSyncToTarget.setOnCheckedChangeListener { _, isChecked ->
            binding.layoutSyncTargets.visibility = if (isChecked) View.VISIBLE else View.GONE
            binding.tvSyncTargetsLabel.visibility = if (isChecked) View.VISIBLE else View.GONE
            onEnableSyncToTargetChanged?.invoke()
        }

        binding.cbEnableSyncToSystem.setOnCheckedChangeListener { _, isChecked ->
            binding.layoutSyncSystems.visibility = if (isChecked) View.VISIBLE else View.GONE
            binding.tvSyncSystemsLabel.visibility = if (isChecked) View.VISIBLE else View.GONE
            onEnableSyncToSystemChanged?.invoke()
        }
    }

    private fun setupSystemTagInput() {
        binding.btnAddSystem.setOnClickListener {
            val systemTag = binding.etSystemTag.text?.toString()?.trim()
            if (!systemTag.isNullOrBlank()) {
                // 首先添加到全局配置
                val globalSystems = GlobalConfig.syncSystems.toMutableSet()
                globalSystems.add(systemTag)
                GlobalConfig.syncSystems = globalSystems

                // 然后根据当前配置对象决定是否也添加到特定配置
                syncConfig.syncSystems.add(systemTag)

                // 更新所有系统标签显示
                updateAllSyncSystemChips()
                binding.etSystemTag.setText("") // 清空输入框
            }
        }

        // 添加回车键监听，以便用户可以按回车添加系统标签
        binding.etSystemTag.setOnEditorActionListener { _, _, _ ->
            val systemTag = binding.etSystemTag.text?.toString()?.trim()
            if (!systemTag.isNullOrBlank()) {
                // 首先添加到全局配置
                val globalSystems = GlobalConfig.syncSystems.toMutableSet()
                globalSystems.add(systemTag)
                GlobalConfig.syncSystems = globalSystems

                // 然后根据当前配置对象决定是否也添加到特定配置
                syncConfig.syncSystems.add(systemTag)

                // 更新所有系统标签显示
                updateAllSyncSystemChips()
                binding.etSystemTag.setText("")
                true
            } else {
                false
            }
        }
    }

    private fun initializePairedDevicesRecyclerView() {
        binding.recyclerPairedDevices.layoutManager =
            LinearLayoutManager(binding.recyclerPairedDevices.context)
        // 初始化空的适配器，等待外部设置数据
        pairedDeviceAdapter = PairedDeviceAdapter(emptyList()) { deviceId, isSelected ->
            // 当设备选择状态改变时，同步到ChipGroup
            if (isSelected) {
                addSyncTargetChip(deviceId)
            } else {
                removeSyncTargetChip(deviceId)
            }
        }
        binding.recyclerPairedDevices.adapter = pairedDeviceAdapter
    }

    fun setPairedDevices(devices: List<PairedDevice>, selectedDeviceIds: Set<String> = emptySet()) {
        pairedDeviceAdapter?.let { adapter ->
            // 更新适配器数据
            val newAdapter = PairedDeviceAdapter(
                devices,
                selectedDeviceIds.toMutableSet()
            ) { deviceId, isSelected ->
                // 当设备选择状态改变时，同步到ChipGroup
                if (isSelected) {
                    addSyncTargetChip(deviceId)
                } else {
                    removeSyncTargetChip(deviceId)
                }
                // 通知外部监听器同步目标可能已更改
                onEnableSyncToTargetChanged?.invoke()
            }
            binding.recyclerPairedDevices.adapter = newAdapter
            // 更新选中状态
            newAdapter.updateSelectedDevices(selectedDeviceIds)
            // 同时更新ChipGroup中的同步目标列表
            setSyncTargets(selectedDeviceIds)
        }
    }

    private fun addSyncTargetChip(target: String) {
        // 检查是否已存在该目标
        for (i in 0 until binding.chipGroupSyncTargets.childCount) {
            val chip = binding.chipGroupSyncTargets.getChildAt(i) as? Chip
            if (chip?.text?.toString() == target) {
                return // 如果已存在，直接返回
            }
        }

        val chip = Chip(binding.chipGroupSyncTargets.context).apply {
            text = target
            isCloseIconVisible = true
            setOnCloseIconClickListener {
                binding.chipGroupSyncTargets.removeView(this)
                // 从适配器中取消选中
                pairedDeviceAdapter?.let { adapter ->
                    adapter.updateSelectedDevices(adapter.getSelectedDevices() - target)
                }
                onEnableSyncToTargetChanged?.invoke()
            }
        }
        binding.chipGroupSyncTargets.addView(chip)
    }

    private fun removeSyncTargetChip(target: String) {
        for (i in 0 until binding.chipGroupSyncTargets.childCount) {
            val chip = binding.chipGroupSyncTargets.getChildAt(i) as? Chip
            if (chip?.text?.toString() == target) {
                binding.chipGroupSyncTargets.removeViewAt(i)
                break
            }
        }
    }

    private fun addSyncSystemChip(system: String) {
        // 检查是否已存在该系统
        for (i in 0 until binding.chipGroupSyncSystems.childCount) {
            val chip = binding.chipGroupSyncSystems.getChildAt(i) as? Chip
            if (chip?.text?.toString() == system) {
                return // 如果已存在，直接返回
            }
        }

        val chip = Chip(binding.chipGroupSyncSystems.context).apply {
            text = system
            isCloseIconVisible = false // 已启用的标签不显示关闭图标
            setOnClickListener {
                // 从当前配置中移除（变为未启用状态）
                syncConfig.syncSystems.remove(system)
                // 更新所有系统标签显示
                updateAllSyncSystemChips()
            }
        }
        binding.chipGroupSyncSystems.addView(chip)
    }

    // 添加未启用系统标签的Chip
    private fun addUnsyncSystemChip(system: String) {
        // 检查是否已存在该系统
        for (i in 0 until binding.chipGroupUnsyncSystems.childCount) {
            val chip = binding.chipGroupUnsyncSystems.getChildAt(i) as? Chip
            if (chip?.text?.toString() == system) {
                return // 如果已存在，直接返回
            }
        }

        val chip = Chip(binding.chipGroupUnsyncSystems.context).apply {
            text = system
            isCloseIconVisible = true // 未启用的标签显示关闭图标，用于删除
            setOnCloseIconClickListener {
                // 从全局配置和特定配置中移除
                val globalSystems = GlobalConfig.syncSystems.toMutableSet()
                globalSystems.remove(system)
                GlobalConfig.syncSystems = globalSystems

                syncConfig.syncSystems.remove(system)

                // 更新所有系统标签显示
                updateAllSyncSystemChips()
                onEnableSyncToSystemChanged?.invoke()
            }
            setOnClickListener {
                // 点击未启用的标签，将其添加到当前配置中（启用）
                syncConfig.syncSystems.add(system)
                // 更新所有系统标签显示
                updateAllSyncSystemChips()
            }
        }
        binding.chipGroupUnsyncSystems.addView(chip)
    }

    private fun removeSyncSystemChip(system: String) {
        for (i in 0 until binding.chipGroupSyncSystems.childCount) {
            val chip = binding.chipGroupSyncSystems.getChildAt(i) as? Chip
            if (chip?.text?.toString() == system) {
                binding.chipGroupSyncSystems.removeViewAt(i)
                break
            }
        }
    }

    // 移除未启用的系统标签
    private fun removeUnsyncSystemChip(system: String) {
        for (i in 0 until binding.chipGroupUnsyncSystems.childCount) {
            val chip = binding.chipGroupUnsyncSystems.getChildAt(i) as? Chip
            if (chip?.text?.toString() == system) {
                binding.chipGroupUnsyncSystems.removeViewAt(i)
                break
            }
        }
    }

    // 添加刷新系统标签选中状态的方法
    fun updateAllSyncSystemChips() {
        // 清空现有的启用和未启用的系统标签
        binding.chipGroupSyncSystems.removeAllViews()
        binding.chipGroupUnsyncSystems.removeAllViews()

        // 获取全局系统标签和当前配置的系统标签
        val globalSystems = GlobalConfig.syncSystems
        val currentSystems = syncConfig.syncSystems.toSet()

        // 添加已启用的系统标签
        currentSystems.forEach { system ->
            addSyncSystemChip(system)
        }

        // 添加未启用的系统标签（全局标签中不在当前配置中的）
        val unsyncSystems = globalSystems.filter { !currentSystems.contains(it) }
        unsyncSystems.forEach { system ->
            addUnsyncSystemChip(system)
        }
    }

    fun setEnableSyncToTarget(enabled: Boolean) {
        binding.cbEnableSyncToTarget.isChecked = enabled
        binding.layoutSyncTargets.visibility = if (enabled) View.VISIBLE else View.GONE
        binding.tvSyncTargetsLabel.visibility = if (enabled) View.VISIBLE else View.GONE
    }

    fun setEnableSyncToSystem(enabled: Boolean) {
        binding.cbEnableSyncToSystem.isChecked = enabled
        binding.layoutSyncSystems.visibility = if (enabled) View.VISIBLE else View.GONE
        binding.tvSyncSystemsLabel.visibility = if (enabled) View.VISIBLE else View.GONE
    }

    fun getEnableSyncToTarget(): Boolean {
        return binding.cbEnableSyncToTarget.isChecked
    }

    fun getEnableSyncToSystem(): Boolean {
        return binding.cbEnableSyncToSystem.isChecked
    }

    fun getSyncType(): Int {
        return binding.etSyncType.text.toString().toIntOrNull() ?: 0
    }

    fun setSyncType(type: Int) {
        binding.etSyncType.setText(type.toString())
    }

    fun setSyncTargets(targets: Set<String>) {
        // 清空现有的Chip
        binding.chipGroupSyncTargets.removeAllViews()

        // 添加新的Chip
        targets.forEach { target ->
            addSyncTargetChip(target)
        }
    }

    fun setSyncSystems(systems: Set<String>) {
        // 清空现有的Chip
        binding.chipGroupSyncSystems.removeAllViews()
        binding.chipGroupUnsyncSystems.removeAllViews()

        // 获取全局系统标签和当前配置的系统标签
        val globalSystems = GlobalConfig.syncSystems
        val currentSystems = syncConfig.syncSystems.toSet()

        // 添加已启用的系统标签
        currentSystems.forEach { system ->
            addSyncSystemChip(system)
        }

        // 添加未启用的系统标签
        val unsyncSystems = globalSystems.filter { !currentSystems.contains(it) }
        unsyncSystems.forEach { system ->
            addUnsyncSystemChip(system)
        }
    }

    fun getSyncTargets(): Set<String> {
        val targets = mutableSetOf<String>()
        for (i in 0 until binding.chipGroupSyncTargets.childCount) {
            val chip = binding.chipGroupSyncTargets.getChildAt(i) as? Chip
            chip?.text?.toString()?.let { targets.add(it) }
        }
        return targets
    }

    fun getSyncSystems(): Set<String> {
        val systems = mutableSetOf<String>()
        for (i in 0 until binding.chipGroupSyncSystems.childCount) {
            val chip = binding.chipGroupSyncSystems.getChildAt(i) as? Chip
            chip?.text?.toString()?.let { systems.add(it) }
        }
        return systems
    }
}