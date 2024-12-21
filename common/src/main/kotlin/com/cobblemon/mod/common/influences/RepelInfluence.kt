/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.influences

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.effects.CobblemonMobEffects
import com.cobblemon.mod.common.api.spawning.influence.SpawningInfluence
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import kotlin.random.Random

class RepelInfluence(
    val player: ServerPlayer
) : SpawningInfluence {

    override fun affectSpawn(entity: Entity) {
        super.affectSpawn(entity)

        if (entity !is PokemonEntity || !player.hasEffect(CobblemonMobEffects.REPEL)) return

        val wildLevel = entity.pokemon.level
        val firstNonFaintedPokemon = Cobblemon.storage.getParty(player)
            .firstOrNull { !it.isFainted() } ?: return

        val ownedLevel = firstNonFaintedPokemon.level

        if (wildLevel < ownedLevel) {
            entity.pokemon.level = calculateLevel(wildLevel, ownedLevel)
        }
    }

    private fun calculateLevel(wildLevel: Int, ownedLevel: Int): Int {
        var maxLevel = (wildLevel * 0.2).toInt() + wildLevel
        if (maxLevel <= ownedLevel) maxLevel += 1
        return Random.nextInt(ownedLevel, maxLevel)
    }
}
