package com.cobblemon.mod.common.client.cinematic

import com.cobblemon.mod.common.client.render.camera.CameraViewAreaExtension
import com.cobblemon.mod.common.junit.BootstrapMinecraft
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.world.level.Level
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

@BootstrapMinecraft
class CameraViewAreaOwnershipTest {
    @BeforeEach
    fun setup() {
        val level = mockk<Level>()
        every { level.minSection } returns -4
        every { level.maxSection } returns 20
        CameraViewAreaExtension.allChanged(mockk<SectionRenderDispatcher>(), level)
    }

    @AfterEach
    fun cleanup() {
        CameraViewAreaExtension.clear()
    }

    @Test
    fun `shared sections stay alive until the last feed releases them`() {
        val section = section(0)
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        CameraViewAreaExtension.retainSections(first, listOf(section))
        CameraViewAreaExtension.retainSections(second, listOf(section))
        CameraViewAreaExtension.releaseSections(first)
        assertSame(section, CameraViewAreaExtension.rawFetch(0, 0, 0, false))
        verify(exactly = 0) { section.releaseBuffers() }
        CameraViewAreaExtension.releaseSections(second)
        assertNull(CameraViewAreaExtension.rawFetch(0, 0, 0, false))
        verify(exactly = 1) { section.releaseBuffers() }
    }

    @Test
    fun `unload removes the section and invalidates previously acquired feed references`() {
        val section = section(0)
        val revision = CameraViewAreaExtension.revision()
        CameraViewAreaExtension.onChunkUnload(0, 0)
        assertTrue(CameraViewAreaExtension.revision() > revision)
        assertNull(CameraViewAreaExtension.rawFetch(0, 0, 0, false))
        verify(exactly = 1) { section.releaseBuffers() }
    }

    @Test
    fun `clear releases all sections after a buffer failure and detaches the dispatcher`() {
        val first = section(0)
        val second = section(1)
        every { first.releaseBuffers() } throws IllegalStateException("buffer failure")
        assertThrows(IllegalStateException::class.java) { CameraViewAreaExtension.clear() }
        verify(exactly = 1) { first.releaseBuffers() }
        verify(exactly = 1) { second.releaseBuffers() }
        assertNull(CameraViewAreaExtension.rawFetch(0, 0, 0, true))
    }

    @Test
    fun `camera compilation updates its graph and remains isolated after release`() {
        val cameraSection = section(0)
        val revision = CameraViewAreaExtension.graphRevision()
        assertTrue(CameraViewAreaExtension.onSectionCompiled(cameraSection))
        assertTrue(CameraViewAreaExtension.graphRevision() > revision)
        assertFalse(CameraViewAreaExtension.onSectionCompiled(mockk()))
        CameraViewAreaExtension.clear()
        assertTrue(CameraViewAreaExtension.onSectionCompiled(cameraSection))
    }

    @Suppress("UNCHECKED_CAST")
    private fun section(x: Int): RenderSection = mockk<RenderSection>(relaxed = true).also {
        every { it.origin } returns BlockPos(x * 16, 0, 0)
        val sections = CameraViewAreaExtension::class.java.getDeclaredField("SECTIONS")
            .apply { isAccessible = true }.get(null) as Long2ObjectOpenHashMap<RenderSection>
        sections.put(SectionPos.asLong(x, 0, 0), it)
        val cameraSections = CameraViewAreaExtension::class.java.getDeclaredField("CAMERA_SECTIONS")
            .apply { isAccessible = true }.get(null) as MutableSet<RenderSection>
        cameraSections.add(it)
    }
}
