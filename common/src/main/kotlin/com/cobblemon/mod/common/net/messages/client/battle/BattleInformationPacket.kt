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
import com.cobblemon.mod.common.util.readNullable
import com.cobblemon.mod.common.util.readString
import com.cobblemon.mod.common.util.writeNullable
import com.cobblemon.mod.common.util.writeString
import net.minecraft.network.RegistryFriendlyByteBuf
import java.time.Instant
import java.util.UUID

class BattleInformationPacket(val battle: UUID, val informationDTO: BattleInformationDTO): NetworkPacket<BattleInformationPacket> {
    override val id = ID
    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(battle)
        buffer.writeInt(informationDTO.turn)
        buffer.writeNullable(informationDTO.weather) { _, value -> encodeFieldEffect(buffer, value) }
        buffer.writeNullable(informationDTO.terrain) { _, value -> encodeFieldEffect(buffer, value) }
        buffer.writeNullable(informationDTO.room) { _, value -> encodeFieldEffect(buffer, value) }
        buffer.writeCollection(informationDTO.side1SidedEffects) { _, value -> encodeFieldEffect(buffer, value) }
        buffer.writeCollection(informationDTO.side2SidedEffects) { _, value -> encodeFieldEffect(buffer, value) }
    }

    companion object {
        val ID = cobblemonResource("battle_information")
        fun decode(buffer: RegistryFriendlyByteBuf) = BattleInformationPacket(
            battle = buffer.readUUID(),
            informationDTO = BattleInformationDTO(
                turn = buffer.readInt(),
                weather = buffer.readNullable { decodeFieldEffect(buffer) },
                terrain = buffer.readNullable { decodeFieldEffect(buffer) },
                room = buffer.readNullable { decodeFieldEffect(buffer) },
                side1SidedEffects = buffer.readList { decodeFieldEffect(buffer) },
                side2SidedEffects = buffer.readList { decodeFieldEffect(buffer) }
            )
        )

        private fun encodeFieldEffect(buffer: RegistryFriendlyByteBuf, fieldEffect: FieldEffect) {
            buffer.writeString(fieldEffect.id)
            buffer.writeInt(fieldEffect.turnStarted)
            buffer.writeNullable(fieldEffect.sourceEntityId) { _, value -> buffer.writeInt(value) }
        }

        private fun decodeFieldEffect(buffer: RegistryFriendlyByteBuf): FieldEffect {
            return FieldEffect(
                id = buffer.readString(),
                turnStarted = buffer.readInt(),
                sourceEntityId = buffer.readNullable { buffer.readInt() }
            )
        }
    }
}

data class BattleInformationDTO(
    val turn: Int,
    val weather: FieldEffect?,
    val terrain: FieldEffect?,
    val room: FieldEffect?,
    val side1SidedEffects: List<FieldEffect>,
    val side2SidedEffects: List<FieldEffect>
)

data class FieldEffect(
    val id: String,
    val turnStarted: Int,
    val sourceEntityId: Int?
)