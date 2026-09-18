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
import com.cobblemon.mod.common.api.nest.WildNestType
import com.cobblemon.mod.common.api.nest.WildNestTypes
import com.cobblemon.mod.common.block.WildNestBlock
import com.cobblemon.mod.common.block.entity.NestBlockEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.mainThreadRuntime
import com.cobblemon.mod.common.util.resolveFloat
import com.cobblemon.mod.common.util.withQueryValue
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.memory.WalkTarget
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import kotlin.jvm.optionals.getOrNull

class PathToNestTask(
    private val speedMultiplier: Expression
) : Objective {
    private var movementTask: com.cobblemon.mod.common.api.ai.biggerbrain.fixation.EntityFixation? = null

    override fun getDescription(entity: LivingEntity) = "Path to nest"

    override fun perform(
        entity: LivingEntity,
        brain: BiggerBrain,
        activeObjectiveTags: MutableSet<String>
    ): ObjectiveStatus {
        if (entity !is PokemonEntity || entity !is PathfinderMob || !entity.isAlive) return ObjectiveStatus.INVALID

        if (brain.isFixation(movementTask)) {
            brain.sendDebugMessage("[PathToNest] Fixation active.")
            return ObjectiveStatus.FIXATED
        }

        val isHoldingEgg = brain.getMemory(CobblemonMemories.HOLDING_EGG) ?: false
        val eggInNest = brain.getMemory(CobblemonMemories.EGG_IN_NEST) ?: false
        val paternalNestLoc = brain.getMemory(CobblemonMemories.PATERNAL_NEST_LOCATION)

        mainThreadRuntime.withQueryValue("entity", entity.asMostSpecificMoLangValue())
        val speed = mainThreadRuntime.resolveFloat(speedMultiplier)

        if (isHoldingEgg && !eggInNest && paternalNestLoc != null) {
            val paternalNestVec = Vec3.atCenterOf(paternalNestLoc)
            if (entity.distanceToSqr(paternalNestVec) <= 2.0) {
                brain.sendDebugMessage("[PathToNest] Already at paternal nest.")
                return ObjectiveStatus.INACTIVE
            }
            if (!brain.distract(comfortDistraction)) return ObjectiveStatus.BLOCKED
            val tracker = RegularMovementTracker.attempt(entity, WalkTarget(paternalNestVec, speed, 1)) ?: return ObjectiveStatus.ACTIVE
            brain.sendDebugMessage("[PathToNest] Moving to paternal nest at $paternalNestLoc.")
            movementTask = brain.fixate(SimpleMoveFixation(tracker, "Path to paternal nest"))
            return ObjectiveStatus.ACTIVE
        }

        if (entity.pokemon.breedingCooldown != 0) return ObjectiveStatus.INVALID

        val nestLocation = brain.getMemory(CobblemonMemories.NEST_LOCATION) ?: return ObjectiveStatus.INVALID
        val mate = brain.getMemory(CobblemonMemories.POTENTIAL_MATE) as? PokemonEntity ?: return ObjectiveStatus.INVALID

        val world = entity.level() as? ServerLevel ?: return ObjectiveStatus.INVALID
        val nestEntity = world.getBlockEntity(nestLocation) as? NestBlockEntity ?: return ObjectiveStatus.INVALID

        val allowedTypes = allowedNestTypes(entity, mate)
        val nestType = (nestEntity.blockState.block as? WildNestBlock)?.type ?: return ObjectiveStatus.INVALID
        if (!isNestTypeAllowed(nestType, entity, allowedTypes)) {
            brain.sendDebugMessage("[PathToNest] Nest type $nestType not allowed. Allowed=$allowedTypes.")
            return ObjectiveStatus.INVALID
        }
        val isNestWaterlogged = nestEntity.blockState.getOptionalValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED).orElse(false)
        if (!isWaterGroupCompatible(entity, mate, isNestWaterlogged)) {
            brain.sendDebugMessage("[PathToNest] Water-group mismatch for nest at $nestLocation (waterlogged=$isNestWaterlogged).")
            return ObjectiveStatus.INVALID
        }

        val claimerEntityOne = nestEntity.nestClaimedBy.first?.let { world.getEntity(it) }
        val claimerEntityTwo = nestEntity.nestClaimedBy.second?.let { world.getEntity(it) }
        if ((claimerEntityOne != null && nestEntity.nestClaimedBy.first != entity.uuid) &&
            (claimerEntityTwo != null && nestEntity.nestClaimedBy.second != entity.uuid)
        ) {
            brain.sendDebugMessage("[PathToNest] Nest already claimed by others at $nestLocation.")
            return ObjectiveStatus.INVALID
        }

        nestEntity.nestClaimedBy = Pair(entity.uuid, mate.uuid)

        val targetVec = Vec3.atCenterOf(nestLocation)
        if (entity.distanceToSqr(targetVec) <= 2.0 && mate.distanceToSqr(targetVec) <= 2.0) {
            brain.sendDebugMessage("[PathToNest] Both parents at nest $nestLocation.")
            return ObjectiveStatus.INACTIVE
        }
        if (nestEntity.getItem(0) != ItemStack.EMPTY) {
            brain.sendDebugMessage("[PathToNest] Nest at $nestLocation already has an egg.")
            return ObjectiveStatus.INVALID
        }

        if (!brain.distract(comfortDistraction)) return ObjectiveStatus.BLOCKED
        val movementTracker = RegularMovementTracker.attempt(entity, WalkTarget(targetVec, speed, 1)) ?: return ObjectiveStatus.ACTIVE
        brain.sendDebugMessage("[PathToNest] Moving to nest at $nestLocation.")
        movementTask = brain.fixate(SimpleMoveFixation(movementTracker, "Path to nest"))

        moveMateTo(mate, targetVec, speed)

        return ObjectiveStatus.ACTIVE
    }

    private fun moveMateTo(mate: PokemonEntity, target: Vec3, speed: Float) {
        val mateBrain = mate.biggerBrain
        if (!mateBrain.distract(comfortDistraction)) return
        val tracker = RegularMovementTracker.attempt(mate, WalkTarget(target, speed, 1)) ?: return
        mateBrain.fixate(SimpleMoveFixation(tracker, "Path to nest (mate)"))
    }

    private fun allowedNestTypes(entity: PokemonEntity, mate: PokemonEntity): Set<WildNestType> {
        val types = WildNestTypes.combinedTypes(entity, mate)
        val mutable = types.toMutableSet()
        if (!entity.pokemon.isWild() && !mate.pokemon.isWild()) {
            mutable.add(WildNestType.PLAYER)
        }
        return mutable
    }

    private fun isNestTypeAllowed(
        nestType: WildNestType,
        entity: PokemonEntity,
        allowedTypes: Set<WildNestType>
    ): Boolean {
        if (nestType == WildNestType.PLAYER) {
            return !entity.pokemon.isWild() && allowedTypes.contains(WildNestType.PLAYER)
        }
        return allowedTypes.contains(nestType)
    }

    private fun isWaterGroupCompatible(
        entity: PokemonEntity,
        mate: PokemonEntity,
        isNestWaterlogged: Boolean
    ): Boolean {
        val waterEggGroups = setOf("water_1", "water_2", "water_3")
        val entityEggGroups = entity.pokemon.species.eggGroups.map { it.name.lowercase() }
        val mateEggGroups = mate.pokemon.species.eggGroups.map { it.name.lowercase() }
        val entityHasWater = entityEggGroups.any { it in waterEggGroups }
        val mateHasWater = mateEggGroups.any { it in waterEggGroups }
        val entityOnlyWater = entityEggGroups.all { it in waterEggGroups }
        val mateOnlyWater = mateEggGroups.all { it in waterEggGroups }

        return if (isNestWaterlogged) {
            entityHasWater && mateHasWater
        } else {
            !(entityOnlyWater || mateOnlyWater)
        }
    }
}
