package com.cobblemon.mod.common.net.messages.editor

import com.cobblemon.mod.common.CobblemonNetwork
import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.junit.BootstrapMinecraft
import com.cobblemon.mod.common.net.messages.SerializedBinding
import com.cobblemon.mod.common.net.messages.client.editor.EditorPreviewAckS2CPacket
import com.cobblemon.mod.common.net.messages.server.editor.EditorPreviewStartC2SPacket
import com.cobblemon.mod.common.net.serverhandling.editor.EditorPreviewStartHandler
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

@BootstrapMinecraft
class EditorPreviewStartHandlerTest {
    private val server = mockk<MinecraftServer>(relaxed = true)
    private val player = mockk<ServerPlayer>(relaxed = true)
    private val packets = mutableListOf<NetworkPacket<*>>()
    private val scene = """{"version":2,"sceneId":"test:preview","startBeatId":"main","beats":{"main":{}}}"""

    @BeforeEach fun setup() {
        mockkObject(CobblemonNetwork)
        every { CobblemonNetwork.sendPacketToPlayer(any(), any()) } answers { packets.add(secondArg()); Unit }
        every { player.hasPermissions(2) } returns true
    }

    @AfterEach fun cleanup() = unmockkObject(CobblemonNetwork)

    private fun reject(packet: EditorPreviewStartC2SPacket): Component? {
        packets.clear()
        EditorPreviewStartHandler.handle(packet, server, player)
        val ack = packets.single() as EditorPreviewAckS2CPacket
        assertEquals(packet.previewId, ack.previewId)
        assertFalse(ack.ok)
        assertNull(ack.sceneId)
        return ack.errorMessage
    }

    @Test fun `invalid bedrock scales are rejected with a translated acknowledgement`() {
        for (scale in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            val packet = EditorPreviewStartC2SPacket(UUID.randomUUID(), scene, Vec3.ZERO,
                listOf(SerializedBinding.Bedrock("actor", "cobblemon:actor", emptyList(), scale)))
            assertEquals(Component.translatable("cobblemon.cinematic_editor.preview.invalid_scale", "actor"), reject(packet))
        }
    }

    @Test fun `permission and parser failures return translated messages`() {
        val packet = EditorPreviewStartC2SPacket(UUID.randomUUID(), "{", Vec3.ZERO, emptyList())
        every { player.hasPermissions(2) } returns false
        assertEquals(Component.translatable("cobblemon.cinematic_editor.preview.permission", 2), reject(packet))
        every { player.hasPermissions(2) } returns true
        assertEquals(Component.translatable("cobblemon.cinematic_editor.preview.parse_failed"), reject(packet))
    }

    @Test fun `scene validation retains a translated reason and its JSON path`() {
        val invalid = scene.replace("\"startBeatId\":\"main\"", "\"startBeatId\":\"missing\"")
        val packet = EditorPreviewStartC2SPacket(UUID.randomUUID(), invalid, Vec3.ZERO, emptyList())
        val detail = Component.empty().append(Component.translatable("cobblemon.cinematic_editor.validation.detail", "startBeatId",
            Component.translatable("cobblemon.cinematic_editor.validation.start_missing", "missing")))
        assertEquals(Component.translatable("cobblemon.cinematic_editor.preview.validation_failed", 1, 1, detail), reject(packet))
    }

    @Test fun `nonfinite origins and times cannot reach scene startup`() {
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            val origin = EditorPreviewStartC2SPacket(UUID.randomUUID(), scene, Vec3(value.toDouble(), 0.0, 0.0), emptyList())
            assertEquals(Component.translatable("cobblemon.cinematic_editor.preview.invalid_origin"), reject(origin))
            val time = EditorPreviewStartC2SPacket(UUID.randomUUID(), scene, Vec3.ZERO, emptyList(), time = value)
            assertEquals(Component.translatable("cobblemon.cinematic_editor.preview.invalid_time"), reject(time))
        }
    }
}
