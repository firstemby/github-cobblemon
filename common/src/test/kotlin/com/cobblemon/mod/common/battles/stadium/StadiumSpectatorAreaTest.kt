package com.cobblemon.mod.common.battles.stadium

import net.minecraft.network.chat.contents.TranslatableContents
import net.minecraft.network.chat.Component
import com.cobblemon.mod.common.net.messages.client.stadium.StadiumSpectateStatePacket
import com.cobblemon.mod.common.net.messages.server.stadium.StadiumSpectatePacket
import io.netty.buffer.Unpooled
import net.minecraft.core.RegistryAccess
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class StadiumSpectatorAreaTest {
    @Test fun `opposite stepped corners include seats and headroom in either order`() {
        val a = Vec3(-4.5, 64.0, -8.5)
        val b = Vec3(3.5, 69.0, -14.5)
        val area = StadiumSpectatorArea.fromCorners(a, b)
        assertEquals(area, StadiumSpectatorArea.fromCorners(b, a))
        assertTrue(area.valid(Vec3(0.0, 64.0, 0.0)))
        assertTrue(area.contains(a))
        assertTrue(area.contains(b.add(0.0, 1.8, 0.0)))
        assertFalse(area.contains(Vec3(0.0, 60.0, -10.0)))
        assertFalse(area.contains(Vec3(0.0, 73.0, -10.0)))
        assertFalse(area.contains(Vec3(4.0, 65.0, -10.0)))
    }

    @Test fun `invalid nonfinite inverted and distant zones are rejected`() {
        val area = StadiumSpectatorArea.fromCorners(Vec3.ZERO, Vec3(4.0, 3.0, 4.0))
        assertTrue(area.valid(Vec3.ZERO))
        assertFalse(area.copy(min = Vec3(Double.NaN, 0.0, 0.0)).valid(Vec3.ZERO))
        assertFalse(area.copy(max = Vec3(-1.0, 3.0, 4.0)).valid(Vec3.ZERO))
        assertFalse(area.copy(max = Vec3(2.0, 70.0, 4.0)).valid(Vec3.ZERO))
        assertFalse(area.valid(Vec3(100.0, 0.0, 100.0)))
    }

    @Test fun `stadium persistence keeps all bleacher zones and enforces limit`() {
        val data = StadiumBlockData.createDefault()
        val area = StadiumSpectatorArea.fromCorners(Vec3.ZERO, Vec3(4.0, 0.0, 4.0))
        assertTrue(StadiumBlockData.fromNbt(data.toNbt()).spectatorAreas.isEmpty())
        data.spectatorAreas.addAll(listOf(area, area.copy(min = Vec3(1.0, 0.0, 1.0))))
        assertEquals(data.spectatorAreas, StadiumBlockData.fromNbt(data.toNbt()).spectatorAreas)
        repeat(7) { data.spectatorAreas.add(area) }
        assertTrue(StadiumConfiguration.errors(data, Vec3.ZERO).any { (it.contents as TranslatableContents).key == "cobblemon.stadium.validation.bleachers" })
    }

    @Test fun `looking at any part of arena works but behind above and away do not`() {
        val arena = StadiumArena(Vec3.ZERO, 16.0, 12.0)
        val eye = Vec3(0.0, 5.0, -20.0)
        assertNotNull(StadiumSpectatorArea.aimPoint(arena, eye, Vec3(0.0, 0.0, 1.0)))
        assertNotNull(StadiumSpectatorArea.aimPoint(arena, eye, Vec3(6.0, -3.0, 20.0)))
        assertNull(StadiumSpectatorArea.aimPoint(arena, eye, Vec3(0.0, 0.0, -1.0)))
        assertNull(StadiumSpectatorArea.aimPoint(arena, eye, Vec3(1.0, 0.0, 0.0)))
        assertNull(StadiumSpectatorArea.aimPoint(arena, eye, Vec3(0.0, 1.0, 1.0)))
        assertNull(StadiumSpectatorArea.aimPoint(arena, Vec3(0.0, 2.0, 0.0), Vec3(0.0, 0.0, 1.0)))
    }

    @Test fun `join leave idle and watching packets round trip`() {
        val buffer = RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY)
        try {
            val id = UUID.randomUUID()
            val name = Component.translatable("cobblemon.stadium.default_name")
            for (state in listOf(StadiumSpectateStatePacket(null), StadiumSpectateStatePacket(id, Component.literal("Arena")), StadiumSpectateStatePacket(id, name, true))) {
                state.encode(buffer)
                assertEquals(state, StadiumSpectateStatePacket.decode(buffer))
            }
            for (leave in listOf(false, true)) {
                StadiumSpectatePacket(id, leave).encode(buffer)
                val decoded = StadiumSpectatePacket.decode(buffer)
                assertEquals(id, decoded.sessionId)
                assertEquals(leave, decoded.leave)
            }
            assertEquals(0, buffer.readableBytes())
        } finally { buffer.release() }
    }
}
