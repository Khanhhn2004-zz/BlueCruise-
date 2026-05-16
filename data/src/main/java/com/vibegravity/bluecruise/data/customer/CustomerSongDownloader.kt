package com.vibegravity.bluecruise.data.customer

import com.vibegravity.bluecruise.domain.customer.CustomerSongType

interface CustomerSongDownloader {
    suspend fun downloadSong(
        accessToken: String,
        userId: String,
        type: CustomerSongType
    ): CustomerSongDownloadResponse
}
