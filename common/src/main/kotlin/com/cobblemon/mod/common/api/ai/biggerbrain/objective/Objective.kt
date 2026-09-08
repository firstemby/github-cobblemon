/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.biggerbrain.objective

import com.cobblemon.mod.common.api.ai.biggerbrain.BiggerBrain
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.memory.MemoryModuleType

/**
 * An ongoing priority for an entity. Objectives are managed by the [BiggerBrain] system. Objectives can act out
 * entirely within their own logic, or they can alter memories or create fixations for the entity to take up its
 * entire attention.
 *
 * @author Hiroku
 * @since December 29th, 2025
 */
interface Objective {
    companion object {
        /**
         * The idea behind this is that later objectives may like to know or respect that something impacting wandering
         * has taken place.
         */
        val OBJECTIVE_TAG_WANDERING = "WANDERING"
    }

    /** The memory types that, upon being changed, should trigger [onMemoryChanged]. */
    val observedMemories: List<MemoryModuleType<*>>
        get() = emptyList()

    /** Gets a current description for this objective. This is purely used for the debug overlay. */
    fun getDescription(entity: LivingEntity): String
    /** Performs this objective's logic for a tick. */
    fun perform(entity: LivingEntity, brain: BiggerBrain, activeObjectiveTags: MutableSet<String>): ObjectiveStatus
    /** Runs when an objective is added to a [BiggerBrain]. Prime opportunity to register memory subscriptions. */
    fun onRegister(entity: LivingEntity, brain: BiggerBrain) {}
}