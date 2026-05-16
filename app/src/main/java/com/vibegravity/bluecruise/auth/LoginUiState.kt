package com.vibegravity.bluecruise.auth

data class LoginUiState(
    val phone: String = "",
    val password: String = "",
    val phoneError: String? = null,
    val passwordError: String? = null,
    val submitError: String? = null,
    val isLoading: Boolean = false,
    val navigateToMain: Boolean = false
)
