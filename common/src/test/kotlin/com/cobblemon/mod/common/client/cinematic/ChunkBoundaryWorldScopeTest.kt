package com.cobblemon.mod.common.client.cinematic

import com.cobblemon.mod.common.client.debug.ChunkBoundaryDebugRenderer
import com.cobblemon.mod.common.junit.BootstrapMinecraft
import com.cobblemon.mod.common.platform.events.ClientChunkEvent
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.chunk.LevelChunk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@BootstrapMinecraft
class ChunkBoundaryWorldScopeTest {
    @Test
    fun `dimension changes clear old chunks and late unloads cannot remove new chunks`() {
        mockkStatic(Minecraft::class)
        val client = mockk<Minecraft>()
        every { Minecraft.getInstance() } returns client
        try {
            val first = mockk<ClientLevel>()
            val second = mockk<ClientLevel>()
            val chunk = mockk<LevelChunk>()
            every { chunk.pos } returns ChunkPos(2, 3)
            client.level = first
            invoke("onChunkLoad", ClientChunkEvent.Load::class.java, ClientChunkEvent.Load(chunk, first))
            assertEquals(1, ChunkBoundaryDebugRenderer.chunkCount())
            client.level = second
            assertEquals(0, ChunkBoundaryDebugRenderer.chunkCount())
            invoke("onChunkLoad", ClientChunkEvent.Load::class.java, ClientChunkEvent.Load(chunk, second))
            invoke("onChunkUnload", ClientChunkEvent.Unload::class.java, ClientChunkEvent.Unload(chunk, first))
            assertEquals(1, ChunkBoundaryDebugRenderer.chunkCount())
        } finally {
            client.level = null
            ChunkBoundaryDebugRenderer.chunkCount()
            unmockkStatic(Minecraft::class)
        }
    }

    private fun invoke(name: String, type: Class<*>, event: ClientChunkEvent) {
        ChunkBoundaryDebugRenderer::class.java.getDeclaredMethod(name, type)
            .apply { isAccessible = true }.invoke(ChunkBoundaryDebugRenderer, event)
    }
}
