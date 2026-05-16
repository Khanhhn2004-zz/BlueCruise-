package com.vibegravity.bluecruise.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.core.content.ContextCompat
import com.vibegravity.bluecruise.R
import com.vibegravity.bluecruise.databinding.ItemBluetoothDeviceBinding
import com.vibegravity.bluecruise.domain.BluetoothDeviceDomain

class BluetoothDeviceAdapter(
    private val onDeviceClicked: (String) -> Unit
) : ListAdapter<BluetoothDeviceDomain, BluetoothDeviceAdapter.DeviceViewHolder>(DeviceDiffCallback()) {

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).macAddress.hashCode().toLong()
    }

    private var cachedSelectedBg: Int = 0
    private var cachedUnselectedBg: Int = 0
    private var cachedSelectedStroke: Int = 0
    private var cachedTransparent: Int = 0
    private var cachedPrimaryBlue: Int = 0
    private var cachedWhite: Int = 0
    private var colorsInitialized = false

    var selectedMacAddress: String? = null
        set(value) {
            if (field == value) return

            val previousMac = field
            field = value
            val previousIndex = currentList.indexOfFirst { it.macAddress == previousMac }
            val newIndex = currentList.indexOfFirst { it.macAddress == value }
            if (previousIndex >= 0) {
                notifyItemChanged(previousIndex)
            }
            if (newIndex >= 0 && newIndex != previousIndex) {
                notifyItemChanged(newIndex)
            }
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemBluetoothDeviceBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return DeviceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val context = holder.itemView.context
        if (!colorsInitialized) {
            cachedSelectedBg = ContextCompat.getColor(context, R.color.primary_blue_10)
            cachedUnselectedBg = ContextCompat.getColor(context, R.color.card_dark)
            cachedSelectedStroke = ContextCompat.getColor(context, R.color.primary_blue)
            cachedTransparent = ContextCompat.getColor(context, R.color.transparent)
            cachedPrimaryBlue = ContextCompat.getColor(context, R.color.primary_blue)
            cachedWhite = ContextCompat.getColor(context, R.color.white)
            colorsInitialized = true
        }
        val device = getItem(position)
        holder.bind(
            device, selectedMacAddress, onDeviceClicked,
            cachedSelectedBg, cachedUnselectedBg, cachedSelectedStroke, cachedTransparent,
            cachedPrimaryBlue, cachedWhite
        )
    }

    class DeviceViewHolder(private val binding: ItemBluetoothDeviceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(
            device: BluetoothDeviceDomain,
            selectedMac: String?,
            onDeviceClicked: (String) -> Unit,
            selectedBg: Int,
            unselectedBg: Int,
            selectedStroke: Int,
            transparent: Int,
            primaryBlue: Int,
            white: Int
        ) {
            val isSelected = device.macAddress == selectedMac

            binding.tvDeviceName.text = device.name.ifEmpty { "Unknown Device" }
            binding.tvDeviceMac.text = device.macAddress

            if (isSelected) {
                binding.cvDevice.setCardBackgroundColor(selectedBg)
                binding.cvDevice.strokeColor = selectedStroke
                binding.flIconBg.setBackgroundResource(R.drawable.bg_circle_48dp_primary)
                binding.ivDeviceIcon.setColorFilter(white)
                binding.rbSelected.isChecked = true
            } else {
                binding.cvDevice.setCardBackgroundColor(unselectedBg)
                binding.cvDevice.strokeColor = transparent
                binding.flIconBg.setBackgroundResource(R.drawable.bg_circle_48dp_darkoverlay)
                binding.ivDeviceIcon.setColorFilter(primaryBlue)
                binding.rbSelected.isChecked = false
            }

            // Set appropriate icon based on name
            val nameLowerCase = device.name.lowercase()
            val iconRes = when {
                nameLowerCase.contains("car") || nameLowerCase.contains("toyota") ||
                    nameLowerCase.contains("honda") || nameLowerCase.contains("ford") ||
                    nameLowerCase.contains("vinfast") -> android.R.drawable.ic_dialog_map
                nameLowerCase.contains("headset") || nameLowerCase.contains("headphones") ->
                    android.R.drawable.ic_lock_silent_mode_off
                nameLowerCase.contains("speaker") || nameLowerCase.contains("bluetooth") ->
                    android.R.drawable.ic_lock_silent_mode
                else -> android.R.drawable.stat_sys_data_bluetooth
            }
            binding.ivDeviceIcon.setImageResource(iconRes)

            binding.root.setOnClickListener {
                onDeviceClicked(device.macAddress)
            }
        }
    }

    class DeviceDiffCallback : DiffUtil.ItemCallback<BluetoothDeviceDomain>() {
        override fun areItemsTheSame(oldItem: BluetoothDeviceDomain, newItem: BluetoothDeviceDomain): Boolean {
            return oldItem.macAddress == newItem.macAddress
        }

        override fun areContentsTheSame(oldItem: BluetoothDeviceDomain, newItem: BluetoothDeviceDomain): Boolean {
            return oldItem.name == newItem.name && oldItem.macAddress == newItem.macAddress
        }
    }
}

