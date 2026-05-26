import com.cobblemon.mod.common.api.spawning.TimeRange

/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

<<<<<<<< HEAD:common/src/main/kotlin/com/cobblemon/mod/common/pokemon/transformation/requirements/TimeRangeRequirement.kt
package com.cobblemon.mod.common.pokemon.transformation.requirements

import com.cobblemon.mod.common.api.pokemon.transformation.requirement.TransformationRequirement
import com.cobblemon.mod.common.api.spawning.TimeRange
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.transformation.requirements.template.EntityQueryRequirement
import net.minecraft.world.entity.LivingEntity

/**
 * A [TransformationRequirement] for when the current time must be in the provided [TimeRange].
 *
 * @property range The required [TimeRange].
========
package com.cobblemon.mod.common.pokemon.requirements

import com.cobblemon.mod.common.api.pokemon.requirement.EntityQueryRequirement
import com.cobblemon.mod.common.api.spawning.TimeRange
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.world.entity.LivingEntity

/**
 * An [com.cobblemon.mod.common.api.pokemon.requirement.Requirement] for when the current time must be in the provided [TimeRange].
>>>>>>>> origin/main:common/src/main/kotlin/com/cobblemon/mod/common/pokemon/requirements/TimeRangeRequirement.kt
 *
 * @author Licious
 * @since March 26th, 2022
 */
class TimeRangeRequirement(val range: TimeRange = TimeRange(0..23999)) : EntityQueryRequirement {
    override fun check(pokemon: Pokemon, queriedEntity: LivingEntity) = this.range.contains((queriedEntity.level().dayTime() % DAY_DURATION).toInt())

    companion object {
        const val ADAPTER_VARIANT = "time_range"
        private const val DAY_DURATION = 24000
    }
}