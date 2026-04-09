/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.entity.ai.steering

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.world.phys.Vec3

// A per-tick steering force contributor evaluated by SteerArbitrator.
// Return null from calculate() if the rule has nothing to say this tick.
interface SteerRule {

    val entity: PokemonEntity

    // Higher = evaluated first, consumes force budget before lower priority rules.
    // 90-100: obstacle avoidance
    // 60-89:  threat / boundary
    // 30-59:  social (separation, alignment, cohesion)
    // 0-29:   goal / wander
    val priority: Int

    // Multiplied against the rule output before budget accounting.
    val weight: Float

    // RGB 0-1 for the debug line drawn by SteerArbitrator.
    val debugColor: Triple<Float, Float, Float> get() = Triple(1f, 1f, 1f)

    fun calculate(): Vec3?
}
