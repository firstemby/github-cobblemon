/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.biggerbrain.fixation

import com.cobblemon.mod.common.api.ai.biggerbrain.BiggerBrain
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.Distraction
import net.minecraft.world.entity.LivingEntity

/**
 * A fixation that rejects all non-force distractions and does nothing, lasting until
 * the provided [condition] is no longer true.
 *
 * @author Hiroku
 * @since January 16th, 2026
 */
open class ConditionalLockFixation(
    val description: String,
    val condition: () -> Boolean
): EntityFixation, LockFixation {
    override fun canDistract(distraction: Distraction, entity: LivingEntity) = distraction.isForce
    override fun getDescription(entity: LivingEntity) = description
    override fun tick(entity: LivingEntity, brain: BiggerBrain) = condition()
}