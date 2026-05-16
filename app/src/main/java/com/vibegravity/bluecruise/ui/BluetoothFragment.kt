package com.vibegravity.bluecruise.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ConcatAdapter
import com.vibegravity.bluecruise.R
import com.vibegravity.bluecruise.auth.LoginActivity
import com.vibegravity.bluecruise.common.AudioPermissionRules
import com.vibegravity.bluecruise.common.PermissionsHelper
import com.vibegravity.bluecruise.service.AutoPlayMusicService
import com.vibegravity.bluecruise.service.FloatingBubbleController
import com.vibegravity.bluecruise.service.resolveFloatingBubbleToggleDecision
import com.vibegravity.bluecruise.utils.isAutoStartGranted
import com.vibegravity.bluecruise.utils.isAutoStartSupported
import com.vibegravity.bluecruise.utils.isOnlineOrDriveUri
import com.vibegravity.bluecruise.utils.requestAutoStartPermission
import com.vibegravity.bluecruise.service.KeepAliveService
import com.vibegravity.bluecruise.databinding.FragmentBluetoothBinding
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class BluetoothFragment : Fragment() {

    private var _binding: FragmentBluetoothBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BluetoothViewModel by viewModels()

    @Inject
    lateinit var floatingBubbleController: FloatingBubbleController
    
    private lateinit var screenAdapter: BluetoothScreenAdapter
    private lateinit var deviceAdapter: BluetoothDeviceAdapter
    private lateinit var logoutFooterAdapter: LogoutFooterAdapter
    private lateinit var concatAdapter: ConcatAdapter
    private var pendingAudioSlot: Int = AUDIO_SLOT_PRIMARY
    
    private val permissionsHelper by lazy { PermissionsHelper(requireContext()) }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (granted) {
            viewModel.refreshPairedDevices()
        } else {
            Toast.makeText(requireContext(), "Bluetooth permissions required", Toast.LENGTH_SHORT).show()
        }
    }

    private var pendingAudioPick = false
    private val requestAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (granted && pendingAudioPick) {
            pendingAudioPick = false
            pickAudioLauncher.launch(arrayOf("audio/*"))
        } else if (!granted) {
            pendingAudioPick = false
            Toast.makeText(requireContext(), R.string.audio_permission_required, Toast.LENGTH_SHORT).show()
        }
    }

    private val pickAudioLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            if (isOnlineOrDriveUri(it)) {
                Toast.makeText(requireContext(), R.string.audio_select_local_only, Toast.LENGTH_LONG).show()
                return@let
            }
            try {
                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
                requireContext().contentResolver.takePersistableUriPermission(it, takeFlags)
            } catch (e: SecurityException) {
                Timber.e(e, "Failed to take persistable URI permission")
            }
            when (pendingAudioSlot) {
                AUDIO_SLOT_SECONDARY -> viewModel.setAudioFilePath2(it.toString())
                else -> viewModel.setAudioFilePath(it.toString())
            }
        }
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        maybeEnableFloatingBubbleAfterPermissionGrant()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBluetoothBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        observeViewModel()
        checkPermissionsAndLoadDevices()
        refreshChromeBanners()
    }

    private fun setupRecyclerView() {
        screenAdapter = BluetoothScreenAdapter(
            onIgnoreBatteryOptClicked = {
                runIfInteractionsAllowed { requestIgnoreBatteryOptimizations() }
            },
            onOpenAutoStartSettingsClicked = {
                runIfInteractionsAllowed { requestAutoStartPermission(requireContext()) }
            },
            onAutoPlayToggled = { enabled ->
                runIfInteractionsAllowed { viewModel.setAutoPlayEnabled(enabled) }
            },
            onEmptyActionClicked = {
                runIfInteractionsAllowed {
                    if (viewModel.uiState.value.isBluetoothEnabled) {
                        checkPermissionsAndLoadDevices()
                    } else {
                        startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                    }
                }
            }
        )
        
        screenAdapter.setListeners(
            onAutoPlayOnAndroidAutoToggled = { enabled ->
                runIfInteractionsAllowed { viewModel.setAutoPlayOnAndroidAutoEnabled(enabled) }
            },
            onAftermarketAndroidAutoTargetToggled = {
                runIfInteractionsAllowed { viewModel.setAftermarketAndroidAutoTargetEnabled(it) }
            },
            onConnectionStartDelayChanged = {
                runIfInteractionsAllowed { viewModel.setConnectionStartDelaySeconds(it) }
            },
            onKeepAliveToggled = { enabled ->
                runIfInteractionsAllowed { viewModel.setKeepAppAliveEnabled(enabled) }
            },
            onPlayStopClicked = {
                runIfInteractionsAllowed {
                    val state = viewModel.uiState.value
                    when {
                        state.isPlaybackPending -> Unit
                        state.isPlaying -> {
                            stopManualPlaybackService()
                            viewModel.onManualPlaybackStopRequested()
                        }
                        else -> {
                            startManualPlaybackService()
                            viewModel.onManualPlaybackStartRequested()
                        }
                    }
                }
            },
            onSyncServerClicked = { runIfInteractionsAllowed { viewModel.syncServerSongs() } },
            onAudioPickerClicked = {
                runIfInteractionsAllowed {
                    pendingAudioSlot = AUDIO_SLOT_PRIMARY
                    ensureAudioPermissionThenPick()
                }
            },
            onAudioPicker2Clicked = {
                runIfInteractionsAllowed {
                    pendingAudioSlot = AUDIO_SLOT_SECONDARY
                    ensureAudioPermissionThenPick()
                }
            },
            onFloatingBubbleToggled = { enabled ->
                runIfInteractionsAllowed { handleFloatingBubbleToggle(enabled) }
            },
            onRoutingTierChanged = { tier ->
                runIfInteractionsAllowed { viewModel.setRoutingTier(tier) }
            }
        )
        
        deviceAdapter = BluetoothDeviceAdapter { macAddress ->
            runIfInteractionsAllowed { viewModel.setTargetDevice(macAddress) }
        }

        logoutFooterAdapter = LogoutFooterAdapter {
            handleLogoutClicked()
        }
        
        concatAdapter = ConcatAdapter(screenAdapter, deviceAdapter, logoutFooterAdapter)
        binding.rvMain.adapter = concatAdapter
        binding.rvMain.setItemViewCacheSize(8)
        binding.rvMain.setHasFixedSize(true)
    }

    private fun startManualPlaybackService() {
        ContextCompat.startForegroundService(
            requireContext(),
            Intent(requireContext(), AutoPlayMusicService::class.java).apply {
                action = AutoPlayMusicService.ACTION_START_PLAYBACK
            }
        )
    }

    private fun stopManualPlaybackService() {
        requireContext().startService(
            Intent(requireContext(), AutoPlayMusicService::class.java).apply {
                action = AutoPlayMusicService.ACTION_STOP_PLAYBACK
            }
        )
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    deviceAdapter.selectedMacAddress = state.targetMacAddress
                    deviceAdapter.submitList(state.pairedDevices)

                    val renderPlan = buildBluetoothScreenRenderPlan(screenAdapter.toSnapshot(), state)
                    screenAdapter.applySnapshot(renderPlan.nextSnapshot)
                    screenAdapter.updateChrome(
                        showTargetCar = renderPlan.nextSnapshot.showTargetCar,
                        showEmptyState = renderPlan.nextSnapshot.showEmptyState
                    )

                    if (renderPlan.shouldSyncKeepAliveService) {
                        syncKeepAliveService(renderPlan.nextSnapshot.isKeepAliveEnabled)
                    }
                    if (renderPlan.shouldSyncFloatingBubbleService) {
                        syncFloatingBubbleService(renderPlan.nextSnapshot.isFloatingBubbleEnabled)
                    }
                    renderPlan.targetCarPayloads.forEach(screenAdapter::notifyTargetCarChanged)
                    if (renderPlan.shouldRefreshEmptyStateContent) {
                        screenAdapter.notifyEmptyStateContentChanged()
                    }
                    logoutFooterAdapter.isLoggingOut = state.isLoggingOut
                    if (state.navigateToLogin) {
                        viewModel.consumeLogoutNavigation()
                        navigateToLogin()
                        return@collect
                    }
                    state.serverSyncMessage?.let { message ->
                        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                        viewModel.consumeServerSyncMessage()
                    }
                }
            }
        }
    }

    private fun handleLogoutClicked() {
        stopManualPlaybackService()
        syncKeepAliveService(false)
        floatingBubbleController.stopService()
        viewModel.onLogoutRequested()
    }

    private fun runIfInteractionsAllowed(action: () -> Unit) {
        val state = viewModel.uiState.value
        if (!state.isLoggingOut && !state.navigateToLogin) {
            action()
        }
    }

    private fun navigateToLogin() {
        startActivity(
            Intent(requireContext(), LoginActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        )
        requireActivity().finish()
    }

    private fun syncKeepAliveService(enabled: Boolean) {
        val context = requireContext()
        val serviceIntent = Intent(context, KeepAliveService::class.java)
        if (enabled) {
            ContextCompat.startForegroundService(context, serviceIntent)
        } else {
            context.stopService(serviceIntent)
        }
    }

    private fun handleFloatingBubbleToggle(enabled: Boolean) {
        val decision = resolveFloatingBubbleToggleDecision(
            enabled = enabled,
            hasOverlayPermission = floatingBubbleController.hasOverlayPermission()
        )

        if (decision.requestPermission) {
            viewModel.markFloatingBubblePermissionPending()
        } else {
            viewModel.clearFloatingBubblePermissionPending()
        }
        if (viewModel.uiState.value.floatingBubbleEnabled != decision.persistEnabled) {
            viewModel.setFloatingBubbleEnabled(decision.persistEnabled)
        }
        if (decision.startService) {
            floatingBubbleController.startService()
        }
        if (decision.stopService) {
            floatingBubbleController.stopService()
        }
        if (decision.requestPermission) {
            overlayPermissionLauncher.launch(floatingBubbleController.createOverlayPermissionIntent())
        }
    }

    private fun maybeEnableFloatingBubbleAfterPermissionGrant() {
        if (viewModel.consumeFloatingBubblePermissionGrant(floatingBubbleController.hasOverlayPermission())) {
            viewModel.setFloatingBubbleEnabled(true)
            floatingBubbleController.startService()
        }
    }

    private fun resyncFloatingBubbleOnResume() {
        if (viewModel.uiState.value.floatingBubbleEnabled && floatingBubbleController.hasOverlayPermission()) {
            floatingBubbleController.startService()
        }
    }

    private fun syncFloatingBubbleService(enabled: Boolean) {
        when {
            !enabled -> floatingBubbleController.stopService()
            floatingBubbleController.hasOverlayPermission() -> floatingBubbleController.startService()
            else -> {
                floatingBubbleController.stopService()
                if (viewModel.uiState.value.floatingBubbleEnabled) {
                    viewModel.setFloatingBubbleEnabled(false)
                }
            }
        }
    }

    private fun checkPermissionsAndLoadDevices() {
        if (permissionsHelper.hasBluetoothPermissions()) {
            viewModel.refreshPairedDevices()
        } else {
            permissionsHelper.requestBluetoothPermissions(requestPermissionLauncher)
        }
    }

    private fun ensureAudioPermissionThenPick() {
        val permissions = AudioPermissionRules.requiredAudioPermissions()
        val granted = permissions.all {
            ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
        }
        if (granted) {
            pickAudioLauncher.launch(arrayOf("audio/*"))
        } else {
            pendingAudioPick = true
            requestAudioPermissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun requestIgnoreBatteryOptimizations() {
        permissionsHelper.requestIgnoreBatteryOptimizations { intent ->
            startActivity(intent)
        }
    }

    private fun refreshChromeBanners() {
        screenAdapter.updateChrome(
            showBatteryBanner = !permissionsHelper.isIgnoringBatteryOptimizations(),
            showAutoStartBanner = isAutoStartSupported() && !isAutoStartGranted(requireContext())
        )
    }

    override fun onResume() {
        super.onResume()
        refreshChromeBanners()
        maybeEnableFloatingBubbleAfterPermissionGrant()
        resyncFloatingBubbleOnResume()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val AUDIO_SLOT_PRIMARY = 1
        const val AUDIO_SLOT_SECONDARY = 2
    }
}

