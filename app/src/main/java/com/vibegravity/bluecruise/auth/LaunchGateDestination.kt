package com.vibegravity.bluecruise.auth

sealed interface LaunchGateDestination {
    data object Login : LaunchGateDestination

    data object Main : LaunchGateDestination
}
