package com.cobblemon.mod.common.battles.stadium

import com.cobblemon.mod.common.net.messages.client.battle.StadiumBattleStatePacket
import com.cobblemon.mod.common.net.messages.client.battle.StadiumPreviewMember
import com.cobblemon.mod.common.net.messages.client.stadium.StadiumCinematicPacket
import com.cobblemon.mod.common.net.messages.client.stadium.StadiumConfigResultPacket
import com.cobblemon.mod.common.net.messages.server.battle.StadiumPreviewChoicePacket
import com.cobblemon.mod.common.net.messages.server.stadium.EditStadiumCinematicPacket
import com.cobblemon.mod.common.net.messages.server.stadium.UpdateStadiumBlockPacket
import io.netty.buffer.Unpooled
import net.minecraft.core.BlockPos
import net.minecraft.core.RegistryAccess
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.contents.TranslatableContents
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class StadiumFlowPacketTest {
    @Test fun `configuration results preserve translation keys and nested arguments`() {
        val buffer = RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY)
        try {
            val position = BlockPos(4, 70, -8)
            val reason = Component.translatable("cobblemon.stadium.team.level_cap", 50)
            val message = Component.translatable("cobblemon.command.stadium.team_error", 2, reason)
            val requestId = UUID.randomUUID()
            StadiumConfigResultPacket(position, requestId, false, message).encode(buffer)
            val result = StadiumConfigResultPacket.decode(buffer)
            assertEquals(position, result.blockPos)
            assertEquals(requestId, result.requestId)
            assertFalse(result.success)
            assertEquals(message, result.message)
            val content = result.message.contents as TranslatableContents
            assertEquals("cobblemon.command.stadium.team_error", content.key)
            assertEquals(reason, content.args[1])
            assertEquals(0, buffer.readableBytes())
        } finally { buffer.release() }
    }

    @Test fun `configuration save requests and results retain their request identity`() {
        val buffer = RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY)
        try {
            val requestId = UUID.randomUUID()
            val position = BlockPos(4, 70, -8)
            val request = UpdateStadiumBlockPacket(position, CompoundTag(), requestId = requestId)
            request.encode(buffer)
            assertEquals(requestId, UpdateStadiumBlockPacket.decode(buffer).requestId)
            StadiumConfigResultPacket(position, requestId, true, Component.literal("Saved")).encode(buffer)
            assertEquals(requestId, StadiumConfigResultPacket.decode(buffer).requestId)
            assertEquals(0, buffer.readableBytes())
        } finally { buffer.release() }
    }

    @Test fun `preview choices and public preview data round trip`() {
        val buffer = RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY)
        try {
            val id = UUID.randomUUID()
            val name = Component.translatable("cobblemon.species.bulbasaur.name")
            val member = StadiumPreviewMember("cobblemon:bulbasaur", setOf("shiny"), name, true)
            val state = StadiumBattleStatePacket(id, "preview", 12, 2, listOf(member.copy(name = Component.literal("Buddy"))),
                listOf(member), 30, 25, 300, Component.translatable("cobblemon.stadium.preview.invalid_order"))
            state.encode(buffer)
            assertEquals(state, StadiumBattleStatePacket.decode(buffer))
            StadiumPreviewChoicePacket(id, 12, listOf(2, 1)).encode(buffer)
            val choice = StadiumPreviewChoicePacket.decode(buffer)
            assertEquals(id, choice.battleId)
            assertEquals(12, choice.rqid)
            assertEquals(listOf(2, 1), choice.order)
            assertEquals(0, buffer.readableBytes())
        } finally { buffer.release() }
    }

    @Test fun `preview rejects excessive roster and aspect counts before reading their contents`() {
        val buffer = RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY)
        try {
            fun header() {
                buffer.clear()
                buffer.writeUUID(UUID.randomUUID())
                buffer.writeUtf("preview", 16)
                buffer.writeInt(1)
                buffer.writeByte(1)
            }
            header()
            buffer.writeByte(7)
            assertThrows(IllegalArgumentException::class.java) { StadiumBattleStatePacket.decode(buffer) }
            header()
            buffer.writeByte(1)
            buffer.writeUtf("cobblemon:bulbasaur")
            buffer.writeByte(65)
            assertThrows(IllegalArgumentException::class.java) { StadiumBattleStatePacket.decode(buffer) }
        } finally { buffer.release() }
    }

    @Test fun `scene exchange preserves identity revision origin and unicode JSON`() {
        val buffer = RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY)
        try {
            val id = UUID.randomUUID()
            val stadium = UUID.randomUUID()
            val source = "{\"sceneId\":\"example\",\"name\":\"Pokémon\"}"
            val pos = BlockPos(8, 60, -2)
            EditStadiumCinematicPacket(id, pos, stadium, "revision", source).encode(buffer)
            val request = EditStadiumCinematicPacket.decode(buffer)
            assertEquals(id, request.requestId)
            assertEquals(stadium, request.stadiumId)
            assertEquals(pos, request.blockPos)
            assertEquals(source, request.source)
            val message = Component.translatable("cobblemon.stadium.cinematic.applied", Component.translatable("cobblemon.stadium.default_name"))
            StadiumCinematicPacket(id, true, message, "revision", source, Vec3(8.0, 61.0, -2.0)).encode(buffer)
            val reply = StadiumCinematicPacket.decode(buffer)
            assertEquals(message, reply.message)
            assertEquals(source, reply.source)
            assertEquals("revision", reply.revision)
            assertEquals(Vec3(8.0, 61.0, -2.0), reply.origin)
            assertEquals(0, buffer.readableBytes())
        } finally { buffer.release() }
    }
}
