/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.biggerbrain.objective

import com.cobblemon.mod.common.api.ai.BehaviourConfigurationContext
import com.cobblemon.mod.common.api.ai.ExpressionOrEntityVariable
import com.cobblemon.mod.common.api.ai.asVariables
import com.cobblemon.mod.common.api.ai.biggerbrain.BiggerBrain
import com.cobblemon.mod.common.api.ai.biggerbrain.MovementStatus
import com.cobblemon.mod.common.api.ai.biggerbrain.MovementTracker
import com.cobblemon.mod.common.api.ai.biggerbrain.RegularMovementTracker
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.Distraction
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.EntityFixation
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.MovementTrackerFixation
import com.cobblemon.mod.common.api.ai.config.objective.ObjectiveConfig
import com.cobblemon.mod.common.api.ai.movement.FluidKind
import com.cobblemon.mod.common.api.ai.movement.MovementCapabilities
import com.cobblemon.mod.common.api.ai.movement.Occupancy
import com.cobblemon.mod.common.api.ai.movement.TraversalPolicy
import com.cobblemon.mod.common.api.ai.resolveFloat
import com.cobblemon.mod.common.api.ai.resolveInt
import com.cobblemon.mod.common.entity.ai.OmniPathNavigation
import com.cobblemon.mod.common.util.asExpression
import com.mojang.datafixers.util.Either
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.memory.WalkTarget

/**
 * Gets the entity out of a fluid that's killing it:
 * - A fluid it can neither swim nor breathe in, once its head is under. It sinks, and this looks for a route along the
 *   bottom to anywhere its head would be above the surface, preferring the route that spends the least time under.
 * - Lava, when it isn't fire immune. Everything can swim in lava, so this looks for the quickest way to dry land or,
 *   failing that, water.
 *
 * If there's no route, nothing is done about it and the entity drowns or burns. Once a route is found, the entity is
 * committed to it: damage and forceful distractions don't interrupt it while it's still in danger.
 *
 * @author Hiroku
 * @since September 30th, 2026
 */
class WalkOutOfWater(
    /** How far horizontally to look for somewhere to surface. */
    val horizontalRange: Int = 12,
    /** How far above the entity to look for somewhere to surface. */
    val verticalRange: Int = 12,
    /** How many of the closest exits to hand to the pathfinder. More exits make the search slower. */
    val maxExits: Int = 48,
    val speedMultiplier: Float = 0.4F,
    /**
     * How many times a route may be searched for while submerged. A search that finds nothing ends the attempt
     * outright; extra searches are only for when a found route gets blocked along the way.
     */
    val maxSearches: Int = 3,
) : Objective {
    var fixation: EntityFixation? = null
    /** Searches done since the entity's head last went under. */
    var searches = 0
    /** Set when a search found no way out. Reset once the entity's head is out of the fluid. */
    var gaveUp = false

    override fun getDescription(entity: LivingEntity) = when {
        gaveUp -> "Escape deadly fluid (no way out)"
        else -> "Escape deadly fluid"
    }

    override fun perform(
        entity: LivingEntity,
        brain: BiggerBrain,
        activeObjectiveTags: MutableSet<String>
    ): ObjectiveStatus {
        if (entity !is PathfinderMob || entity.navigation !is OmniPathNavigation) {
            return ObjectiveStatus.INVALID
        }

        // Cheap check first since this runs every tick for every Pokémon.
        val capabilities = if (entity.isInWater || entity.isInLava) MovementCapabilities.of(entity) else null
        val danger = capabilities?.let { deadlyFluid(entity, it) } ?: FluidKind.NONE
        if (capabilities == null || danger == FluidKind.NONE) {
            searches = 0
            gaveUp = false
            return if (brain.isFixation(fixation)) ObjectiveStatus.FIXATED else ObjectiveStatus.INACTIVE
        }

        if (brain.isFixation(fixation)) {
            return ObjectiveStatus.FIXATED
        } else if (gaveUp || searches >= maxSearches) {
            return ObjectiveStatus.INACTIVE
        } else if (!brain.canDistract(Distraction.FORCE)) {
            return ObjectiveStatus.BLOCKED
        }

        searches++
        val navigation = entity.navigation as OmniPathNavigation
        val policy = TraversalPolicy.escapeFluid(entity, danger)
        val searchRange = horizontalRange * 1.5F + verticalRange
        // Dry land first so the escape doesn't end knee-deep, then anywhere else that's safe.
        val isDryLand: (Occupancy, FluidKind) -> Boolean = { occupancy, fluid -> occupancy == Occupancy.WALK && fluid == FluidKind.NONE }
        val isSafe: (Occupancy, FluidKind) -> Boolean = when (danger) {
            // Wading in it is fine since the head's out. Lava is never a way out of water.
            FluidKind.LAVA -> { occupancy, fluid -> fluid != FluidKind.LAVA && policy.allows(occupancy, fluid) }
            else -> { occupancy, fluid -> occupancy == Occupancy.WADE && fluid == danger }
        }
        val path = sequenceOf(isDryLand, isSafe)
            .map { findExits(entity, capabilities, it) }
            .filter { it.isNotEmpty() }
            .mapNotNull { navigation.planPath(it, 0, policy, searchRange) }
            .firstOrNull { it.canReach() }

        if (path == null) {
            gaveUp = true
            return ObjectiveStatus.INACTIVE
        }

        val walkTarget = WalkTarget(path.target, speedMultiplier, 0)
        val tracker = RegularMovementTracker.following(entity, walkTarget, path, policy)
        if (tracker == null) {
            gaveUp = true
            return ObjectiveStatus.INACTIVE
        }
        // Only interrupt whatever else was going on once there's actually somewhere to go.
        brain.distract(Distraction.FORCE)
        fixation = brain.fixate(WalkOutOfWaterFixation(capabilities, tracker))
        return ObjectiveStatus.FIXATED
    }

    /**
     * Nearby positions matching [accepts], closest first. Only positions the entity could be in are considered;
     * whether they can be reached is left to the pathfinder.
     */
    private fun findExits(
        entity: PathfinderMob,
        capabilities: MovementCapabilities,
        accepts: (Occupancy, FluidKind) -> Boolean
    ): Set<BlockPos> {
        val level = entity.level()
        val origin = entity.blockPosition()
        val exits = mutableListOf<BlockPos>()
        // Hardly anything below is useful: the way out of water is up the bank, not into a cave under the bed.
        for (pos in BlockPos.betweenClosed(
            origin.offset(-horizontalRange, -MAX_EXIT_DEPTH_BELOW, -horizontalRange),
            origin.offset(horizontalRange, verticalRange, horizontalRange)
        )) {
            val occupancy = Occupancy.at(capabilities, entity.eyeHeight, pos, level::getBlockState) ?: continue
            if (occupancy.isStandable && accepts(occupancy, FluidKind.of(level.getFluidState(pos)))) {
                exits.add(pos.immutable())
            }
        }
        // Closest horizontally so that exits straight overhead (unreachable cliff tops) don't crowd out the shore.
        return exits
            .sortedBy { (it.x - origin.x) * (it.x - origin.x) + (it.z - origin.z) * (it.z - origin.z) }
            .take(maxExits)
            .toSet()
    }

    class WalkOutOfWaterFixation(
        private val capabilities: MovementCapabilities,
        override val movementTracker: MovementTracker
    ) : EntityFixation, MovementTrackerFixation {
        override fun getDescription(entity: LivingEntity) = "Escaping deadly fluid"

        override fun canDistract(distraction: Distraction, entity: LivingEntity): Boolean {
            // Nothing is more important than surviving, but once out of danger it's fair game.
            return deadlyFluid(entity, capabilities) == FluidKind.NONE
        }

        override fun onDistract(distraction: Distraction, entity: LivingEntity) {
            movementTracker.stop()
        }

        override fun tick(entity: LivingEntity, brain: BiggerBrain): Boolean {
            // Keeps going after surfacing, but by then canDistract lets anything more interesting take over.
            return tickMovementFixation() == MovementStatus.PENDING
        }
    }

    class Config : ObjectiveConfig {
        val horizontalRange: ExpressionOrEntityVariable = Either.left("12".asExpression())
        val verticalRange: ExpressionOrEntityVariable = Either.left("12".asExpression())
        val maxExits: ExpressionOrEntityVariable = Either.left("48".asExpression())
        val speedMultiplier: ExpressionOrEntityVariable = Either.left("0.4".asExpression())
        val maxSearches: ExpressionOrEntityVariable = Either.left("3".asExpression())

        override fun getVariables(entity: LivingEntity, behaviourConfigurationContext: BehaviourConfigurationContext) =
            listOf(horizontalRange, verticalRange, maxExits, speedMultiplier, maxSearches).asVariables()

        override fun create(entity: LivingEntity, behaviourConfigurationContext: BehaviourConfigurationContext): Objective {
            val runtime = behaviourConfigurationContext.runtime
            return WalkOutOfWater(
                horizontalRange = horizontalRange.resolveInt(runtime),
                verticalRange = verticalRange.resolveInt(runtime),
                maxExits = maxExits.resolveInt(runtime),
                speedMultiplier = speedMultiplier.resolveFloat(runtime),
                maxSearches = maxSearches.resolveInt(runtime)
            )
        }
    }

    companion object {
        const val MAX_EXIT_DEPTH_BELOW = 2

        /** Whether the entity's head is in a fluid it can neither swim nor breathe in. */
        fun isDrowningIn(capabilities: MovementCapabilities, headFluid: FluidKind): Boolean {
            return headFluid != FluidKind.NONE && !capabilities.canSwimIn(headFluid) && !capabilities.canBreatheIn(headFluid)
        }

        /** The fluid that's currently killing the entity, or [FluidKind.NONE]. */
        fun deadlyFluid(entity: LivingEntity, capabilities: MovementCapabilities): FluidKind {
            val headFluid = FluidKind.atEyes(entity)
            return when {
                isDrowningIn(capabilities, headFluid) -> headFluid
                entity.isInLava && capabilities.isHarmedBy(FluidKind.LAVA) -> FluidKind.LAVA
                else -> FluidKind.NONE
            }
        }
    }
}
