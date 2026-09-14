package com.cobblemon.mod.common.client.cinematic

import com.cobblemon.mod.common.client.camera.CameraClientChunkCacheExtension
import com.cobblemon.mod.common.junit.BootstrapMinecraft
import com.cobblemon.mod.common.mixin.accessor.CameraChunkStorageAccessor
import com.cobblemon.mod.common.mixin.client.ClientChunkCacheMixin
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.chunk.LevelChunk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReferenceArray

@BootstrapMinecraft
class CameraChunkOwnershipTest {
    private val level = mockk<ClientLevel>(relaxed = true)
    private val cache = object : ClientChunkCacheMixin() {}
    private val storage = Storage()

    @BeforeEach
    fun setup() {
        mockkStatic(CameraClientChunkCacheExtension::class)
        every { CameraClientChunkCacheExtension.shouldKeepChunk(any()) } returns true
        ClientChunkCacheMixin::class.java.getDeclaredField("level").apply { isAccessible = true }.set(cache, level)
    }

    @AfterEach
    fun cleanup() {
        CameraClientChunkCacheExtension.snapshot().forEach {
            CameraClientChunkCacheExtension.take(level, it.pos.x, it.pos.z)
        }
        unmockkStatic(CameraClientChunkCacheExtension::class)
    }

    @Test
    fun `view movement transfers the same chunk without unloading its block entities or light`() {
        val chunk = chunk(0, 0)
        storage.chunks.set(0, chunk)
        storage.count = 1
        retainOutside(20, 0, 5)
        assertNull(storage.chunks.get(0))
        assertEquals(0, storage.count)
        assertSame(chunk, CameraClientChunkCacheExtension.getChunk(0, 0))
        assertSame(chunk, CompletableFuture.supplyAsync { CameraClientChunkCacheExtension.getChunk(0, 0) }.get())
        verify(exactly = 0) { level.unload(any()) }
    }

    @Test
    fun `entering player range promotes a camera chunk without waiting for a new packet`() {
        val chunk = chunk(0, 0)
        CameraClientChunkCacheExtension.retain(chunk)
        promote()
        assertSame(chunk, storage.chunks.get(0))
        assertEquals(1, storage.count)
        assertNull(CameraClientChunkCacheExtension.getChunk(0, 0))
        verify(exactly = 0) { level.unload(any()) }
        verify(exactly = 0) { level.onChunkLoaded(any()) }
    }

    @Test
    fun `promotion preserves a camera-owned ring collision and keeps the player count accurate`() {
        val old = chunk(11, 0)
        val incoming = chunk(0, 0)
        storage.chunks.set(0, old)
        storage.count = 1
        CameraClientChunkCacheExtension.retain(incoming)
        promote()
        assertSame(incoming, storage.chunks.get(0))
        assertSame(old, CameraClientChunkCacheExtension.getChunk(11, 0))
        assertNull(CameraClientChunkCacheExtension.getChunk(0, 0))
        assertEquals(1, storage.count)
        verify(exactly = 0) { level.unload(any()) }
    }

    @Test
    fun `ordinary chunks and chunks still in player range keep native ownership`() {
        val ordinary = chunk(0, 0)
        storage.chunks.set(0, ordinary)
        storage.count = 1
        every { CameraClientChunkCacheExtension.shouldKeepChunk(any()) } returns false
        retainOutside(20, 0, 5)
        assertSame(ordinary, storage.chunks.get(0))
        every { CameraClientChunkCacheExtension.shouldKeepChunk(any()) } returns true
        retainOutside(0, 0, 5)
        assertSame(ordinary, storage.chunks.get(0))
        assertEquals(1, storage.count)
        assertTrue(CameraClientChunkCacheExtension.snapshot().isEmpty())
    }

    @Test
    fun `a different level cannot take a retained chunk`() {
        val chunk = chunk(0, 0)
        CameraClientChunkCacheExtension.retain(chunk)
        assertNull(CameraClientChunkCacheExtension.take(mockk(), 0, 0))
        assertSame(chunk, CameraClientChunkCacheExtension.take(level, 0, 0))
    }

    private fun chunk(x: Int, z: Int): LevelChunk = mockk<LevelChunk>(relaxed = true).also {
        every { it.pos } returns ChunkPos(x, z)
        every { it.level } returns level
    }

    private fun retainOutside(x: Int, z: Int, radius: Int) {
        ClientChunkCacheMixin::class.java.getDeclaredMethod("cobblemon\$retainOutsideRange",
            CameraChunkStorageAccessor::class.java, Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            .apply { isAccessible = true }.invoke(cache, storage, x, z, radius)
    }

    private fun promote() {
        ClientChunkCacheMixin::class.java.getDeclaredMethod("cobblemon\$promoteCameraChunks", CameraChunkStorageAccessor::class.java)
            .apply { isAccessible = true }.invoke(cache, storage)
    }

    private class Storage : CameraChunkStorageAccessor {
        val chunks = AtomicReferenceArray<LevelChunk>(121)
        var count = 0
        override fun `cobblemon$getChunks`() = chunks
        override fun `cobblemon$getChunkRadius`() = 5
        override fun `cobblemon$getChunkCount`() = count
        override fun `cobblemon$setChunkCount`(count: Int) { this.count = count }
        override fun `cobblemon$getViewCenterX`() = 0
        override fun `cobblemon$getViewCenterZ`() = 0
        override fun `cobblemon$getIndex`(x: Int, z: Int) = Math.floorMod(z, 11) * 11 + Math.floorMod(x, 11)
    }
}
