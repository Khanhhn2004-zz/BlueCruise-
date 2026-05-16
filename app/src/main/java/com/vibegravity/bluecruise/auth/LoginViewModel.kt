package com.vibegravity.bluecruise.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibegravity.bluecruise.di.IoDispatcher
import com.vibegravity.bluecruise.domain.auth.LoginResult
import com.vibegravity.bluecruise.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onPhoneChanged(phone: String) {
        _uiState.update {
            it.copy(
                phone = phone,
                phoneError = null,
                submitError = null
            )
        }
    }

    fun onPasswordChanged(password: String) {
        _uiState.update {
            it.copy(
                password = password,
                passwordError = null,
                submitError = null
            )
        }
    }

    fun submit() {
        val phone = _uiState.value.phone.trim()
        val password = _uiState.value.password

        when {
            phone.isBlank() -> {
                _uiState.update {
                    it.copy(
                        phoneError = PHONE_REQUIRED_MESSAGE,
                        passwordError = null,
                        submitError = null,
                        isLoading = false
                    )
                }
                return
            }

            password.isBlank() -> {
                _uiState.update {
                    it.copy(
                        phoneError = null,
                        passwordError = PASSWORD_REQUIRED_MESSAGE,
                        submitError = null,
                        isLoading = false
                    )
                }
                return
            }
        }

        _uiState.update {
            it.copy(
                phone = phone,
                phoneError = null,
                passwordError = null,
                submitError = null,
                isLoading = true,
                navigateToMain = false
            )
        }

        viewModelScope.launch(ioDispatcher) {
            when (val result = authRepository.login(phone = phone, password = password)) {
                is LoginResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            submitError = null,
                            navigateToMain = true
                        )
                    }
                }

                is LoginResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            submitError = result.message,
                            navigateToMain = false
                        )
                    }
                }
            }
        }
    }

    fun consumeNavigation() {
        _uiState.update { currentState ->
            if (currentState.navigateToMain) {
                currentState.copy(navigateToMain = false)
            } else {
                currentState
            }
        }
    }

    private companion object {
        const val PHONE_REQUIRED_MESSAGE = "Phone is required"
        const val PASSWORD_REQUIRED_MESSAGE = "Password is required"
    }
}
