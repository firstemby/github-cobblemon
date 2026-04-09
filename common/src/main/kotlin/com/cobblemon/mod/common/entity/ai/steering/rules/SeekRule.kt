/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.entity.ai.steering.rules

import com.cobblemon.mod.common.entity.ai.steering.SteerRule
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.entity.pokemon.ai.PokemonMoveControl
import com.cobblemon.mod.common.entity.pokemon.extensions.steerToTarget
import net.minecraft.world.phys.Vec3

// Steers toward a target position. Reads speed from PokemonMoveControl.steerSpeed.
// Return null from targetProvider to deactivate for a tick.
class SeekRule(
    override val entity: PokemonEntity,
    override val priority: Int = 10,
    override val weight: Float = 1.0f,
    override val debugColor: Triple<Float, Float, Float> = Triple(0f, 1f, 1f),
    val maxForce: Double,
    val horzOnly: Boolean = false,
    val arrivalRange: Double? = null,
    val targetProvider: (PokemonEntity) -> Vec3?
) : SteerRule {

    override fun calculate(): Vec3? {
        val target = targetProvider(entity) ?: return null
        val speed = (entity.moveControl as? PokemonMoveControl)?.steerSpeed ?: 0.1
        return entity.steerToTarget(target, speed, maxForce, arrivalRange, horzOnly)
    }
}
