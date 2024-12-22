/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.influences

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnAction
import com.cobblemon.mod.common.api.spawning.detail.SpawnAction
import com.cobblemon.mod.common.api.spawning.influence.SpawningInfluence
import com.cobblemon.mod.common.effects.CobblemonMobEffects
import net.minecraft.server.level.ServerPlayer

class RepelInfluence(
    val player: ServerPlayer
) : SpawningInfluence {

    override fun affectAction(action: SpawnAction<*>) {
        super.affectAction(action)

        if (action !is PokemonSpawnAction || !player.hasEffect(CobblemonMobEffects.REPEL)) return

        val firstNonFaintedPokemon = Cobblemon.storage.getParty(player)
            .firstOrNull { !it.isFainted() } ?: return

        if (action.props.level == null) {
            val level = action.detail.getDerivedLevelRange()
                .filter { it >= firstNonFaintedPokemon.level }
                .randomOrNull()?.coerceAtLeast(firstNonFaintedPokemon.level)
                ?: firstNonFaintedPokemon.level

            action.props.level = level
        }
    }
}
