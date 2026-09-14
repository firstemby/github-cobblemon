package com.cobblemon.mod.common.client.cinematic

import com.cobblemon.mod.common.client.render.camera.CameraFeed
import com.cobblemon.mod.common.client.render.camera.CameraFeedHandler
import com.cobblemon.mod.common.client.render.camera.compositor.CompositorManager
import com.cobblemon.mod.common.junit.BootstrapMinecraft
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

@BootstrapMinecraft
class CameraFeedSelectionTest {
    private val activeId = UUID.randomUUID()
    private val inactiveId = UUID.randomUUID()
    private val otherDimensionId = UUID.randomUUID()
    private val active = mockk<CameraFeed>(relaxed = true)
    private val inactive = mockk<CameraFeed>(relaxed = true)
    private val otherDimension = mockk<CameraFeed>(relaxed = true)

    @BeforeEach
    fun setup() {
        mockkStatic(Minecraft::class, CompositorManager::class)
        val client = mockk<Minecraft>()
        val level = mockk<ClientLevel>()
        client.level = level
        every { level.dimension() } returns Level.OVERWORLD
        every { Minecraft.getInstance() } returns client
        every { CompositorManager.getRequiredFeeds() } returns setOf(activeId, otherDimensionId)
        every { CompositorManager.isFullscreenActive() } returns false
        val feeds = field<MutableMap<UUID, CameraFeed>>("feeds")
        feeds[activeId] = active
        feeds[inactiveId] = inactive
        feeds[otherDimensionId] = otherDimension
        val contexts = field<MutableMap<UUID, CameraFeedHandler.FeedContext>>("contexts")
        for (id in listOf(activeId, inactiveId, otherDimensionId)) {
            contexts[id] = CameraFeedHandler.FeedContext(if (id == otherDimensionId) Level.NETHER else Level.OVERWORLD,
                Vec3.ZERO, 0f, 0f, 0f, 70f, 128, 8)
        }
    }

    @AfterEach
    fun cleanup() {
        try {
            CameraFeedHandler.clear()
        } finally {
            unmockkStatic(Minecraft::class, CompositorManager::class)
        }
    }

    @Test
    fun `only required feeds in the current dimension consume capture slots`() {
        repeat(4) { assertEquals(mapOf(activeId to active), CameraFeedHandler.getFeedsToRender()) }
        every { CompositorManager.getRequiredFeeds() } returns emptySet()
        assertTrue(CameraFeedHandler.getFeedsToRender().isEmpty())
    }

    @Test
    fun `clear removes ownership and disposes remaining feeds after a disposal failure`() {
        every { active.dispose() } throws IllegalStateException("disposal failed")
        CameraFeedHandler.clear()
        assertFalse(CameraFeedHandler.hasFeeds())
        assertNull(CameraFeedHandler.getFeedContext(activeId))
        verify(exactly = 1) { inactive.dispose() }
        verify(exactly = 1) { otherDimension.dispose() }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> field(name: String): T = CameraFeedHandler::class.java.getDeclaredField(name)
        .apply { isAccessible = true }.get(null) as T
}
