package com.vibegravity.bluecruise.domain.customer

sealed interface SlotSyncResult {
    val cacheUri: String?
    val activeUri: String?

    data class UpdatedActive(
        override val cacheUri: String,
        override val activeUri: String
    ) : SlotSyncResult

    data class UpdatedCacheOnly(
        override val cacheUri: String,
        override val activeUri: String?
    ) : SlotSyncResult

    data class PreservedExisting(
        override val activeUri: String?
    ) : SlotSyncResult {
        override val cacheUri: String? = null
    }

    data class Failed(
        val message: String,
        override val activeUri: String?
    ) : SlotSyncResult {
        override val cacheUri: String? = null
    }
}

data class CustomerSongSyncOutcome(
    val trigger: CustomerSongSyncTrigger,
    val hello: SlotSyncResult,
    val goodbye: SlotSyncResult
)
