package com.vibegravity.bluecruise.ui

import com.vibegravity.bluecruise.domain.BluetoothDeviceDomain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BluetoothScreenRenderPlanTest {

    @Test
    fun `unchanged ui state produces no scoped follow-up work`() {
        val previous = BluetoothScreenSnapshot(
            isBluetoothEnabled = true,
            showTargetCar = true,
            showEmptyState = false,
            targetDeviceName = "Blue Car",
            isAutoPlayEnabled = true,
            connectionStartDelaySeconds = 0,
            isKeepAliveEnabled = false,
            audioFilePath = "content://tone",
            audioFilePath2 = "content://tone-2",
            isFloatingBubbleEnabled = true,
            isPlaying = false,
            isPlaybackPending = false,
            routingTier = 2
        )

        val plan = buildBluetoothScreenRenderPlan(
            previous = previous,
            state = state(
                pairedDevices = listOf(device("Blue Car", TARGET_MAC)),
                targetMacAddress = TARGET_MAC,
                audioFilePath = "content://tone",
                audioFilePath2 = "content://tone-2",
                floatingBubbleEnabled = true,
                routingTier = 2
            )
        )

        assertFalse(plan.shouldSyncKeepAliveService)
        assertFalse(plan.shouldSyncFloatingBubbleService)
        assertTrue(plan.targetCarPayloads.isEmpty())
        assertFalse(plan.shouldRefreshEmptyStateContent)
        assertEquals(previous, plan.nextSnapshot)
    }

    @Test
    fun `secondary audio and floating bubble changes emit scoped payloads`() {
        val plan = buildBluetoothScreenRenderPlan(
            previous = BluetoothScreenSnapshot(
                showTargetCar = true,
                targetDeviceName = "Blue Car",
                audioFilePath2 = null,
                isFloatingBubbleEnabled = false
            ),
            state = state(
                pairedDevices = listOf(device("Blue Car", TARGET_MAC)),
                targetMacAddress = TARGET_MAC,
                audioFilePath2 = "content://tone-2",
                floatingBubbleEnabled = true
            )
        )

        assertTrue(plan.targetCarPayloads.contains(BluetoothScreenAdapter.PAYLOAD_AUDIO_PATH_2))
        assertTrue(plan.targetCarPayloads.contains(BluetoothScreenAdapter.PAYLOAD_FLOATING_BUBBLE))
        assertTrue(plan.shouldSyncFloatingBubbleService)
        assertEquals("content://tone-2", plan.nextSnapshot.audioFilePath2)
        assertTrue(plan.nextSnapshot.isFloatingBubbleEnabled)
    }

    @Test
    fun `connection start delay change emits scoped payload`() {
        val plan = buildBluetoothScreenRenderPlan(
            previous = BluetoothScreenSnapshot(
                showTargetCar = true,
                targetDeviceName = "Blue Car",
                connectionStartDelaySeconds = 0
            ),
            state = state(
                pairedDevices = listOf(device("Blue Car", TARGET_MAC)),
                targetMacAddress = TARGET_MAC,
                connectionStartDelaySeconds = 3
            )
        )

        assertTrue(
            plan.targetCarPayloads.contains(BluetoothScreenAdapter.PAYLOAD_CONNECTION_START_DELAY)
        )
        assertEquals(3, plan.nextSnapshot.connectionStartDelaySeconds)
    }

    @Test
    fun `keep alive transition requests one service sync and scoped payload`() {
        val plan = buildBluetoothScreenRenderPlan(
            previous = BluetoothScreenSnapshot(
                showTargetCar = true,
                targetDeviceName = "Blue Car",
                isKeepAliveEnabled = false
            ),
            state = state(
                pairedDevices = listOf(device("Blue Car", TARGET_MAC)),
                targetMacAddress = TARGET_MAC,
                keepAppAliveEnabled = true
            )
        )

        assertTrue(plan.shouldSyncKeepAliveService)
        assertTrue(plan.targetCarPayloads.contains(BluetoothScreenAdapter.PAYLOAD_KEEP_ALIVE))
    }

    @Test
    fun `empty state content refresh only happens while empty row stays visible`() {
        val plan = buildBluetoothScreenRenderPlan(
            previous = BluetoothScreenSnapshot(
                isBluetoothEnabled = true,
                showEmptyState = true
            ),
            state = state(
                isBluetoothEnabled = false,
                pairedDevices = emptyList()
            )
        )

        assertTrue(plan.nextSnapshot.showEmptyState)
        assertTrue(plan.shouldRefreshEmptyStateContent)
    }

    @Test
    fun `empty state content refresh is skipped once empty row is removed`() {
        val plan = buildBluetoothScreenRenderPlan(
            previous = BluetoothScreenSnapshot(
                isBluetoothEnabled = false,
                showEmptyState = true
            ),
            state = state(
                isBluetoothEnabled = true,
                pairedDevices = listOf(device("Blue Car", TARGET_MAC)),
                targetMacAddress = TARGET_MAC
            )
        )

        assertFalse(plan.nextSnapshot.showEmptyState)
        assertFalse(plan.shouldRefreshEmptyStateContent)
    }

    @Test
    fun `device name payload is only emitted when target card remains visible`() {
        val renamedTargetPlan = buildBluetoothScreenRenderPlan(
            previous = BluetoothScreenSnapshot(
                showTargetCar = true,
                targetDeviceName = "Blue Car"
            ),
            state = state(
                pairedDevices = listOf(device("Blue Car Prime", TARGET_MAC)),
                targetMacAddress = TARGET_MAC
            )
        )

        val newlyShownTargetPlan = buildBluetoothScreenRenderPlan(
            previous = BluetoothScreenSnapshot(
                showTargetCar = false,
                targetDeviceName = ""
            ),
            state = state(
                pairedDevices = listOf(device("Blue Car Prime", TARGET_MAC)),
                targetMacAddress = TARGET_MAC
            )
        )

        assertEquals(
            listOf(BluetoothScreenAdapter.PAYLOAD_DEVICE_NAME),
            renamedTargetPlan.targetCarPayloads
        )
        assertFalse(
            newlyShownTargetPlan.targetCarPayloads.contains(BluetoothScreenAdapter.PAYLOAD_DEVICE_NAME)
        )
        assertTrue(newlyShownTargetPlan.nextSnapshot.showTargetCar)
    }

    @Test
    fun `aftermarket android auto payload is emitted when toggle state changes`() {
        val plan = buildBluetoothScreenRenderPlan(
            previous = BluetoothScreenSnapshot(
                showTargetCar = true,
                targetDeviceName = "Blue Car",
                isAutoPlayOnAndroidAutoEnabled = true,
                isAftermarketAndroidAutoTargetEnabled = false
            ),
            state = state(
                pairedDevices = listOf(device("Blue Car", TARGET_MAC)),
                targetMacAddress = TARGET_MAC,
                autoPlayOnAndroidAutoEnabled = true,
                isAftermarketAndroidAutoTargetEnabled = true
            )
        )

        assertTrue(
            plan.targetCarPayloads.contains(
                BluetoothScreenAdapter.PAYLOAD_AFTERMARKET_ANDROID_AUTO_TARGET
            )
        )
        assertTrue(plan.nextSnapshot.isAftermarketAndroidAutoTargetEnabled)
    }

    private fun state(
        isBluetoothEnabled: Boolean = true,
        pairedDevices: List<BluetoothDeviceDomain> = emptyList(),
        targetMacAddress: String? = null,
        autoPlayEnabled: Boolean = true,
        connectionStartDelaySeconds: Int = 0,
        keepAppAliveEnabled: Boolean = false,
        autoPlayOnAndroidAutoEnabled: Boolean = false,
        isAftermarketAndroidAutoTargetEnabled: Boolean = false,
        audioFilePath: String? = null,
        audioFilePath2: String? = null,
        floatingBubbleEnabled: Boolean = false,
        routingTier: Int = 1,
        isPlaying: Boolean = false,
        isPlaybackPending: Boolean = false
    ): BluetoothUiState {
        return BluetoothUiState(
            isBluetoothEnabled = isBluetoothEnabled,
            pairedDevices = pairedDevices,
            targetMacAddress = targetMacAddress,
            autoPlayEnabled = autoPlayEnabled,
            connectionStartDelaySeconds = connectionStartDelaySeconds,
            keepAppAliveEnabled = keepAppAliveEnabled,
            autoPlayOnAndroidAutoEnabled = autoPlayOnAndroidAutoEnabled,
            isAftermarketAndroidAutoTargetEnabled = isAftermarketAndroidAutoTargetEnabled,
            audioFilePath = audioFilePath,
            audioFilePath2 = audioFilePath2,
            floatingBubbleEnabled = floatingBubbleEnabled,
            routingTier = routingTier,
            isPlaying = isPlaying,
            isPlaybackPending = isPlaybackPending
        )
    }

    private fun device(name: String, macAddress: String): BluetoothDeviceDomain {
        return BluetoothDeviceDomain(name = name, macAddress = macAddress)
    }

    private companion object {
        const val TARGET_MAC = "AA:BB:CC:DD:EE:FF"
    }
}
