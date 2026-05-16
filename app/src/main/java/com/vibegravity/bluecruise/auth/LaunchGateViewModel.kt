package com.vibegravity.bluecruise.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibegravity.bluecruise.di.IoDispatcher
import com.vibegravity.bluecruise.domain.repository.AuthSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltViewModel
class LaunchGateViewModel @Inject constructor(
    private val authSessionRepository: AuthSessionRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _destination = MutableStateFlow<LaunchGateDestination?>(null)
    val destination: StateFlow<LaunchGateDestination?> = _destination.asStateFlow()

    init {
        viewModelScope.launch(ioDispatcher) {
            runCatching {
                authSessionRepository.sessionFlow.first()
            }.onSuccess { session ->
                _destination.value = if (session.isLoggedIn) {
                    LaunchGateDestination.Main
                } else {
                    LaunchGateDestination.Login
                }
            }.onFailure {
                authSessionRepository.clearSession()
                _destination.value = LaunchGateDestination.Login
            }
        }
    }
}
