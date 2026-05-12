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
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon
import com.cobblemon.mod.common.net.messages.client.battle.BattleInitializePacket.ActiveBattlePokemonDTO
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.readItemStack
import com.cobblemon.mod.common.util.readString
import com.cobblemon.mod.common.util.readText
import com.cobblemon.mod.common.util.writeItemStack
import com.cobblemon.mod.common.util.writeString
import com.cobblemon.mod.common.util.writeText
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import java.time.Instant

class TeamPreviewPacket : NetworkPacket<TeamPreviewPacket> {
    override val id = ID

    val selections: Int
    val team: List<BattlePokemonDTO?>
    val opponent: List<BattlePokemonDTO?>
    val mustPickBy: Instant
    val opponentName: Component

    constructor(
        selections: Int,
        team: List<BattlePokemonDTO>,
        opponent: List<BattlePokemonDTO>,
        mustPickBy: Instant,
        opponentName: Component
    ) {
        this.selections = selections
        this.team = team
        this.opponent = opponent
        this.mustPickBy = mustPickBy
        this.opponentName = opponentName
    }

    constructor(
        selections: Int,
        team: List<Pokemon?>,
        opponent: List<Pokemon?>,
        openTeamSheet: Boolean,
        mustPickBy: Instant,
        opponentName: Component
    ) {
        this.selections = selections
        this.team = team.map { it?.let { BattlePokemon(it).toBattleDTO(true) } }
        this.opponent = opponent.map { it?.let { BattlePokemon(it).toBattleDTO(openTeamSheet) } }
        this.mustPickBy = mustPickBy
        this.opponentName = opponentName
    }

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeInt(selections)
        buffer.writeCollection(team) { _, value ->
            buffer.writeNullable(value) { _, value -> encodeBattlePokemonDTO(buffer, value) }
        }
        buffer.writeCollection(opponent) { _, value ->
            buffer.writeNullable(value) { _, value -> encodeBattlePokemonDTO(buffer, value) }
        }
        buffer.writeInstant(mustPickBy)
        buffer.writeText(opponentName)
    }

    private fun encodeBattlePokemonDTO(buffer: RegistryFriendlyByteBuf, dto: BattlePokemonDTO) {
        buffer.writeUUID(dto.uuid)
        buffer.writeBoolean(dto.fainted)
        buffer.writeNullable(dto.ability) { _, v -> buffer.writeString(v) }
        buffer.writeCollection(dto.moves) { _, v ->
            buffer.writeNullable(v) { _, dto ->
                buffer.writeText(dto.move)
                buffer.writeInt(dto.timesUsed)
            }
        }
        buffer.writeNullable(dto.heldItem) { _, item -> buffer.writeItemStack(item) }
        buffer.writeMap(dto.buffs, { _, v -> buffer.writeEnum(v) }, { _, v -> buffer.writeDouble(v) })
        buffer.writeNullable(dto.speed) { _, v -> buffer.writeInt(v) }
        buffer.writeNullable(dto.terastallized) { _, v -> buffer.writeResourceLocation(v.id) }
        buffer.writeNullable(dto.activeBattlePokemonDTO) { _, v -> v.saveToBuffer(buffer) }
    }

    companion object {
        val ID = cobblemonResource("team_preview")

        fun decode(buffer: RegistryFriendlyByteBuf): TeamPreviewPacket {
            return TeamPreviewPacket(
                buffer.readInt(),
                buffer.readList { buffer.readNullable { decodeBattlePokemonDTO(buffer) } },
                buffer.readList { buffer.readNullable { decodeBattlePokemonDTO(buffer) } },
                buffer.readInstant(),
                buffer.readText()
            )
        }

        private fun decodeBattlePokemonDTO(buffer: RegistryFriendlyByteBuf): BattlePokemonDTO {
            return BattlePokemonDTO(
                uuid = buffer.readUUID(),
                fainted = buffer.readBoolean(),
                ability = buffer.readNullable { buffer.readString() },
                moves = buffer.readList { buffer.readNullable { MoveDTO(buffer.readText(), buffer.readInt()) } },
                heldItem = buffer.readNullable { buffer.readItemStack() },
                buffs = buffer.readMap({ buffer.readEnum(Stats::class.java) }, { buffer.readDouble() }),
                speed = buffer.readNullable { buffer.readInt() },
                terastallized = buffer.readNullable { TeraTypes.get(buffer.readResourceLocation()) },
                activeBattlePokemonDTO = buffer.readNullable { ActiveBattlePokemonDTO.loadFromBuffer(buffer) }
            )
        }
    }
}