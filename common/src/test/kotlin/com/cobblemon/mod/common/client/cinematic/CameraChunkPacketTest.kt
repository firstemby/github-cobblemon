package com.cobblemon.mod.common.client.cinematic

import com.cobblemon.mod.common.client.camera.CameraClientChunkCacheExtension
import com.cobblemon.mod.common.junit.BootstrapMinecraft
import com.cobblemon.mod.common.mixin.client.CameraClientPacketListenerMixin
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import io.netty.buffer.Unpooled
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.chunk.LevelChunk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

@BootstrapMinecraft
class CameraChunkPacketTest {
    private val level = mockk<ClientLevel>(relaxed = true)
    private val chunk = mockk<LevelChunk>(relaxed = true)
    private val position = ChunkPos(20, -30)

    @BeforeEach
    fun setup() {
        mockkStatic(Minecraft::class)
        val client = mockk<Minecraft>()
        every { Minecraft.getInstance() } returns client
        every { client.isSameThread } returns true
        every { chunk.pos } returns position
        every { chunk.level } returns level
        CameraClientChunkCacheExtension.retain(chunk)
    }

    @AfterEach
    fun cleanup() {
        CameraClientChunkCacheExtension.take(level, position.x, position.z)
        unmockkStatic(Minecraft::class)
    }

    @Test
    fun `replacement packets update the retained instance and consume their payload once`() {
        val values = mutableListOf<Int>()
        every { chunk.replaceWithPacketData(any(), any(), any()) } answers {
            values.add(firstArg<FriendlyByteBuf>().readInt())
            Unit
        }
        val tags = Consumer<ClientboundLevelChunkPacketData.BlockEntityTagOutput> {}
        for (value in listOf(11, 29)) {
            val buffer = FriendlyByteBuf(Unpooled.buffer().writeInt(value))
            try {
                assertSame(chunk, CameraClientChunkCacheExtension.replaceWithPacketData(level,
                    position.x, position.z, buffer, CompoundTag(), tags))
                assertEquals(0, buffer.readableBytes())
            } finally {
                buffer.release()
            }
        }
        assertEquals(listOf(11, 29), values)
        verify(exactly = 2) { level.onChunkLoaded(position) }
        verify(exactly = 0) { level.unload(any()) }
    }

    @Test
    fun `forget packets preserve retained lighting only for the owning level`() {
        val listener = object : CameraClientPacketListenerMixin() {}
        val levelField = CameraClientPacketListenerMixin::class.java.getDeclaredField("level")
            .apply { isAccessible = true }
        val callback = CameraClientPacketListenerMixin::class.java.getDeclaredMethod("cobblemon\$keepCameraLight",
            ClientboundForgetLevelChunkPacket::class.java, CallbackInfo::class.java).apply { isAccessible = true }
        val packet = ClientboundForgetLevelChunkPacket(position)
        levelField.set(listener, level)
        val retained = CallbackInfo("queueLightRemoval", true)
        callback.invoke(listener, packet, retained)
        assertTrue(retained.isCancelled)
        levelField.set(listener, mockk<ClientLevel>())
        val otherLevel = CallbackInfo("queueLightRemoval", true)
        callback.invoke(listener, packet, otherLevel)
        assertFalse(otherLevel.isCancelled)
    }

    @Test
    fun `clear publishes empty storage to background readers and unloads retained chunks`() {
        CameraClientChunkCacheExtension.clear()
        assertNull(CompletableFuture.supplyAsync {
            CameraClientChunkCacheExtension.getChunk(position.x, position.z)
        }.get())
        verify(exactly = 1) { level.unload(chunk) }
        verify(exactly = 1) { level.queueLightUpdate(any()) }
        CameraClientChunkCacheExtension.clear()
        verify(exactly = 1) { level.unload(chunk) }
    }
}
