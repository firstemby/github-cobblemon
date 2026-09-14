package com.cobblemon.mod.common.client.cinematic

import com.cobblemon.mod.common.client.render.camera.CameraFeed
import com.cobblemon.mod.common.client.render.camera.CameraViewAreaExtension
import com.cobblemon.mod.common.client.render.camera.RenderTargetManager
import com.cobblemon.mod.common.junit.BootstrapMinecraft
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.CompiledSection
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection
import net.minecraft.client.renderer.culling.Frustum
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

@BootstrapMinecraft
class CameraFeedVisibilityTest {
    private val level = mockk<Level>()
    private val sections = mutableMapOf<BlockPos, RenderSection>()
    private var revision = 1L
    private lateinit var feed: CameraFeed

    @BeforeEach
    fun setup() {
        mockkStatic(RenderTargetManager::class, CameraViewAreaExtension::class)
        every { RenderTargetManager.getOrCreate(any(), any()) } returns mockk(relaxed = true)
        every { RenderTargetManager.remove(any()) } answers {}
        every { level.minSection } returns -4
        every { level.maxSection } returns 20
        every { CameraViewAreaExtension.revision() } answers { revision }
        every { CameraViewAreaExtension.graphRevision() } returns 1L
        every { CameraViewAreaExtension.retainSections(any(), any()) } answers {}
        every { CameraViewAreaExtension.releaseSections(any()) } answers {}
        every { CameraViewAreaExtension.rawFetch(any(), any(), any(), true) } answers {
            val origin = BlockPos(firstArg<Int>() * 16, secondArg<Int>() * 16, thirdArg<Int>() * 16)
            sections.getOrPut(origin) { section(origin) }
        }
        feed = CameraFeed(UUID.randomUUID(), Vec3.ZERO, 128, 8)
    }

    @AfterEach
    fun cleanup() {
        feed.dispose()
        unmockkStatic(RenderTargetManager::class, CameraViewAreaExtension::class)
    }

    @Test
    fun `moving into a new section replaces the discovery seed and prunes old visible sections`() {
        feed.refreshViewArea(level, 8)
        val old = visible().single()
        feed.updateContext(Vec3(1600.0, 0.0, 0.0), 128, 8)
        feed.refreshViewArea(level, 8)
        val current = visible().single()
        assertNotSame(old, current)
        assertEquals(BlockPos(1600, 0, 0), current.origin)
    }

    @Test
    fun `released section references are rejected before a reload and recreated on refresh`() {
        feed.refreshViewArea(level, 8)
        val released = visible().single()
        sections.clear()
        revision++
        val applied = mutableListOf<RenderSection>()
        feed.applyVisibleSections(applied)
        assertTrue(applied.isEmpty())
        feed.refreshViewArea(level, 8)
        assertNotSame(released, visible().single())
    }

    @Test
    fun `each current frustum can remove previously visible sections without moving chunks`() {
        feed.refreshViewArea(level, 8)
        assertEquals(1, visible().size)
        feed.updateContext(Vec3.ZERO, 128, 8)
        val frustum = mockk<Frustum>()
        every { frustum.isVisible(any<AABB>()) } returns false
        feed.updateVisibleSections(frustum)
        val applied = mutableListOf<RenderSection>()
        feed.applyVisibleSections(applied)
        assertTrue(applied.isEmpty())
    }

    private fun visible(): List<RenderSection> {
        val frustum = mockk<Frustum>()
        every { frustum.isVisible(any<AABB>()) } returns true
        feed.updateVisibleSections(frustum)
        return mutableListOf<RenderSection>().also { feed.applyVisibleSections(it) }
    }

    private fun section(origin: BlockPos): RenderSection = mockk<RenderSection>(relaxed = true).also {
        every { it.origin } returns origin
        RenderSection::class.java.getDeclaredField("compiled").apply { isAccessible = true }
            .set(it, AtomicReference(CompiledSection.UNCOMPILED))
        every { it.boundingBox } returns AABB(Vec3.atLowerCornerOf(origin), Vec3.atLowerCornerOf(origin.offset(16, 16, 16)))
    }
}
