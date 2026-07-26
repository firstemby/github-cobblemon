/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.ai.moves.events

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity

abstract class MoveEventAction {

    abstract fun execute(owner: PokemonEntity, target: LivingEntity, parentEntity: Entity)

    companion object {
        val types = mutableMapOf(
            cobblemonResource("createmove") to CreateMoveEventAction::class.java,
        )
    }
}