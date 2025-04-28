/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.ai

import com.bedrockk.molang.runtime.value.DoubleValue
import com.cobblemon.mod.common.api.ai.SleepDepth
import com.cobblemon.mod.common.api.conditional.RegistryLikeCondition
import com.cobblemon.mod.common.api.molang.ObjectValue
import com.cobblemon.mod.common.api.spawning.TimeRange
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.block.Block

/**
 * Behavioural properties relating to a Pokémon sleeping. This can be wild sleeping or sleeping on the player or both.
 *
 * @author Hiroku
 * @since July 16th, 2022
 */
class RestBehaviour {
    val canSleep = false
    val times = TimeRange.timeRanges["night"]!!
    val blocks = mutableListOf<RegistryLikeCondition<Block>>()
    val biomes = mutableListOf<RegistryLikeCondition<Biome>>()
    val light = IntRange(0, 15)
    val depth = SleepDepth.normal
    val canSeeSky: Boolean? = null
    val skyLight: IntRange? = null

    val willSleepOnBed = false

    @Transient
    val struct = ObjectValue<RestBehaviour>(this).also {
        it.addFunction("can_sleep") { DoubleValue(canSleep) }
        it.addFunction("min_light") { DoubleValue(light.first) }
        it.addFunction("max_light") { DoubleValue(light.last) }
        it.addFunction("will_sleep_on_bed") { DoubleValue(willSleepOnBed) }
    }
}