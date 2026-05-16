package com.vibegravity.bluecruise.domain

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.KeyEvent
import androidx.core.content.getSystemService
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer
import com.vibegravity.bluecruise.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmartAutoRoutingEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : com.vibegravity.bluecruise.domain.RoutingExecutor {

    /**
     * Check if Android Auto is currently connected.
     * When AA is connected, we previously skipped routing exploits; now we only log.
     */
    @Suppress("unused")
    internal fun isAndroidAutoConnected(): Boolean {
        return try {
            val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as android.app.UiModeManager
            val isCarMode = uiModeManager.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_CAR
            Timber.d("Android Auto detection: carMode=$isCarMode")
            isCarMode
        } catch (e: Exception) {
            Timber.e(e, "Error checking Android Auto connection")
            false
        }
    }

    override suspend fun executeRoutingExploit(tier: Int, mediaSessionToken: Any?) {
        val aaConnected = isAndroidAutoConnected()
        
        if (aaConnected) {
            Timber.d("Android Auto connected - skipping routing exploit, letting AA handle audio")
            return
        }

        val mediaSessionCompat = mediaSessionToken as? MediaSessionCompat
        executeRoutingExploitInternal(tier, mediaSessionCompat)
    }

    /**
     * Executes the smart routing logic based on the provided tier.
     * Ensures the code runs gracefully without throwing fatal exceptions on unsupported devices.
     */
    private suspend fun executeRoutingExploitInternal(tier: Int, mediaSessionCompat: MediaSessionCompat?) {
        Timber.d("Executing Audio Routing Exploit. Tier: $tier")
        val audioManager = context.getSystemService<AudioManager>() ?: return

        when (tier) {
            1 -> executeTier1NavigationExploit()
            2 -> executeTier2StateToggling(mediaSessionCompat)
            3 -> executeTier3ScoExploit(audioManager)
            else -> {
                Timber.d("Invalid or Passive Tier ($tier). Performing standard Media Play KeyEvent.")
                executeStandardPlayDispatch(audioManager)
            }
        }
    }

    /**
     * TIER 1 (The Gentle Way):
     * Plays a silent track tagged with Navigation usage.
     * Most car head-units will duck radio and switch to Bluetooth silently for navigation prompts.
     */
    private suspend fun executeTier1NavigationExploit() {
        withContext(Dispatchers.Main) {
            @Suppress("TooGenericExceptionCaught")
            try {
                Timber.d("Tier 1: Starting Navigation Audio Exploit")
                // ExoPlayer only allows automatic audio focus for USAGE_MEDIA/USAGE_GAME, so we use
                // USAGE_MEDIA and disable handleAudioFocus to avoid IllegalArgumentException.
                // The short silent burst still nudges many head units to switch to Bluetooth.
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build()

                val dummyPlayer = ExoPlayer.Builder(context)
                    .setAudioAttributes(audioAttributes, false)
                    .build()

                // Load a raw dummy silent file
                // Note: Ensure res/raw/dummy_silence.wav exists and is a valid audio file.
                val uri = "android.resource://${context.packageName}/${R.raw.dummy_silence}"
                val mediaItem = MediaItem.Builder()
                    .setUri(uri)
                    .setMimeType(MimeTypes.AUDIO_WAV)
                    .build()
                dummyPlayer.setMediaItem(mediaItem)
                dummyPlayer.prepare()
                dummyPlayer.play()

                // Wait for the dummy sound duration
                delay(200)
                dummyPlayer.release()
            } catch (e: Exception) {
                Timber.e(e, "Tier 1 Exploit Failed")
            }
        }
    }

    /**
     * TIER 2 (The Trickster):
     * Rapidly toggles PlaybackState through MediaSessionCompat.
     * Simulates "New Media Device Inserted" to trick AAOS into switching sources automatically.
     */
    private suspend fun executeTier2StateToggling(mediaSessionCompat: MediaSessionCompat?) {
        if (mediaSessionCompat == null) {
            Timber.w("MediaSessionCompat is null, skipping Tier 2")
            return
        }
        @Suppress("TooGenericExceptionCaught")
        try {
            Timber.d("Tier 2: Starting State Toggling Exploit")
            
            // Step 1: Force state clearly to NONE (Clears Car UI)
            val stateNone = PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_NONE, 0L, 1.0f)
                .build()
            mediaSessionCompat.setPlaybackState(stateNone)
            delay(150)

            // Step 2: Transition to BUFFERING
            val stateBuffering = PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_BUFFERING, 0L, 1.0f)
                .build()
            mediaSessionCompat.setPlaybackState(stateBuffering)
            delay(150)

            // Step 3: Force PLAYING
            val statePlaying = PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_PLAYING, 0L, 1.0f)
                .build()
            mediaSessionCompat.setPlaybackState(statePlaying)
            
            Timber.d("Tier 2 Exploit completed.")
        } catch (e: Exception) {
            Timber.e(e, "Tier 2 Exploit Failed")
        }
    }

    /**
     * TIER 3 (The Aggressive Way):
     * Momentarily initiates a Hands-Free Profile (HFP) Fake Call via SCO.
     * Absolute Interrupt Priority overrides the car's radio strictly.
     */
    @Suppress("DEPRECATION")
    private suspend fun executeTier3ScoExploit(audioManager: AudioManager) {
        @Suppress("TooGenericExceptionCaught")
        try {
            Timber.d("Tier 3: Starting SCO Fake-Call Exploit")
            // 1. Claim communication mode
            val originalMode = audioManager.mode
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            // 2. Start SCO (Triggers Car UI to "In Call")
            audioManager.isBluetoothScoOn = true
            audioManager.startBluetoothSco()
            // Wait for Car Head Unit to catch up and switch mode
            delay(1000)
            // 3. Clean up and restore
            audioManager.stopBluetoothSco()
            audioManager.isBluetoothScoOn = false
            audioManager.mode = originalMode
            delay(200) // Small breather before music starts
            Timber.d("Tier 3 Exploit completed. Restored to NORMAL mode.")
        } catch (e: Exception) {
            Timber.e(e, "Tier 3 Exploit Failed")
            // Ensure safe restoration
            audioManager.stopBluetoothSco()
            audioManager.isBluetoothScoOn = false
            audioManager.mode = AudioManager.MODE_NORMAL
        }
    }

    /**
     * Standard Dispatch as fallback for older conventional radios.
     */
    private fun executeStandardPlayDispatch(audioManager: AudioManager) {
        val eventTime = SystemClock.uptimeMillis()
        val downEvent = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY, 0)
        val upEvent = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY, 0)

        audioManager.dispatchMediaKeyEvent(downEvent)
        audioManager.dispatchMediaKeyEvent(upEvent)
        Timber.d("Dispatched standard Media Play KeyEvent")
    }
}
