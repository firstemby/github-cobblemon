/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.biggerbrain.objective

import com.bedrockk.molang.Expression
import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.ai.biggerbrain.BiggerBrain
import com.cobblemon.mod.common.api.ai.biggerbrain.RegularMovementTracker
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.comfortDistraction
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.SimpleMoveFixation
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.Objective
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.ObjectiveStatus
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMostSpecificMoLangValue
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.mainThreadRuntime
import com.cobblemon.mod.common.util.resolveFloat
import com.cobblemon.mod.common.util.withQueryValue
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.memory.WalkTarget
import net.minecraft.world.phys.Vec3

class PathToIdealNestLocationTask(
    private val speedMultiplier: Expression
) : Objective {
    private var movementTask: com.cobblemon.mod.common.api.ai.biggerbrain.fixation.EntityFixation? = null

    override fun getDescription(entity: LivingEntity) = "Path to ideal nest"

    override fun perform(
        entity: LivingEntity,
        brain: BiggerBrain,
        activeObjectiveTags: MutableSet<String>
    ): ObjectiveStatus {
        if (entity !is PokemonEntity || entity !is PathfinderMob || !entity.isAlive) return ObjectiveStatus.INVALID

        if (brain.isFixation(movementTask)) {
            brain.sendDebugMessage("[PathToIdealNest] Fixation active.")
            return ObjectiveStatus.FIXATED
        }

        if (brain.getMemory(CobblemonMemories.PATERNAL_NEST_LOCATION) != null) {
            brain.sendDebugMessage("[PathToIdealNest] Already a nest parent; skipping.")
            return ObjectiveStatus.INVALID
        }

        if (entity.pokemon.breedingCooldown > 0) {
            brain.sendDebugMessage("[PathToIdealNest] Breeding cooldown active; skipping.")
            return ObjectiveStatus.INVALID
        }

        if (brain.getMemory(CobblemonMemories.READY_TO_BUILD_NEST) != true) {
            brain.sendDebugMessage("[PathToIdealNest] Not ready to build.")
            return ObjectiveStatus.INVALID
        }

        val mate = brain.getMemory(CobblemonMemories.POTENTIAL_MATE) as? PokemonEntity ?: return ObjectiveStatus.INVALID
        if (mate.pokemon.breedingCooldown > 0) {
            brain.sendDebugMessage("[PathToIdealNest] Mate breeding cooldown active; skipping.")
            return ObjectiveStatus.INVALID
        }
        val nestPos: BlockPos = brain.getMemory(CobblemonMemories.IDEAL_NEST_LOCATION) ?: return ObjectiveStatus.INVALID
        val nestCenter = Vec3.atCenterOf(nestPos)

        if (entity.distanceToSqr(nestCenter) <= 2.0) {
            brain.sendDebugMessage("[PathToIdealNest] Already at ideal nest location $nestPos.")
            return ObjectiveStatus.INACTIVE
        }

        mainThreadRuntime.withQueryValue("entity", entity.asMostSpecificMoLangValue())
        val speed = mainThreadRuntime.resolveFloat(speedMultiplier)

        if (!brain.distract(comfortDistraction)) return ObjectiveStatus.BLOCKED

        val movementTracker = RegularMovementTracker.attempt(entity, WalkTarget(nestCenter, speed, 1))
            ?: return ObjectiveStatus.ACTIVE
        brain.sendDebugMessage("[PathToIdealNest] Moving to ideal nest location $nestPos.")
        movementTask = brain.fixate(SimpleMoveFixation(movementTracker, "Move to ideal nest"))

        moveMateTo(mate, nestCenter, speed)

        return ObjectiveStatus.ACTIVE
    }

    private fun moveMateTo(mate: PokemonEntity, target: Vec3, speed: Float) {
        val mateBrain = mate.biggerBrain
        if (!mateBrain.distract(comfortDistraction)) return
        val tracker = RegularMovementTracker.attempt(mate, WalkTarget(target, speed, 1)) ?: return
        mateBrain.fixate(SimpleMoveFixation(tracker, "Move to ideal nest (mate)"))
    }
}
