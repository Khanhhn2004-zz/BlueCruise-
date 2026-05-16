package com.vibegravity.bluecruise.service

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer
import com.vibegravity.bluecruise.R
import com.vibegravity.bluecruise.domain.RoutingExecutor
import com.vibegravity.bluecruise.domain.repository.SettingsRepository
import com.vibegravity.bluecruise.utils.isOnlineOrDriveUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

class PlaybackOrchestrator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val routingExecutor: RoutingExecutor
) {
    suspend fun startPlayback(
        player: ExoPlayer,
        defaultAudioUri: String? = null,
        routingSessionToken: Any? = null,
        audioSlot: Int = AutoPlayMusicService.AUDIO_SLOT_PRIMARY,
        audioUriOverride: String? = null
    ): Boolean {
        val playbackConfig = loadPlaybackConfig(audioSlot)

        val effectiveUriString = audioUriOverride?.takeIf { it.isNotBlank() }
            ?: playbackConfig.savedAudioPath
            ?: defaultAudioUri?.takeIf { it.isNotBlank() }
            ?: resolveBundledDefaultAudioUri(audioSlot)
            ?: run {
            Timber.w("No audio path or default URI available")
            return false
        }

        Timber.d("Starting playback with URI: $effectiveUriString")

        val uri = Uri.parse(effectiveUriString)
        if (uri.scheme == "content" && isOnlineOrDriveUri(uri)) {
            Timber.e("Selected audio URI points to online content; aborting playback")
            return false
        }

        if (!canOpen(uri)) {
            Timber.e("Selected audio URI is unavailable; aborting playback")
            return false
        }

        val mimeType = resolveMimeType(uri, effectiveUriString)
        if (mimeType?.startsWith("audio") != true) {
            Timber.e("Selected audio URI is not a supported audio type; aborting playback")
            return false
        }

        routingExecutor.executeRoutingExploit(playbackConfig.routingTier, routingSessionToken)

        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .setMimeType(mimeType)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(playbackConfig.displayTitle ?: resolveDisplayTitleFromUri(audioSlot, effectiveUriString))
                    .build()
            )
            .build()
        player.setMediaItem(mediaItem, 0L)
        player.prepare()
        player.play()

        return true
    }

    fun stopPlayback(player: ExoPlayer) {
        player.pause()
        player.stop()
    }

    private suspend fun loadPlaybackConfig(audioSlot: Int): PlaybackConfig = withContext(Dispatchers.IO) {
        val resolvedAudioPath = when (audioSlot) {
            AutoPlayMusicService.AUDIO_SLOT_SECONDARY ->
                settingsRepository.audioFilePath2Flow.firstOrNull()
            else ->
                settingsRepository.audioFilePathFlow.firstOrNull()
        }?.takeIf { it.isNotBlank() }

        val storedTitle = when (audioSlot) {
            AutoPlayMusicService.AUDIO_SLOT_SECONDARY ->
                settingsRepository.serverAudioTitle2Flow.firstOrNull()
            else ->
                settingsRepository.serverAudioTitle1Flow.firstOrNull()
        }?.takeIf { it.isNotBlank() }

        PlaybackConfig(
            savedAudioPath = resolvedAudioPath,
            displayTitle = storedTitle ?: resolveDisplayTitleFromUri(audioSlot, resolvedAudioPath),
            routingTier = settingsRepository.routingTierFlow.firstOrNull() ?: 1
        )
    }

    private fun resolveMimeType(uri: Uri, effectiveUriString: String): String? {
        return when (uri.scheme) {
            "android.resource", "rawresource" -> MimeTypes.AUDIO_MPEG
            "content" -> context.contentResolver.getType(uri)
            "file", "http", "https" -> {
                val extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
            }
            null -> {
                val extension = MimeTypeMap.getFileExtensionFromUrl(effectiveUriString)
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
            }
            else -> null
        }
    }

    private fun canOpen(uri: Uri): Boolean {
        return try {
            when (uri.scheme) {
                null, "file" -> {
                    val filePath = uri.path ?: return false
                    File(filePath).exists()
                }
                "http", "https" -> true
                else -> {
                    context.contentResolver.openInputStream(uri)?.use { } != null
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to open audio URI: $uri")
            false
        }
    }

    private fun resolveBundledDefaultAudioUri(audioSlot: Int): String? {
        val resourceId = when (audioSlot) {
            AutoPlayMusicService.AUDIO_SLOT_SECONDARY -> R.raw.default_goodbye
            else -> R.raw.default_greeting
        }
        return if (resourceId == 0) {
            null
        } else {
            "android.resource://${context.packageName}/$resourceId"
        }
    }

    private fun resolveDisplayTitleFromUri(audioSlot: Int, uriString: String?): String? {
        val candidate = uriString
            ?.substringAfterLast('/')
            ?.substringAfterLast(':')
            ?.let { raw -> raw.substringBeforeLast('.', missingDelimiterValue = raw).trim() }
            .orEmpty()

        if (candidate.isBlank()) {
            return defaultSlotLabel(audioSlot)
        }

        val normalized = if (candidate.startsWith("customer_")) {
            candidate.removePrefix("customer_").substringAfter('_', candidate)
        } else {
            candidate
        }.replace('_', ' ').trim()

        return when (normalized.lowercase()) {
            "hello" -> context.getString(R.string.song_1_label)
            "goodbye" -> context.getString(R.string.song_2_label)
            else -> normalized.ifBlank { defaultSlotLabel(audioSlot) }
        }
    }

    private fun defaultSlotLabel(audioSlot: Int): String {
        return if (audioSlot == AutoPlayMusicService.AUDIO_SLOT_SECONDARY) {
            context.getString(R.string.song_2_label)
        } else {
            context.getString(R.string.song_1_label)
        }
    }

    private data class PlaybackConfig(
        val savedAudioPath: String?,
        val displayTitle: String?,
        val routingTier: Int
    )
}
