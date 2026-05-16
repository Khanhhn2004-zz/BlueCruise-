package com.vibegravity.bluecruise.domain.auth

enum class LoginError {
    EMPTY_PHONE,
    EMPTY_PASSWORD,
    INVALID_CREDENTIALS,
    NETWORK,
    SERVER,
    UNKNOWN
}
