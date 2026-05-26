import EntityQueryRequirement.EntityQueryRequirement
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.world.entity.LivingEntity

/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

<<<<<<<< HEAD:common/src/main/kotlin/com/cobblemon/mod/common/pokemon/transformation/requirements/WeatherRequirement.kt
package com.cobblemon.mod.common.pokemon.transformation.requirements
========
package com.cobblemon.mod.common.pokemon.requirements
>>>>>>>> origin/main:common/src/main/kotlin/com/cobblemon/mod/common/pokemon/requirements/WeatherRequirement.kt

import com.cobblemon.mod.common.api.pokemon.requirement.EntityQueryRequirement
import com.cobblemon.mod.common.pokemon.Pokemon
<<<<<<<< HEAD:common/src/main/kotlin/com/cobblemon/mod/common/pokemon/transformation/requirements/WeatherRequirement.kt
import com.cobblemon.mod.common.pokemon.transformation.requirements.template.EntityQueryRequirement
========
>>>>>>>> origin/main:common/src/main/kotlin/com/cobblemon/mod/common/pokemon/requirements/WeatherRequirement.kt
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