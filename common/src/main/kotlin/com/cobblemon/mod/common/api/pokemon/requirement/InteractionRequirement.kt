/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.pokemon.requirement

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack

interface InteractionRequirement : Requirement {
    fun check(context: InteractionAttemptContext): Boolean

    override fun check(pokemon: Pokemon): Boolean {
        throw UnsupportedOperationException(
            "ItemUsedRequirement is interaction-only and cannot be evaluated without context"
        )
    }
}

data class InteractionAttemptContext(
    val pokemon: PokemonEntity,
    val player: Player,
    val item: ItemStack
)

fun Requirement.isSatisfied(context: InteractionAttemptContext): Boolean {
    return when (this) {
        is InteractionRequirement -> this.check(context)
        else -> this.check(context.pokemon.pokemon)
    }
}
