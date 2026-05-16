package com.vibegravity.bluecruise.data.customer

import com.vibegravity.bluecruise.domain.customer.CustomerSongType
import java.io.File
import java.net.URI
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CustomerSongFileStoreTest {

    @get:Rule
    val tmpFolder: TemporaryFolder = TemporaryFolder.builder().assureDeletion().build()

    @Test
    fun `write stores bytes under customer songs directory and returns file uri`() {
        val fileStore = DefaultCustomerSongFileStore(tmpFolder.root)

        val uri = fileStore.write(
            userId = "user-456",
            type = CustomerSongType.GOODBYE,
            bytes = byteArrayOf(4, 5, 6),
            fileNameHint = "goodbye.mp3"
        )

        val file = File(URI(uri))
        assertTrue(file.exists())
        assertTrue(file.parentFile?.name == "customer-songs")
        assertTrue(file.name.contains("user-456"))
        assertTrue(file.name.endsWith(".mp3"))
        assertArrayEquals(byteArrayOf(4, 5, 6), file.readBytes())
    }

    @Test
    fun `clearAll removes only cached customer songs directory`() {
        val fileStore = DefaultCustomerSongFileStore(tmpFolder.root)
        val uri = fileStore.write(
            userId = "user-456",
            type = CustomerSongType.HELLO,
            bytes = byteArrayOf(1, 2, 3),
            fileNameHint = "hello.mp3"
        )
        val cachedFile = File(URI(uri))
        val unrelatedFile = File(tmpFolder.root, "manual-song.mp3").apply {
            writeBytes(byteArrayOf(9, 9, 9))
        }

        fileStore.clearAll()

        assertFalse(cachedFile.exists())
        assertFalse(File(tmpFolder.root, "customer-songs").exists())
        assertTrue(unrelatedFile.exists())
        assertArrayEquals(byteArrayOf(9, 9, 9), unrelatedFile.readBytes())
    }
}
