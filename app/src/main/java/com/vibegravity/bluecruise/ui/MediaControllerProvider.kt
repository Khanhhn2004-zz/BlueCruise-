package com.vibegravity.bluecruise.ui

import androidx.media3.session.MediaController

/**
 * Provides a MediaController for playback control. Optional in tests (null) so ViewModel can be unit tested.
 */
interface MediaControllerProvider {
    fun initialize(callback: (MediaController?) -> Unit)
}
