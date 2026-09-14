/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.runtime

import com.cobblemon.mod.common.junit.BootstrapMinecraft
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket
import net.minecraft.network.protocol.game.VecDeltaCodec
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.ServerGamePacketListenerImpl
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@BootstrapMinecraft
class PreviewActorTest {
    @Test
    fun `relative packets match Minecraft rounding across zero and short limits`() {
        val fixture = Fixture()
        val viewer = Viewer()
        fixture.players.add(viewer.player)
        fixture.position = Vec3(0.6 / 4096, 64.0, -0.6 / 4096)
        fixture.actor.syncTransform()
        val client = VecDeltaCodec().apply { setBase(fixture.position) }
        for (position in listOf(Vec3(1.4 / 4096, 64.0, -1.4 / 4096), Vec3(-1.6 / 4096, 64.0, 1.6 / 4096),
            Vec3(-8.0, 64.0, 1.6 / 4096), Vec3(20.0, 64.0, 20.0), Vec3(20.25, 64.1, 20.5))) {
            viewer.packets.clear()
            fixture.position = position
            fixture.actor.syncTransform()
            val packet = viewer.packets.single()
            when (packet) {
                is ClientboundMoveEntityPacket -> client.setBase(client.decode(packet.xa.toLong(), packet.ya.toLong(), packet.za.toLong()))
                is ClientboundTeleportEntityPacket -> client.setBase(Vec3(packet.x, packet.y, packet.z))
                else -> error("Unexpected movement packet: $packet")
            }
            assertEquals(Math.round(position.x * 4096), Math.round(client.base.x * 4096))
            assertEquals(Math.round(position.y * 4096), Math.round(client.base.y * 4096))
            assertEquals(Math.round(position.z * 4096), Math.round(client.base.z * 4096))
            if (position.x == 20.0) assertTrue(packet is ClientboundTeleportEntityPacket)
        }
    }

    @Test
    fun `late joiners get their own movement baseline without resetting existing viewers`() {
        val fixture = Fixture()
        val first = Viewer()
        val late = Viewer()
        fixture.players.add(first.player)
        fixture.actor.syncTransform()
        fixture.position = Vec3(3.0, 64.0, 0.0)
        fixture.players.add(late.player)
        fixture.actor.sendCatchUpTo(late.player)
        fixture.actor.sendCatchUpTo(late.player)
        assertEquals(1, late.packets.filterIsInstance<ClientboundAddEntityPacket>().size)
        assertEquals(3.0, late.packets.filterIsInstance<ClientboundTeleportEntityPacket>().single().x)
        first.packets.clear()
        late.packets.clear()
        fixture.position = Vec3(4.0, 64.0, 0.0)
        fixture.actor.syncTransform()
        assertEquals(4 * 4096, first.packets.filterIsInstance<ClientboundMoveEntityPacket>().single().xa.toInt())
        assertEquals(4096, late.packets.filterIsInstance<ClientboundMoveEntityPacket>().single().xa.toInt())
    }

    @Test
    fun `departed and reconnected viewers are removed and spawned once even without motion`() {
        val fixture = Fixture()
        val first = Viewer()
        fixture.players.add(first.player)
        fixture.actor.syncTransform()
        fixture.actor.retainViewers(emptySet())
        assertEquals(1, first.packets.filterIsInstance<ClientboundRemoveEntitiesPacket>().size)
        val replacement = Viewer(first.player.uuid)
        fixture.players.clear()
        fixture.players.add(replacement.player)
        fixture.actor.sendCatchUpTo(replacement.player)
        fixture.actor.syncTransform()
        assertEquals(1, replacement.packets.filterIsInstance<ClientboundAddEntityPacket>().size)
        fixture.players.clear()
        fixture.actor.despawn()
        fixture.actor.despawn()
        assertEquals(1, replacement.packets.filterIsInstance<ClientboundRemoveEntitiesPacket>().size)
    }

    @Test
    fun `hidden actors do not spawn for catch-up and cleanup continues after a failed connection`() {
        val fixture = Fixture()
        val broken = Viewer()
        val healthy = Viewer()
        fixture.players.addAll(listOf(broken.player, healthy.player))
        fixture.actor.sendCatchUpTo(healthy.player)
        assertTrue(healthy.packets.isEmpty())
        fixture.actor.syncTransform()
        every { broken.connection.send(any<ClientboundRemoveEntitiesPacket>()) } throws IllegalStateException("closed")
        fixture.actor.despawn()
        fixture.actor.sendCatchUpTo(healthy.player)
        assertEquals(1, healthy.packets.filterIsInstance<ClientboundRemoveEntitiesPacket>().size)
        assertEquals(1, healthy.packets.filterIsInstance<ClientboundAddEntityPacket>().size)
    }

    @Test
    fun `negative rotations use floor and head rotation precedes movement`() {
        val fixture = Fixture()
        val viewer = Viewer()
        fixture.players.add(viewer.player)
        fixture.actor.syncTransform()
        viewer.packets.clear()
        fixture.yaw = -0.1f
        fixture.actor.syncTransform()
        assertEquals((-1).toByte(), (viewer.packets[0] as ClientboundRotateHeadPacket).yHeadRot)
        assertEquals((-1).toByte(), (viewer.packets[1] as ClientboundMoveEntityPacket).getyRot())
    }

    private class Viewer(id: UUID = UUID.randomUUID()) {
        val player = mockk<ServerPlayer>(relaxed = true)
        val connection = mockk<ServerGamePacketListenerImpl>(relaxed = true)
        val packets = mutableListOf<Packet<*>>()
        init {
            every { player.uuid } returns id
            player.connection = connection
            every { connection.send(any<Packet<*>>()) } answers { packets.add(firstArg()); Unit }
        }
    }

    private class Fixture {
        val players = mutableListOf<ServerPlayer>()
        var position = Vec3(0.0, 64.0, 0.0)
        var yaw = 0f
        val entity = mockk<Entity>(relaxed = true)
        val actor: PreviewActor
        init {
            every { entity.id } returns 123
            every { entity.uuid } returns UUID.randomUUID()
            every { entity.type } returns EntityType.ARMOR_STAND
            every { entity.position() } answers { position }
            every { entity.trackingPosition() } answers { position }
            every { entity.x } answers { position.x }
            every { entity.y } answers { position.y }
            every { entity.z } answers { position.z }
            every { entity.yRot } answers { yaw }
            every { entity.yHeadRot } answers { yaw }
            every { entity.deltaMovement } returns Vec3.ZERO
            every { entity.entityData.packDirty() } returns null
            every { entity.entityData.nonDefaultValues } returns null
            actor = object : PreviewActor(entity, "actor", { players }) {
                override fun buildSpawnPacket() = buildVanillaAddEntityPacket()
            }
        }
    }
}
