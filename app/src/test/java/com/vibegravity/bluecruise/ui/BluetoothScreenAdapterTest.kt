package com.vibegravity.bluecruise.ui

import androidx.recyclerview.widget.RecyclerView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class BluetoothScreenAdapterTest {

    @Test
    fun `adapter starts with paired devices title row`() {
        val adapter = createAdapter()

        assertEquals(1, adapter.itemCount)
    }

    @Test
    fun `updateChrome uses diff updates instead of full invalidation`() {
        val adapter = createAdapter()
        val observer = RecordingObserver()
        adapter.registerAdapterDataObserver(observer)

        adapter.updateChrome(showTargetCar = true, showEmptyState = true)

        assertEquals(3, adapter.itemCount)
        assertEquals(0, observer.changedCalls)
        assertTrue(observer.insertedItemCount > 0 || observer.movedItemCount > 0)
    }

    @Test
    fun `target and empty rows dispatch scoped payload refreshes`() {
        val adapter = createAdapter()
        adapter.updateChrome(showTargetCar = true, showEmptyState = true)
        val observer = RecordingObserver()
        adapter.registerAdapterDataObserver(observer)

        adapter.notifyTargetCarChanged(BluetoothScreenAdapter.PAYLOAD_DEVICE_NAME)
        adapter.notifyEmptyStateContentChanged()

        assertEquals(
            listOf(
                BluetoothScreenAdapter.PAYLOAD_DEVICE_NAME,
                BluetoothScreenAdapter.PAYLOAD_EMPTY_STATE_CONTENT
            ),
            observer.changedPayloads
        )
        assertEquals(0, observer.changedCalls)
    }

    @Test
    fun `refresh helpers do nothing when target and empty rows are absent`() {
        val adapter = createAdapter()
        val observer = RecordingObserver()
        adapter.registerAdapterDataObserver(observer)

        adapter.notifyTargetCarChanged(BluetoothScreenAdapter.PAYLOAD_KEEP_ALIVE)
        adapter.notifyEmptyStateContentChanged()

        assertEquals(0, observer.changedCalls)
        assertTrue(observer.changedPayloads.isEmpty())
        assertEquals(0, observer.insertedItemCount)
        assertEquals(0, observer.movedItemCount)
    }

    @Test
    fun `updateChrome skips diff dispatch when chrome state is unchanged`() {
        val adapter = createAdapter()
        adapter.updateChrome(showTargetCar = true, showEmptyState = true)
        val observer = RecordingObserver()
        adapter.registerAdapterDataObserver(observer)

        adapter.updateChrome(showTargetCar = true, showEmptyState = true)

        assertEquals(0, observer.changedCalls)
        assertTrue(observer.changedPayloads.isEmpty())
        assertEquals(0, observer.insertedItemCount)
        assertEquals(0, observer.movedItemCount)
        assertFalse(adapter.showBatteryBanner)
        assertTrue(adapter.showTargetCar)
        assertTrue(adapter.showEmptyState)
    }

    @Test
    fun `snapshot round trip preserves secondary audio and floating bubble state`() {
        val adapter = createAdapter().apply {
            audioFilePath = "content://tone-1"
            audioFilePath2 = "content://tone-2"
            connectionStartDelaySeconds = 4
            isFloatingBubbleEnabled = true
        }

        val snapshot = adapter.toSnapshot()
        val restored = createAdapter()
        restored.applySnapshot(snapshot)

        assertEquals("content://tone-2", snapshot.audioFilePath2)
        assertEquals(4, snapshot.connectionStartDelaySeconds)
        assertTrue(snapshot.isFloatingBubbleEnabled)
        assertEquals("content://tone-2", restored.audioFilePath2)
        assertEquals(4, restored.connectionStartDelaySeconds)
        assertTrue(restored.isFloatingBubbleEnabled)
    }

    private fun createAdapter(): BluetoothScreenAdapter {
        return BluetoothScreenAdapter(
            onIgnoreBatteryOptClicked = {},
            onOpenAutoStartSettingsClicked = {},
            onAutoPlayToggled = {},
            onEmptyActionClicked = {}
        )
    }

    private class RecordingObserver : RecyclerView.AdapterDataObserver() {
        var changedCalls: Int = 0
        var insertedItemCount: Int = 0
        var movedItemCount: Int = 0
        val changedPayloads = mutableListOf<Any?>()

        override fun onChanged() {
            changedCalls += 1
        }

        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
            insertedItemCount += itemCount
        }

        override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
            movedItemCount += itemCount
        }

        override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) {
            repeat(itemCount) {
                changedPayloads += payload
            }
        }
    }
}
