/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.biggerbrain.distraction

import net.minecraft.world.damagesource.DamageSource

/**
 * Something that may distract an entity from its current fixation. The majority of fixations
 * will rely on the types of distraction to determine if they should be distracted.
 *
 * @author Hiroku
 * @since December 29th, 2025
 */
open class Distraction(val types: Set<DistractionType>) {
    constructor(vararg types: DistractionType) : this(types.toSet())

    val isForce = DistractionType.FORCE in types
    val isComfort = DistractionType.COMFORT in types
    val isDamage = DistractionType.DAMAGE in types
    val isMove = DistractionType.MOVE in types
    val isLook = DistractionType.LOOK in types

    fun hasAnyOf(vararg distractionTypes: DistractionType): Boolean {
        return distractionTypes.any { it in types }
    }

    companion object {
        val FORCE = Distraction(DistractionType.FORCE)
    }
}

/** Singleton for a COMFORT type of distraction. */
object ComfortDistraction : Distraction(DistractionType.COMFORT)

open class HurtDistraction(val damageSource: DamageSource) : Distraction(DistractionType.DAMAGE)