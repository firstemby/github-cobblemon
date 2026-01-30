/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.biggerbrain.objective

import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.ai.biggerbrain.BiggerBrain
import com.cobblemon.mod.common.api.ai.biggerbrain.RegularMovementTracker
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.comfortDistraction
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.SimpleMoveFixation
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.Objective
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.ObjectiveStatus
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.memory.WalkTarget
import net.minecraft.world.phys.Vec3

class RetrieveEggTask : Objective {
    private var movementTask: com.cobblemon.mod.common.api.ai.biggerbrain.fixation.EntityFixation? = null

    override fun getDescription(entity: LivingEntity) = "Retrieve egg"

    override fun perform(
        entity: LivingEntity,
        brain: BiggerBrain,
        activeObjectiveTags: MutableSet<String>
    ): ObjectiveStatus {
        if (entity !is PokemonEntity || entity !is PathfinderMob || !entity.isAlive) return ObjectiveStatus.INVALID

        if (brain.isFixation(movementTask)) {
            brain.sendDebugMessage("[RetrieveEgg] Fixation active.")
            return ObjectiveStatus.FIXATED
        }

        val eggInNest = brain.getMemory(CobblemonMemories.EGG_IN_NEST)
        if (eggInNest != null && eggInNest == true) {
            brain.sendDebugMessage("[RetrieveEgg] Egg already in nest.")
            return ObjectiveStatus.INVALID
        }

        val eggPos: BlockPos = brain.getMemory(CobblemonMemories.EGG_LOCATION) ?: return ObjectiveStatus.INVALID
        val eggCenter = Vec3.atCenterOf(eggPos)
        activeObjectiveTags.add(Objective.OBJECTIVE_TAG_WANDERING)

        if (entity.distanceToSqr(eggCenter) <= 2.0) {
            brain.sendDebugMessage("[RetrieveEgg] At egg location $eggPos; ready to grab.")
            brain.setMemory(CobblemonMemories.READY_TO_GRAB_EGG, true)
            return ObjectiveStatus.INACTIVE
        }

        brain.eraseMemory(CobblemonMemories.READY_TO_GRAB_EGG)

        if (!brain.distract(comfortDistraction)) return ObjectiveStatus.BLOCKED
        val tracker = RegularMovementTracker.attempt(entity, WalkTarget(eggCenter, 1.0F, 1)) ?: return ObjectiveStatus.ACTIVE
        brain.sendDebugMessage("[RetrieveEgg] Moving to egg at $eggPos.")
        movementTask = brain.fixate(SimpleMoveFixation(tracker, "Retrieve egg"))

        return ObjectiveStatus.ACTIVE
    }
}
