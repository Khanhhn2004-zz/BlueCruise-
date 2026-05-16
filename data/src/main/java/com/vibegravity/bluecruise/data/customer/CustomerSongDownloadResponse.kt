package com.vibegravity.bluecruise.data.customer

data class CustomerSongDownloadResponse(
    val bytes: ByteArray,
    val fileName: String?,
    val contentType: String?
)
