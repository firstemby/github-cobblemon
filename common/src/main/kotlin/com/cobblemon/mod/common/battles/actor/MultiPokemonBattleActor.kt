/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battles.actor

import com.cobblemon.mod.common.api.battles.model.actor.AIBattleActor
import com.cobblemon.mod.common.api.battles.model.actor.ActorType
import com.cobblemon.mod.common.api.battles.model.actor.FleeableBattleActor
import com.cobblemon.mod.common.api.battles.model.ai.BattleAI
import com.cobblemon.mod.common.api.text.text
import com.cobblemon.mod.common.battles.ai.RandomBattleAI
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon
import java.util.UUID
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3

class MultiPokemonBattleActor(
        pokemonList: List<BattlePokemon>,
        override val fleeDistance: Float,
        artificialDecider: BattleAI = RandomBattleAI(),
        uuid: UUID = UUID.randomUUID()
) : AIBattleActor(uuid, pokemonList, artificialDecider), FleeableBattleActor {
    override fun getName(): MutableComponent = "Wild Pokémon".text() // TODO probably remove by making it nullable
    override fun nameOwned(name: String): MutableComponent = Component.literal(name)
    override val type = ActorType.WILD
    override fun getWorldAndPosition(): Pair<ServerLevel, Vec3>? {
// Ensure there are Pokémon in the list
        if (pokemonList.isEmpty()) return null

        // Gather all positions and ensure they share the same world
        val positionsAndWorlds = pokemonList.mapNotNull { battlePokemon ->
            val entity = battlePokemon.effectedPokemon.entity
            val level = entity?.level() as? ServerLevel
            val position = entity?.position()
            if (level != null && position != null) {
                level to position
            } else {
                null
            }
        }

        if (positionsAndWorlds.isEmpty()) return null

        // Ensure all Pokémon are in the same world
        val world = positionsAndWorlds.first().first
        if (positionsAndWorlds.any { it.first != world }) return null

        // Compute the average position
        val averagePosition = positionsAndWorlds
                .map { it.second }
                .reduce { acc, pos -> acc.add(pos) }
                .scale(1.0 / positionsAndWorlds.size)

        return world to averagePosition
    }
}