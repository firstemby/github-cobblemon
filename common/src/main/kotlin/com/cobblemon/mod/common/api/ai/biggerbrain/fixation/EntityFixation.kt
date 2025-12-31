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
 * An entity's fixation, representing a task that takes a central role in the entity's behaviour
 * and excludes all other possible fixations.
 *
 * The role of the fixation is to do an action which is the primary focus of the entity at that time,
 * and which may be distracted from under some circumstances. An entity may be fixated on moving from
 * A to B, and then be distracted upon being hurt. Or maybe it's an attack fixation, in which case
 * damage doesn't distract it at all.
 *
 * The difference between a fixation and an objective is that objectives are capable of running in parallel,
 * and a fixation may even be in the pursuit of an objective, but the fixation is central.
 *
 * @author Hiroku
 * @since December 28th, 2025
 */
interface EntityFixation {
    /**
     * Returns true if the given distraction has stopped this fixation.
     * If you need to do clean up after being distracted, you need to do it in here.
     */
    fun isDistracted(distraction: Distraction, entity: LivingEntity): Boolean
    /** Continues doing whatever the fixation does. Returns true if the fixation should continue. */
    fun tick(entity: LivingEntity, brain: BiggerBrain): Boolean
    /** Gets a current description of the fixation for the debug screen. */
    fun getDescription(entity: LivingEntity): String
}