package com.vibegravity.bluecruise.ui

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.vibegravity.bluecruise.R
import com.vibegravity.bluecruise.databinding.ItemAutoStartBannerBinding
import com.vibegravity.bluecruise.databinding.ItemBatteryBannerBinding
import com.vibegravity.bluecruise.databinding.ItemEmptyStateBinding
import com.vibegravity.bluecruise.databinding.ItemSectionTitleBinding
import com.vibegravity.bluecruise.databinding.ItemTargetCarBinding

/** Holds data for the target car row to keep bind/updatePartial under parameter limit. */
data class TargetCarState(
    val deviceName: String,
    val isAutoPlay: Boolean,
    val isAutoPlayOnAndroidAuto: Boolean,
    val isAftermarketAndroidAutoTarget: Boolean,
    val connectionStartDelaySeconds: Int,
    val isKeepAlive: Boolean,
    val audioPath: String?,
    val audioPath2: String?,
    val isFloatingBubbleEnabled: Boolean,
    val isPlaying: Boolean,
    val isPlaybackPending: Boolean,
    val isServerSyncing: Boolean,
    val routingTier: Int
)

/** Callbacks for the target car row. */
data class TargetCarCallbacks(
    val onAutoPlayToggled: (Boolean) -> Unit,
    val onAutoPlayOnAndroidAutoToggled: (Boolean) -> Unit,
    val onAftermarketAndroidAutoTargetToggled: (Boolean) -> Unit,
    val onConnectionStartDelayChanged: (Int) -> Unit,
    val onKeepAliveToggled: (Boolean) -> Unit,
    val onPlayStopClicked: () -> Unit,
    val onSyncServerClicked: () -> Unit,
    val onAudioPickerClicked: () -> Unit,
    val onAudioPicker2Clicked: () -> Unit,
    val onFloatingBubbleToggled: (Boolean) -> Unit,
    val onRoutingTierChanged: (Int) -> Unit
)

private data class ScreenChromeState(
    val showBatteryBanner: Boolean,
    val showAutoStartBanner: Boolean,
    val showTargetCar: Boolean,
    val showEmptyState: Boolean
)

class BluetoothScreenAdapter(
    private val onIgnoreBatteryOptClicked: () -> Unit,
    private val onOpenAutoStartSettingsClicked: () -> Unit,
    private val onAutoPlayToggled: (Boolean) -> Unit,
    private val onEmptyActionClicked: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    enum class ViewType {
        BATTERY_BANNER,
        AUTO_START_BANNER,
        TARGET_CAR,
        EMPTY_STATE,
        PAIRED_DEVICES_TITLE
    }

    companion object {
        const val PAYLOAD_AUTO_PLAY = "PAYLOAD_AUTO_PLAY"
        const val PAYLOAD_CONNECTION_START_DELAY = "PAYLOAD_CONNECTION_START_DELAY"
        const val PAYLOAD_KEEP_ALIVE = "PAYLOAD_KEEP_ALIVE"
        const val PAYLOAD_AUTO_PLAY_ON_ANDROID_AUTO = "PAYLOAD_AUTO_PLAY_ON_ANDROID_AUTO"
        const val PAYLOAD_AFTERMARKET_ANDROID_AUTO_TARGET =
            "PAYLOAD_AFTERMARKET_ANDROID_AUTO_TARGET"
        const val PAYLOAD_AUDIO_PATH = "PAYLOAD_AUDIO_PATH"
        const val PAYLOAD_AUDIO_PATH_2 = "PAYLOAD_AUDIO_PATH_2"
        const val PAYLOAD_FLOATING_BUBBLE = "PAYLOAD_FLOATING_BUBBLE"
        const val PAYLOAD_PLAYBACK_STATE = "PAYLOAD_PLAYBACK_STATE"
        const val PAYLOAD_SERVER_SYNC_STATE = "PAYLOAD_SERVER_SYNC_STATE"
        const val PAYLOAD_ROUTING_TIER = "PAYLOAD_ROUTING_TIER"
        const val PAYLOAD_DEVICE_NAME = "PAYLOAD_DEVICE_NAME"
        const val PAYLOAD_EMPTY_STATE_CONTENT = "PAYLOAD_EMPTY_STATE_CONTENT"
    }

    private val items = buildItems(
        ScreenChromeState(
            showBatteryBanner = false,
            showAutoStartBanner = false,
            showTargetCar = false,
            showEmptyState = false
        )
    ).toMutableList()

    var showBatteryBanner = false
        private set

    var showAutoStartBanner = false
        private set

    var showTargetCar = false
        private set

    var showEmptyState = false
        private set

    var isBluetoothEnabled: Boolean = true

    // Data for Target Car
    var targetDeviceName: String = ""
    var isAutoPlayEnabled: Boolean = true
    var connectionStartDelaySeconds: Int = 0
    var isKeepAppAliveEnabled: Boolean = false
    var isAutoPlayOnAndroidAutoEnabled: Boolean = false
    var isAftermarketAndroidAutoTargetEnabled: Boolean = false
    var audioFilePath: String? = null
    var audioFilePath2: String? = null
    var isFloatingBubbleEnabled: Boolean = false
    var isPlaying: Boolean = false
    var isPlaybackPending: Boolean = false
    var isServerSyncing: Boolean = false
    var routingTier: Int = 1

    private var onAutoPlayOnAndroidAutoToggled: ((Boolean) -> Unit)? = null
    private var onAftermarketAndroidAutoTargetToggled: ((Boolean) -> Unit)? = null
    private var onConnectionStartDelayChanged: ((Int) -> Unit)? = null
    private var onKeepAliveToggled: ((Boolean) -> Unit)? = null
    private var onPlayStopClicked: (() -> Unit)? = null
    private var onSyncServerClicked: (() -> Unit)? = null
    private var onAudioPickerClicked: (() -> Unit)? = null
    private var onAudioPicker2Clicked: (() -> Unit)? = null
    private var onFloatingBubbleToggled: ((Boolean) -> Unit)? = null
    private var onRoutingTierChanged: ((Int) -> Unit)? = null

    fun setListeners(
        onAutoPlayOnAndroidAutoToggled: (Boolean) -> Unit,
        onAftermarketAndroidAutoTargetToggled: (Boolean) -> Unit,
        onConnectionStartDelayChanged: (Int) -> Unit,
        onKeepAliveToggled: (Boolean) -> Unit,
        onPlayStopClicked: () -> Unit,
        onSyncServerClicked: () -> Unit,
        onAudioPickerClicked: () -> Unit,
        onAudioPicker2Clicked: () -> Unit,
        onFloatingBubbleToggled: (Boolean) -> Unit,
        onRoutingTierChanged: (Int) -> Unit
    ) {
        this.onAutoPlayOnAndroidAutoToggled = onAutoPlayOnAndroidAutoToggled
        this.onAftermarketAndroidAutoTargetToggled = onAftermarketAndroidAutoTargetToggled
        this.onConnectionStartDelayChanged = onConnectionStartDelayChanged
        this.onKeepAliveToggled = onKeepAliveToggled
        this.onPlayStopClicked = onPlayStopClicked
        this.onSyncServerClicked = onSyncServerClicked
        this.onAudioPickerClicked = onAudioPickerClicked
        this.onAudioPicker2Clicked = onAudioPicker2Clicked
        this.onFloatingBubbleToggled = onFloatingBubbleToggled
        this.onRoutingTierChanged = onRoutingTierChanged
    }

    fun updateChrome(
        showBatteryBanner: Boolean = this.showBatteryBanner,
        showAutoStartBanner: Boolean = this.showAutoStartBanner,
        showTargetCar: Boolean = this.showTargetCar,
        showEmptyState: Boolean = this.showEmptyState
    ) {
        val nextState = ScreenChromeState(
            showBatteryBanner = showBatteryBanner,
            showAutoStartBanner = showAutoStartBanner,
            showTargetCar = showTargetCar,
            showEmptyState = showEmptyState
        )
        if (currentChromeState() == nextState) return

        val oldItems = items.toList()
        this.showBatteryBanner = nextState.showBatteryBanner
        this.showAutoStartBanner = nextState.showAutoStartBanner
        this.showTargetCar = nextState.showTargetCar
        this.showEmptyState = nextState.showEmptyState

        val newItems = buildItems(nextState)
        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = oldItems.size

            override fun getNewListSize(): Int = newItems.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return oldItems[oldItemPosition] == newItems[newItemPosition]
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return oldItems[oldItemPosition] == newItems[newItemPosition]
            }
        })

        items.clear()
        items.addAll(newItems)
        diffResult.dispatchUpdatesTo(this)
    }

    private fun currentChromeState(): ScreenChromeState {
        return ScreenChromeState(
            showBatteryBanner = showBatteryBanner,
            showAutoStartBanner = showAutoStartBanner,
            showTargetCar = showTargetCar,
            showEmptyState = showEmptyState
        )
    }

    private fun buildItems(state: ScreenChromeState): List<ViewType> {
        return buildList {
            if (state.showBatteryBanner) add(ViewType.BATTERY_BANNER)
            if (state.showAutoStartBanner) add(ViewType.AUTO_START_BANNER)
            if (state.showTargetCar) add(ViewType.TARGET_CAR)
            if (state.showEmptyState) add(ViewType.EMPTY_STATE)
            add(ViewType.PAIRED_DEVICES_TITLE)
        }
    }

    /** Call after changing [isBluetoothEnabled] while [showEmptyState] is true so the empty row updates. */
    fun notifyEmptyStateContentChanged() {
        val idx = items.indexOf(ViewType.EMPTY_STATE)
        if (idx >= 0) notifyItemChanged(idx, PAYLOAD_EMPTY_STATE_CONTENT)
    }

    fun notifyTargetCarChanged(payload: Any? = null) {
        val index = items.indexOf(ViewType.TARGET_CAR)
        if (index != -1) {
            notifyItemChanged(index, payload)
        }
    }

    override fun getItemViewType(position: Int): Int {
        return items[position].ordinal
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (ViewType.entries[viewType]) {
            ViewType.BATTERY_BANNER -> {
                BatteryBannerViewHolder(ItemBatteryBannerBinding.inflate(inflater, parent, false))
            }
            ViewType.AUTO_START_BANNER -> {
                AutoStartBannerViewHolder(ItemAutoStartBannerBinding.inflate(inflater, parent, false))
            }
            ViewType.TARGET_CAR -> {
                TargetCarViewHolder(ItemTargetCarBinding.inflate(inflater, parent, false))
            }
            ViewType.EMPTY_STATE -> {
                EmptyStateViewHolder(ItemEmptyStateBinding.inflate(inflater, parent, false))
            }
            ViewType.PAIRED_DEVICES_TITLE -> {
                SectionTitleViewHolder(ItemSectionTitleBinding.inflate(inflater, parent, false))
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        onBindViewHolder(holder, position, emptyList())
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: List<Any>
    ) {
        val type = items[position]
        val targetCarState = TargetCarState(
            deviceName = targetDeviceName,
            isAutoPlay = isAutoPlayEnabled,
            isAutoPlayOnAndroidAuto = isAutoPlayOnAndroidAutoEnabled,
            isAftermarketAndroidAutoTarget = isAftermarketAndroidAutoTargetEnabled,
            connectionStartDelaySeconds = connectionStartDelaySeconds,
            isKeepAlive = isKeepAppAliveEnabled,
            audioPath = audioFilePath,
            audioPath2 = audioFilePath2,
            isFloatingBubbleEnabled = isFloatingBubbleEnabled,
            isPlaying = isPlaying,
            isPlaybackPending = isPlaybackPending,
            isServerSyncing = isServerSyncing,
            routingTier = routingTier
        )
        if (payloads.isNotEmpty()) {
            when (type) {
                ViewType.TARGET_CAR -> {
                    (holder as TargetCarViewHolder).updatePartial(payloads, targetCarState)
                    return
                }
                ViewType.EMPTY_STATE -> {
                    (holder as EmptyStateViewHolder).updatePartial(
                        payloads,
                        isBluetoothEnabled,
                        onEmptyActionClicked
                    )
                    return
                }
                else -> Unit
            }
        }

        when (type) {
            ViewType.BATTERY_BANNER -> {
                (holder as BatteryBannerViewHolder).bind(onIgnoreBatteryOptClicked)
            }
            ViewType.AUTO_START_BANNER -> {
                (holder as AutoStartBannerViewHolder).bind(onOpenAutoStartSettingsClicked)
            }
            ViewType.TARGET_CAR -> {
                val callbacks = TargetCarCallbacks(
                    onAutoPlayToggled = onAutoPlayToggled,
                    onAutoPlayOnAndroidAutoToggled = { onAutoPlayOnAndroidAutoToggled?.invoke(it) },
                    onAftermarketAndroidAutoTargetToggled = {
                        onAftermarketAndroidAutoTargetToggled?.invoke(it)
                    },
                    onConnectionStartDelayChanged = { onConnectionStartDelayChanged?.invoke(it) },
                    onKeepAliveToggled = { onKeepAliveToggled?.invoke(it) },
                    onPlayStopClicked = { onPlayStopClicked?.invoke() },
                    onSyncServerClicked = { onSyncServerClicked?.invoke() },
                    onAudioPickerClicked = { onAudioPickerClicked?.invoke() },
                    onAudioPicker2Clicked = { onAudioPicker2Clicked?.invoke() },
                    onFloatingBubbleToggled = { onFloatingBubbleToggled?.invoke(it) },
                    onRoutingTierChanged = { onRoutingTierChanged?.invoke(it) }
                )
                (holder as TargetCarViewHolder).bind(targetCarState, callbacks)
            }
            ViewType.EMPTY_STATE -> {
                (holder as EmptyStateViewHolder).bind(isBluetoothEnabled, onEmptyActionClicked)
            }
            ViewType.PAIRED_DEVICES_TITLE -> {
                holder.itemView.setLayerType(View.LAYER_TYPE_NONE, null)
            }
        }
    }

    class BatteryBannerViewHolder(private val binding: ItemBatteryBannerBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(onClick: () -> Unit) {
            binding.root.setLayerType(View.LAYER_TYPE_NONE, null)
            binding.root.setOnClickListener { onClick() }
        }
    }

    class AutoStartBannerViewHolder(private val binding: ItemAutoStartBannerBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(onClick: () -> Unit) {
            binding.root.setLayerType(View.LAYER_TYPE_NONE, null)
            binding.root.setOnClickListener { onClick() }
        }
    }

    class TargetCarViewHolder(private val binding: ItemTargetCarBinding) :
        RecyclerView.ViewHolder(binding.root) {
        private var callbacks: TargetCarCallbacks? = null

        fun bind(state: TargetCarState, callbacks: TargetCarCallbacks) {
            val context = binding.root.context
            this.callbacks = callbacks
            binding.tvTargetDeviceName.text = state.deviceName
            binding.tvAudioFile.text = state.audioPath ?: context.getString(R.string.select_audio_file)
            binding.tvAudioFile2.text = state.audioPath2 ?: context.getString(R.string.select_audio_file)
            binding.clAudioPicker.setOnClickListener { callbacks.onAudioPickerClicked() }
            binding.clAudioPicker2.setOnClickListener { callbacks.onAudioPicker2Clicked() }

            bindAutoPlay(state.isAutoPlay)
            bindAutoPlayOnAndroidAuto(state.isAutoPlayOnAndroidAuto)
            bindAftermarketAndroidAutoTarget(
                isAftermarketAndroidAutoTarget = state.isAftermarketAndroidAutoTarget,
                isEnabled = state.isAutoPlayOnAndroidAuto
            )
            bindConnectionStartDelay(state.connectionStartDelaySeconds)
            bindKeepAlive(state.isKeepAlive)
            bindFloatingBubble(state.isFloatingBubbleEnabled)
            bindRoutingTier(state.routingTier)

            applyPlayStopButton(context, state.isPlaying, state.isPlaybackPending)
            binding.btnManualPlayStop.setOnClickListener { callbacks.onPlayStopClicked() }
            applySyncServerButton(context, state.isServerSyncing)
            binding.btnSyncServer.setOnClickListener { callbacks.onSyncServerClicked() }
            binding.ivMusicIcon.setImageResource(R.drawable.ic_greeting_round)
            binding.ivMusicIcon2.setImageResource(R.drawable.ic_goodbye_round)
        }

        private fun applyPlayStopButton(
            context: android.content.Context,
            isPlaying: Boolean,
            isPlaybackPending: Boolean
        ) {
            binding.btnManualPlayStop.isEnabled = !isPlaybackPending
            binding.btnManualPlayStop.alpha = if (isPlaybackPending) 0.72f else 1f

            if (isPlaybackPending) {
                binding.btnManualPlayStop.text = context.getString(R.string.starting_playback)
                binding.btnManualPlayStop.setIconResource(R.drawable.ic_play_round)
                binding.btnManualPlayStop.backgroundTintList =
                    ColorStateList.valueOf(ContextCompat.getColor(context, R.color.primary_blue))
                binding.btnManualPlayStop.setTextColor(ContextCompat.getColor(context, R.color.white))
                binding.btnManualPlayStop.iconTint =
                    ColorStateList.valueOf(ContextCompat.getColor(context, R.color.white))
            } else if (isPlaying) {
                binding.btnManualPlayStop.text = context.getString(R.string.stop)
                binding.btnManualPlayStop.setIconResource(R.drawable.ic_stop_round)
                binding.btnManualPlayStop.backgroundTintList =
                    ColorStateList.valueOf(ContextCompat.getColor(context, R.color.black))
                binding.btnManualPlayStop.setTextColor(ContextCompat.getColor(context, R.color.white))
                binding.btnManualPlayStop.iconTint =
                    ColorStateList.valueOf(ContextCompat.getColor(context, R.color.white))
            } else {
                binding.btnManualPlayStop.text = context.getString(R.string.play_now)
                binding.btnManualPlayStop.setIconResource(R.drawable.ic_play_round)
                binding.btnManualPlayStop.backgroundTintList =
                    ColorStateList.valueOf(ContextCompat.getColor(context, R.color.primary_blue))
                binding.btnManualPlayStop.setTextColor(ContextCompat.getColor(context, R.color.white))
                binding.btnManualPlayStop.iconTint =
                    ColorStateList.valueOf(ContextCompat.getColor(context, R.color.white))
            }
        }

        private fun applySyncServerButton(
            context: android.content.Context,
            isServerSyncing: Boolean
        ) {
            binding.btnSyncServer.isEnabled = !isServerSyncing
            binding.btnSyncServer.text = if (isServerSyncing) {
                context.getString(R.string.syncing_server)
            } else {
                context.getString(R.string.sync_server)
            }
        }

        fun updatePartial(payloads: List<Any>, state: TargetCarState) {
            val context = binding.root.context
            payloads.forEach { payload ->
                when (payload) {
                    PAYLOAD_AUTO_PLAY -> {
                        bindAutoPlay(state.isAutoPlay)
                    }
                    PAYLOAD_AUTO_PLAY_ON_ANDROID_AUTO -> {
                        bindAutoPlayOnAndroidAuto(state.isAutoPlayOnAndroidAuto)
                        bindAftermarketAndroidAutoTarget(
                            isAftermarketAndroidAutoTarget = state.isAftermarketAndroidAutoTarget,
                            isEnabled = state.isAutoPlayOnAndroidAuto
                        )
                    }
                    PAYLOAD_AFTERMARKET_ANDROID_AUTO_TARGET -> {
                        bindAftermarketAndroidAutoTarget(
                            isAftermarketAndroidAutoTarget = state.isAftermarketAndroidAutoTarget,
                            isEnabled = state.isAutoPlayOnAndroidAuto
                        )
                    }
                    PAYLOAD_CONNECTION_START_DELAY -> {
                        bindConnectionStartDelay(state.connectionStartDelaySeconds)
                    }
                    PAYLOAD_KEEP_ALIVE -> {
                        bindKeepAlive(state.isKeepAlive)
                    }
                    PAYLOAD_AUDIO_PATH -> {
                        binding.tvAudioFile.text =
                            state.audioPath ?: context.getString(R.string.select_audio_file)
                    }
                    PAYLOAD_AUDIO_PATH_2 -> {
                        binding.tvAudioFile2.text =
                            state.audioPath2 ?: context.getString(R.string.select_audio_file)
                    }
                    PAYLOAD_FLOATING_BUBBLE -> {
                        bindFloatingBubble(state.isFloatingBubbleEnabled)
                    }
                    PAYLOAD_PLAYBACK_STATE -> {
                        applyPlayStopButton(context, state.isPlaying, state.isPlaybackPending)
                    }
                    PAYLOAD_SERVER_SYNC_STATE -> {
                        applySyncServerButton(context, state.isServerSyncing)
                    }
                    PAYLOAD_ROUTING_TIER -> {
                        bindRoutingTier(state.routingTier)
                    }
                    PAYLOAD_DEVICE_NAME -> {
                        binding.tvTargetDeviceName.text = state.deviceName
                    }
                }
            }
        }

        private fun bindAutoPlay(isAutoPlay: Boolean) {
            binding.switchAutoPlay.setOnCheckedChangeListener(null)
            binding.switchAutoPlay.isChecked = isAutoPlay
            binding.layoutAutoPlay.setOnClickListener {
                binding.switchAutoPlay.toggle()
            }
            callbacks?.let { callbacks ->
                binding.switchAutoPlay.setOnCheckedChangeListener { _, isChecked ->
                    callbacks.onAutoPlayToggled(isChecked)
                }
            }
        }

        private fun bindAutoPlayOnAndroidAuto(isAutoPlayOnAndroidAuto: Boolean) {
            binding.switchAutoPlayOnAndroidAuto.setOnCheckedChangeListener(null)
            binding.switchAutoPlayOnAndroidAuto.isChecked = isAutoPlayOnAndroidAuto
            binding.layoutAutoPlayOnAndroidAuto.setOnClickListener {
                binding.switchAutoPlayOnAndroidAuto.toggle()
            }
            callbacks?.let { callbacks ->
                binding.switchAutoPlayOnAndroidAuto.setOnCheckedChangeListener { _, isChecked ->
                    callbacks.onAutoPlayOnAndroidAutoToggled(isChecked)
                }
            }
        }

        private fun bindAftermarketAndroidAutoTarget(
            isAftermarketAndroidAutoTarget: Boolean,
            isEnabled: Boolean
        ) {
            binding.switchAftermarketAndroidAutoTarget.setOnCheckedChangeListener(null)
            binding.switchAftermarketAndroidAutoTarget.isChecked = isAftermarketAndroidAutoTarget
            binding.switchAftermarketAndroidAutoTarget.isEnabled = isEnabled
            binding.layoutAftermarketAndroidAutoTarget.isEnabled = isEnabled
            binding.layoutAftermarketAndroidAutoTarget.alpha = if (isEnabled) 1.0f else 0.5f

            if (isEnabled) {
                binding.layoutAftermarketAndroidAutoTarget.setOnClickListener {
                    binding.switchAftermarketAndroidAutoTarget.toggle()
                }
                callbacks?.let { callbacks ->
                    binding.switchAftermarketAndroidAutoTarget.setOnCheckedChangeListener { _, isChecked ->
                        callbacks.onAftermarketAndroidAutoTargetToggled(isChecked)
                    }
                }
            } else {
                binding.layoutAftermarketAndroidAutoTarget.setOnClickListener(null)
            }
        }

        private fun bindConnectionStartDelay(seconds: Int) {
            val clampedSeconds = seconds.coerceIn(0, 10)
            binding.sliderConnectionStartDelay.clearOnChangeListeners()
            binding.sliderConnectionStartDelay.value = clampedSeconds.toFloat()
            binding.tvConnectionStartDelayValue.text =
                binding.root.context.getString(R.string.connection_delay_seconds_value, clampedSeconds)
            callbacks?.let { callbacks ->
                binding.sliderConnectionStartDelay.addOnChangeListener { _, value, fromUser ->
                    val roundedValue = value.toInt().coerceIn(0, 10)
                    binding.tvConnectionStartDelayValue.text =
                        binding.root.context.getString(
                            R.string.connection_delay_seconds_value,
                            roundedValue
                        )
                    if (fromUser) {
                        callbacks.onConnectionStartDelayChanged(roundedValue)
                    }
                }
            }
        }

        private fun bindKeepAlive(isKeepAlive: Boolean) {
            binding.switchKeepAlive.setOnCheckedChangeListener(null)
            binding.switchKeepAlive.isChecked = isKeepAlive
            binding.layoutKeepAlive.setOnClickListener {
                binding.switchKeepAlive.toggle()
            }
            callbacks?.let { callbacks ->
                binding.switchKeepAlive.setOnCheckedChangeListener { _, isChecked ->
                    callbacks.onKeepAliveToggled(isChecked)
                }
            }
        }

        private fun bindFloatingBubble(enabled: Boolean) {
            binding.switchFloatingBubble.setOnCheckedChangeListener(null)
            binding.switchFloatingBubble.isChecked = enabled
            binding.layoutFloatingBubble.setOnClickListener {
                binding.switchFloatingBubble.toggle()
            }
            callbacks?.let { callbacks ->
                binding.switchFloatingBubble.setOnCheckedChangeListener { _, isChecked ->
                    callbacks.onFloatingBubbleToggled(isChecked)
                }
            }
        }

        private fun bindRoutingTier(tier: Int) {
            binding.toggleGroupTier.clearOnButtonCheckedListeners()
            val checkedId = when (tier) {
                1 -> R.id.btnTier1
                2 -> R.id.btnTier2
                3 -> R.id.btnTier3
                else -> R.id.btnTier1
            }
            binding.toggleGroupTier.check(checkedId)
            updateTierDescription(tier)
            callbacks?.let { callbacks ->
                binding.toggleGroupTier.addOnButtonCheckedListener { _, buttonId, isChecked ->
                    if (isChecked) {
                        val selectedTier = when (buttonId) {
                            R.id.btnTier1 -> 1
                            R.id.btnTier2 -> 2
                            R.id.btnTier3 -> 3
                            else -> 1
                        }
                        callbacks.onRoutingTierChanged(selectedTier)
                        updateTierDescription(selectedTier)
                    }
                }
            }
        }

        private fun updateTierDescription(tier: Int) {
            val descResId = when (tier) {
                1 -> R.string.tier_1_desc
                2 -> R.string.tier_2_desc
                3 -> R.string.tier_3_desc
                else -> R.string.tier_1_desc
            }
            binding.tvTierDescription.setText(descResId)
        }
    }

    class EmptyStateViewHolder(private val binding: ItemEmptyStateBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(bluetoothEnabled: Boolean, onClick: () -> Unit) {
            bindContent(bluetoothEnabled)
            binding.btnEmptyAction.setOnClickListener { onClick() }
        }

        fun updatePartial(payloads: List<Any>, bluetoothEnabled: Boolean, onClick: () -> Unit) {
            if (payloads.contains(PAYLOAD_EMPTY_STATE_CONTENT)) {
                bindContent(bluetoothEnabled)
                binding.btnEmptyAction.setOnClickListener { onClick() }
                return
            }
            bind(bluetoothEnabled, onClick)
        }

        private fun bindContent(bluetoothEnabled: Boolean) {
            if (bluetoothEnabled) {
                binding.tvEmptyTitle.setText(R.string.no_devices_found)
                binding.tvEmptyDesc.setText(R.string.no_devices_desc)
                binding.btnEmptyAction.setText(R.string.refresh_devices)
            } else {
                binding.tvEmptyTitle.setText(R.string.bluetooth_disabled_title)
                binding.tvEmptyDesc.setText(R.string.bluetooth_disabled_desc)
                binding.btnEmptyAction.setText(R.string.enable_bluetooth)
            }
        }
    }

    class SectionTitleViewHolder(binding: ItemSectionTitleBinding) :
        RecyclerView.ViewHolder(binding.root)
}
