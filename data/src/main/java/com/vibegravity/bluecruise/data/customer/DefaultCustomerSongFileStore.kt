package com.vibegravity.bluecruise.data.customer

import com.vibegravity.bluecruise.domain.customer.CustomerSongType
import java.io.File
import javax.inject.Inject

class DefaultCustomerSongFileStore @Inject constructor(
    private val baseDir: File
) : CustomerSongFileStore {

    override fun write(
        userId: String,
        type: CustomerSongType,
        bytes: ByteArray,
        fileNameHint: String?
    ): String {
        val songsDir = File(baseDir, CUSTOMER_SONGS_DIR).apply { mkdirs() }
        val extension = fileNameHint
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.ifBlank { DEFAULT_EXTENSION }
            ?: DEFAULT_EXTENSION
        val file = File(
            songsDir,
            "customer_${sanitize(userId)}_${type.wireValue}.$extension"
        )
        file.writeBytes(bytes)
        return file.toURI().toString()
    }

    override fun clearAll() {
        val songsDir = File(baseDir, CUSTOMER_SONGS_DIR)
        if (songsDir.exists()) {
            songsDir.deleteRecursively()
        }
    }

    private fun sanitize(value: String): String {
        return value.map { character ->
            if (character.isLetterOrDigit() || character == '-' || character == '_') {
                character
            } else {
                '_'
            }
        }.joinToString("")
    }

    private companion object {
        const val CUSTOMER_SONGS_DIR = "customer-songs"
        const val DEFAULT_EXTENSION = "mp3"
    }
}
