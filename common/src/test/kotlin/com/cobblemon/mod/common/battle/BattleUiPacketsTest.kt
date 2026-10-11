/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battle

import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.junit.BootstrapMinecraft
import com.cobblemon.mod.common.net.messages.client.battle.BattleActorInformationPacket
import com.cobblemon.mod.common.net.messages.client.battle.BattleInformationDTO
import com.cobblemon.mod.common.net.messages.client.battle.BattleInformationPacket
import com.cobblemon.mod.common.net.messages.client.battle.BattlePokemonDTO
import com.cobblemon.mod.common.net.messages.client.battle.FieldEffect
import com.cobblemon.mod.common.net.messages.client.battle.MoveDTO
import com.cobblemon.mod.common.net.messages.client.battle.TeamPreviewPacket
import io.netty.buffer.Unpooled
import net.minecraft.core.RegistryAccess
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

@BootstrapMinecraft
class BattleUiPacketsTest {
    private fun pokemon() = BattlePokemonDTO(
        UUID.randomUUID(), false, null,
        listOf(MoveDTO(Component.translatable("cobblemon.move.tackle"), 2), null, null, null),
        null, mapOf(Stats.SPEED to 1.5), null, null, null
    )

    @Test
    fun `preview preserves empty party slots and selection indices`() {
        val member = pokemon()
        val packet = TeamPreviewPacket(
            1, listOf(null, member, null), listOf(member, null),
            Instant.parse("2026-01-01T00:00:00Z"), Component.literal("Opponent")
        )
        val buffer = RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY)
        try {
            packet.encode(buffer)
            val decoded = TeamPreviewPacket.decode(buffer)
            assertEquals(packet.team, decoded.team)
            assertEquals(packet.opponent, decoded.opponent)
            assertEquals(packet.mustPickBy, decoded.mustPickBy)
            assertEquals(1, decoded.selections)
            assertEquals(packet.opponentName, decoded.opponentName)
            assertEquals(0, buffer.readableBytes())
        } finally {
            buffer.release()
        }
    }

    @Test
    fun `actor update preserves unknown opponent details`() {
        val packet = BattleActorInformationPacket(UUID.randomUUID(), pokemon())
        val buffer = RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY)
        try {
            packet.encode(buffer)
            val decoded = BattleActorInformationPacket.decode(buffer)
            assertEquals(packet.actor, decoded.actor)
            assertEquals(packet.update, decoded.update)
            assertNull(decoded.update.ability)
            assertNull(decoded.update.speed)
            assertNull(decoded.update.heldItem)
            assertEquals(0, buffer.readableBytes())
        } finally {
            buffer.release()
        }
    }

    @Test
    fun `field updates preserve sides and stacked hazards`() {
        val spikes = FieldEffect("spikes", 2, null)
        val packet = BattleInformationPacket(UUID.randomUUID(), BattleInformationDTO(
            4, FieldEffect("raindance", 1, 23), null, FieldEffect("trickroom", 3, null),
            listOf(spikes, spikes), listOf(FieldEffect("reflect", 3, null))
        ))
        val buffer = RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY)
        try {
            packet.encode(buffer)
            val decoded = BattleInformationPacket.decode(buffer)
            assertEquals(packet.battle, decoded.battle)
            assertEquals(packet.informationDTO, decoded.informationDTO)
            assertEquals(0, buffer.readableBytes())
        } finally {
            buffer.release()
        }
    }
}
