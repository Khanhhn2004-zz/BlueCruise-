package com.vibegravity.bluecruise.data.customer

import com.vibegravity.bluecruise.domain.customer.CustomerSongType

interface CustomerSongFileStore {
    fun write(
        userId: String,
        type: CustomerSongType,
        bytes: ByteArray,
        fileNameHint: String?
    ): String

    fun clearAll()
}
