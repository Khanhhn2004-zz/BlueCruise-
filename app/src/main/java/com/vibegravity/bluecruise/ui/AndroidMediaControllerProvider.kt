package com.vibegravity.bluecruise.ui

import android.content.ComponentName
import android.content.Context
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.vibegravity.bluecruise.service.AutoPlayMusicService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import timber.log.Timber

class AndroidMediaControllerProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : MediaControllerProvider {

    override fun initialize(callback: (MediaController?) -> Unit) {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, AutoPlayMusicService::class.java)
        )
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture.addListener(
            {
                val controller = try {
                    controllerFuture.get()
                } catch (exception: Exception) {
                    if (exception is InterruptedException) {
                        Thread.currentThread().interrupt()
                    }
                    Timber.w(exception, "Failed to initialize MediaController")
                    null
                }
                callback(controller)
            },
            com.google.common.util.concurrent.MoreExecutors.directExecutor()
        )
    }
}
