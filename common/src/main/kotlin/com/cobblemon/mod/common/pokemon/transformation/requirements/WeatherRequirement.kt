/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.transformation.requirements

import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.transformation.requirements.template.EntityQueryRequirement
import net.minecraft.world.entity.LivingEntity

/**
 * A [EntityQueryRequirement] that checks if the world's weather [isRaining] and/or [isThundering].
 * This does not check the biome that the Pokemon is currently in, nor if the
 *
 * @property isRaining If the pokemon's world is raining.
 * @property isThundering If the pokemon's world is thundering.
 *
 * @author Paul
 * @since August 13th, 2022
 */
class WeatherRequirement(val isRaining: Boolean? = null, val isThundering: Boolean? = null) : EntityQueryRequirement {
    companion object {
        const val ADAPTER_VARIANT = "weather"
    }

    override fun check(pokemon: Pokemon, queriedEntity: LivingEntity): Boolean {
        val world = queriedEntity.level()
        return when {
            isRaining == true && !world.isRaining -> false
            isRaining == false && world.isRaining -> false
            isThundering == true && !world.isThundering -> false
            isThundering == false && world.isThundering -> false
            else -> true
        }
    }
}