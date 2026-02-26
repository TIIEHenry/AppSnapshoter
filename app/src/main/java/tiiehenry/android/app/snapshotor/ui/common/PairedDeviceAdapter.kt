package tiiehenry.android.app.snapshotor.ui.common

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import tiiehenry.android.app.snapshotor.databinding.ItemPairedDeviceBinding
import tiiehenry.android.app.snapshotor.model.PairedDevice

class PairedDeviceAdapter(
    private val devices: List<PairedDevice>,
    private val selectedDeviceIds: MutableSet<String> = mutableSetOf(),
    private val onDeviceSelectionChanged: (String, Boolean) -> Unit = { _, _ -> }
) : RecyclerView.Adapter<PairedDeviceAdapter.DeviceViewHolder>() {

    class DeviceViewHolder(val  binding: ItemPairedDeviceBinding) : RecyclerView.ViewHolder(binding.root) {

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding =
            ItemPairedDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DeviceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = devices[position]
        holder.binding.tvDeviceName.text = device.deviceName
        
        // 设置选中状态
        holder.binding.cbDeviceSelected.isChecked = selectedDeviceIds.contains(device.deviceId)
        
        // 设置点击监听器
        holder.binding.cbDeviceSelected.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                selectedDeviceIds.add(device.deviceId)
            } else {
                selectedDeviceIds.remove(device.deviceId)
            }
            onDeviceSelectionChanged(device.deviceId, isChecked)
        }
    }

    override fun getItemCount(): Int = devices.size

    fun updateSelectedDevices(selectedIds: Set<String>) {
        selectedDeviceIds.clear()
        selectedDeviceIds.addAll(selectedIds)
        notifyDataSetChanged()
    }

    fun getSelectedDevices(): Set<String> {
        return selectedDeviceIds.toSet()
    }
}