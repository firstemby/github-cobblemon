/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.battle

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.readText
import com.cobblemon.mod.common.util.writeText
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import java.util.UUID

data class StadiumPreviewMember(val species: String, val aspects: Set<String>, val name: Component, val healthy: Boolean)

data class StadiumBattleStatePacket(
    val battleId: UUID,
    val phase: String,
    val rqid: Int = 0,
    val slots: Int = 1,
    val own: List<StadiumPreviewMember> = emptyList(),
    val opponent: List<StadiumPreviewMember> = emptyList(),
    val decisionSeconds: Int = -1,
    val opponentSeconds: Int = -1,
    val matchSeconds: Int = -1,
    val message: Component = Component.empty()
) : NetworkPacket<StadiumBattleStatePacket> {
    override val id get() = ID
    override fun encode(buffer: RegistryFriendlyByteBuf) {
        require(slots in 1..2 && phase in phases)
        buffer.writeUUID(battleId)
        buffer.writeUtf(phase, 16)
        buffer.writeInt(rqid)
        buffer.writeByte(slots)
        fun members(team: List<StadiumPreviewMember>) {
            require(team.size <= 6)
            buffer.writeByte(team.size)
            team.forEach { member ->
                require(member.aspects.size <= MAX_ASPECTS)
                buffer.writeResourceLocation(ResourceLocation.parse(member.species))
                buffer.writeByte(member.aspects.size)
                member.aspects.forEach { buffer.writeUtf(it, 128) }
                buffer.writeText(member.name)
                buffer.writeBoolean(member.healthy)
            }
        }
        members(own)
        members(opponent)
        buffer.writeInt(decisionSeconds)
        buffer.writeInt(opponentSeconds)
        buffer.writeInt(matchSeconds)
        buffer.writeText(message)
    }

    companion object {
        val ID = cobblemonResource("stadium_battle_state")
        private const val MAX_ASPECTS = 64
        private val phases = setOf("preview", "waiting", "battle", "closed")

        fun decode(buffer: RegistryFriendlyByteBuf): StadiumBattleStatePacket {
            val battleId = buffer.readUUID()
            val phase = buffer.readUtf(16)
            val rqid = buffer.readInt()
            val slots = buffer.readUnsignedByte().toInt()
            require(slots in 1..2 && phase in phases)
            fun members(): List<StadiumPreviewMember> {
                val size = buffer.readUnsignedByte().toInt()
                require(size <= 6)
                return List(size) {
                    val species = buffer.readResourceLocation().toString()
                    val count = buffer.readUnsignedByte().toInt()
                    require(count <= MAX_ASPECTS)
                    val aspects = List(count) { buffer.readUtf(128) }.toSet()
                    StadiumPreviewMember(species, aspects, buffer.readText(), buffer.readBoolean())
                }
            }
            return StadiumBattleStatePacket(battleId, phase, rqid, slots, members(), members(),
                buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readText())
        }
    }
}
