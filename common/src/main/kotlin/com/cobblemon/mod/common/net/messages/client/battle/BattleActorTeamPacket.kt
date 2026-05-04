/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.battle

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.api.types.tera.TeraTypes
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.readItemStack
import com.cobblemon.mod.common.util.readString
import com.cobblemon.mod.common.util.readText
import com.cobblemon.mod.common.util.writeItemStack
import com.cobblemon.mod.common.util.writeString
import com.cobblemon.mod.common.util.writeText
import net.minecraft.network.RegistryFriendlyByteBuf
import java.util.UUID

class BattleActorTeamPacket(val actor: UUID, val team: List<BattlePokemonDTO>): NetworkPacket<BattleActorTeamPacket> {
    override val id = ID
    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(actor)
        buffer.writeCollection(team) { pb, value ->
            pb.writeUUID(value.uuid)
            pb.writeBoolean(value.fainted)
            buffer.writeNullable (value.ability) { _, v -> buffer.writeString(v) }
            pb.writeCollection(value.moves) { _, v ->
                pb.writeNullable(v) { _, dto ->
                    buffer.writeText(dto.move)
                    buffer.writeInt(dto.timesUsed)
                }
            }
            pb.writeNullable(value.heldItem) { _, item -> buffer.writeItemStack(item) }
            pb.writeMap(value.buffs, { _, v -> buffer.writeEnum(v) }, { _, v -> buffer.writeDouble(v) })
            buffer.writeNullable(value.speed) { _, v -> buffer.writeInt(v) }
            buffer.writeNullable(value.terastallized) { _, v -> buffer.writeResourceLocation(v.id) }
            buffer.writeNullable(value.activeBattlePokemonDTO) { _, v -> v.saveToBuffer(buffer) }
        }
    }

    companion object {
        val ID = cobblemonResource("battle_team")
        fun decode(buffer: RegistryFriendlyByteBuf) = BattleActorTeamPacket(
            actor = buffer.readUUID(),
            team = buffer.readList {
                BattlePokemonDTO(
                    uuid = buffer.readUUID(),
                    fainted = buffer.readBoolean(),
                    ability = buffer.readNullable { buffer.readString() },
                    moves = buffer.readList { buffer.readNullable { MoveDTO(buffer.readText(), buffer.readInt()) } },
                    heldItem = buffer.readNullable { buffer.readItemStack() },
                    buffs = buffer.readMap({ buffer.readEnum(Stats::class.java) }, { buffer.readDouble() }),
                    speed = buffer.readNullable { buffer.readInt() },
                    terastallized = buffer.readNullable { TeraTypes.get(buffer.readResourceLocation()) },
                    activeBattlePokemonDTO = buffer.readNullable { BattleInitializePacket.ActiveBattlePokemonDTO.loadFromBuffer(buffer) }
                )
            }
        )
    }
}