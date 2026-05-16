package com.vibegravity.bluecruise.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.res.ColorStateList
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.vibegravity.bluecruise.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

internal sealed interface FloatingBubblePlaybackCommand {
    data class Start(val slot: Int) : FloatingBubblePlaybackCommand
    data object Stop : FloatingBubblePlaybackCommand
}

@AndroidEntryPoint
class FloatingBubbleService : Service() {

    @Inject
    lateinit var playbackRuntimeStateStore: PlaybackRuntimeStateStore

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var song1Button: ImageButton? = null
    private var song2Button: ImageButton? = null
    private var dismissTargetView: View? = null
    private var dismissTargetLayoutParams: WindowManager.LayoutParams? = null
    private var playbackUiState = FloatingBubblePlaybackUiState()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForegroundWithNotification()
        setupFloatingView()
        observePlaybackRuntimeState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Floating Bubble Controls",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundWithNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.floating_bubble_desc))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun setupFloatingView() {
        val manager = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager = manager
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 96
            y = 160
        }
        layoutParams = params

        val view = LayoutInflater.from(this).inflate(R.layout.view_floating_bubble, null, false)
        view.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val viewport = createViewport(view)
        val initialPosition = clampFloatingBubblePosition(
            position = FloatingBubblePoint(x = 96, y = 160),
            viewport = viewport
        )
        params.x = initialPosition.x
        params.y = initialPosition.y
        floatingView = view
        song1Button = view.findViewById<ImageButton>(R.id.btnFloatingSong1).apply {
            setOnClickListener { handleSlotTap(AutoPlayMusicService.AUDIO_SLOT_PRIMARY) }
        }
        song2Button = view.findViewById<ImageButton>(R.id.btnFloatingSong2).apply {
            setOnClickListener { handleSlotTap(AutoPlayMusicService.AUDIO_SLOT_SECONDARY) }
        }
        bindDrag(view, params, manager, viewport)
        updateButtons()

        runCatching { manager.addView(view, params) }
            .onFailure {
                stopSelf()
            }
    }

    private fun bindDrag(
        root: View,
        params: WindowManager.LayoutParams,
        manager: WindowManager,
        viewport: FloatingBubbleViewport
    ) {
        var startX = 0
        var startY = 0
        var downRawX = 0f
        var downRawY = 0f
        var dragged = false
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        val touchListener = View.OnTouchListener { touchedView, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    downRawX = event.rawX
                    downRawY = event.rawY
                    dragged = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - downRawX).toInt()
                    val deltaY = (event.rawY - downRawY).toInt()
                    if (!dragged && (kotlin.math.abs(deltaX) > touchSlop || kotlin.math.abs(deltaY) > touchSlop)) {
                        dragged = true
                        showDismissTarget(manager, viewport)
                    }
                    if (dragged) {
                        val nextPosition = clampFloatingBubblePosition(
                            position = FloatingBubblePoint(
                                x = startX + deltaX,
                                y = startY + deltaY
                            ),
                            viewport = viewport
                        )
                        params.x = nextPosition.x
                        params.y = nextPosition.y
                        manager.updateViewLayout(root, params)
                        updateDismissTargetHoverState(
                            resolveFloatingBubbleDrop(
                                position = nextPosition,
                                viewport = viewport,
                                dismissTarget = dismissTargetRect(viewport)
                            ).shouldDismiss
                        )
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragged) {
                        val dropResult = resolveFloatingBubbleDrop(
                            position = FloatingBubblePoint(x = params.x, y = params.y),
                            viewport = viewport,
                            dismissTarget = dismissTargetRect(viewport)
                        )
                        hideDismissTarget(manager)
                        if (dropResult.shouldDismiss) {
                            stopSelf()
                        } else {
                            params.x = dropResult.position.x
                            params.y = dropResult.position.y
                            manager.updateViewLayout(root, params)
                        }
                    } else if (touchedView is ImageButton) {
                        touchedView.performClick()
                    } else {
                        hideDismissTarget(manager)
                    }
                    dragged = false
                    true
                }
                else -> true
            }
        }
        root.setOnTouchListener(touchListener)
        song1Button?.setOnTouchListener(touchListener)
        song2Button?.setOnTouchListener(touchListener)
    }

    private fun observePlaybackRuntimeState() {
        scope.launch {
            playbackRuntimeStateStore.state.collect { runtimeState ->
                playbackUiState = mapPlaybackRuntimeStateToBubbleUiState(runtimeState)
                updateButtons()
            }
        }
    }

    private fun handleSlotTap(slot: Int) {
        val result = resolveFloatingBubbleTap(
            tappedSlot = slot,
            state = playbackUiState
        )
        when (val command = result.command) {
            is FloatingBubblePlaybackCommand.Start -> {
                startPlayback(command.slot)
            }
            FloatingBubblePlaybackCommand.Stop -> {
                stopPlayback()
            }
        }
    }

    private fun startPlayback(slot: Int) {
        val intent = Intent(this, AutoPlayMusicService::class.java).apply {
            action = AutoPlayMusicService.ACTION_START_PLAYBACK
            putExtra(AutoPlayMusicService.EXTRA_AUDIO_SLOT, slot)
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopPlayback() {
        startService(Intent(this, AutoPlayMusicService::class.java).apply {
            action = AutoPlayMusicService.ACTION_STOP_PLAYBACK
        })
    }

    private fun updateButtons() {
        song1Button?.applyBubbleStyle(
            slot = AutoPlayMusicService.AUDIO_SLOT_PRIMARY,
            isActive = (playbackUiState.isPlaybackActive || playbackUiState.isPlaybackTransitionPending) &&
                playbackUiState.activeSlot == AutoPlayMusicService.AUDIO_SLOT_PRIMARY
        )
        song2Button?.applyBubbleStyle(
            slot = AutoPlayMusicService.AUDIO_SLOT_SECONDARY,
            isActive = (playbackUiState.isPlaybackActive || playbackUiState.isPlaybackTransitionPending) &&
                playbackUiState.activeSlot == AutoPlayMusicService.AUDIO_SLOT_SECONDARY
        )
    }

    private fun ImageButton.applyBubbleStyle(slot: Int, isActive: Boolean) {
        val visual = resolveFloatingBubbleButtonVisual(slot, isActive)
        val iconRes = when (visual.icon) {
            FloatingBubbleButtonVisual.Icon.GREETING -> R.drawable.ic_greeting_round
            FloatingBubbleButtonVisual.Icon.GOODBYE -> R.drawable.ic_goodbye_round
            FloatingBubbleButtonVisual.Icon.STOP -> R.drawable.ic_stop_round
        }
        setImageResource(iconRes)
        val backgroundColor = when (visual.tone) {
            FloatingBubbleButtonVisual.Tone.COOL -> {
                if (isActive) {
                    ContextCompat.getColor(this@FloatingBubbleService, R.color.bubble_greeting_active)
                } else {
                    ContextCompat.getColor(this@FloatingBubbleService, R.color.bubble_greeting_idle)
                }
            }
            FloatingBubbleButtonVisual.Tone.WARM -> {
                if (isActive) {
                    ContextCompat.getColor(this@FloatingBubbleService, R.color.bubble_goodbye_active)
                } else {
                    ContextCompat.getColor(this@FloatingBubbleService, R.color.bubble_goodbye_idle)
                }
            }
        }
        imageTintList = ColorStateList.valueOf(
            ContextCompat.getColor(this@FloatingBubbleService, android.R.color.white)
        )
        backgroundTintList = ColorStateList.valueOf(backgroundColor)
        alpha = if (isActive) 1f else 0.92f
    }

    private fun createViewport(root: View): FloatingBubbleViewport {
        val metrics = resources.displayMetrics
        return FloatingBubbleViewport(
            width = metrics.widthPixels,
            height = metrics.heightPixels,
            bubbleWidth = root.measuredWidth.coerceAtLeast(dpToPx(68)),
            bubbleHeight = root.measuredHeight.coerceAtLeast(dpToPx(120)),
            edgeInset = dpToPx(16),
            topInset = dpToPx(24),
            bottomInset = dpToPx(40)
        )
    }

    private fun dismissTargetRect(viewport: FloatingBubbleViewport): FloatingBubbleRect {
        val diameter = dismissTargetLayoutParams?.width ?: dpToPx(88)
        return resolveFloatingBubbleDismissTargetRect(
            viewport = viewport,
            diameter = diameter,
            bottomMargin = dpToPx(72)
        )
    }

    private fun showDismissTarget(
        manager: WindowManager,
        viewport: FloatingBubbleViewport
    ) {
        if (dismissTargetView != null) return

        val rect = dismissTargetRect(viewport)
        val params = WindowManager.LayoutParams(
            rect.right - rect.left,
            rect.bottom - rect.top,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = rect.left
            y = rect.top
        }
        dismissTargetLayoutParams = params

        val appearance = resolveFloatingBubbleDismissAppearance(isHovering = false)
        val dismissView = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(appearance.backgroundColor)
                setStroke(dpToPx(2), appearance.strokeColor)
            }
            alpha = 0.94f
            resolveFloatingBubbleDismissCrossRotations().forEach { rotation ->
                addView(
                    View(this@FloatingBubbleService).apply {
                        setBackgroundColor(appearance.slashColor)
                        this.rotation = rotation
                    },
                    FrameLayout.LayoutParams(
                        dpToPx(30),
                        dpToPx(appearance.slashThicknessDp),
                        Gravity.CENTER
                    )
                )
            }
        }
        dismissTargetView = dismissView
        runCatching { manager.addView(dismissView, params) }
            .onFailure {
                dismissTargetView = null
                dismissTargetLayoutParams = null
            }
    }

    private fun updateDismissTargetHoverState(isHovering: Boolean) {
        val dismissView = dismissTargetView ?: return
        val appearance = resolveFloatingBubbleDismissAppearance(isHovering)
        val background = dismissView.background as? GradientDrawable ?: return
        dismissView.alpha = if (isHovering) 1f else 0.94f
        background.setColor(appearance.backgroundColor)
        background.setStroke(dpToPx(2), appearance.strokeColor)
    }

    private fun hideDismissTarget(manager: WindowManager) {
        dismissTargetView?.let { target ->
            runCatching { manager.removeView(target) }
        }
        dismissTargetView = null
        dismissTargetLayoutParams = null
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        scope.cancel()
        windowManager?.let(::hideDismissTarget)
        floatingView?.let { view ->
            windowManager?.removeView(view)
        }
        floatingView = null
        windowManager = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private companion object {
        const val CHANNEL_ID = "floating_bubble_controls"
        const val NOTIFICATION_ID = 1003
    }
}
