/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.movement

import com.cobblemon.mod.common.entity.ai.OmniPathNavigation
import com.cobblemon.mod.common.pokemon.ai.OmniPathNodeMaker
import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity

/**
 * The rules for a single movement request: which occupancies the entity is willing to path through for this
 * particular reason, and what each one costs. A policy is attached to the path it produced by
 * [OmniPathNavigation], so recomputations of that path keep following the same rules.
 *
 * Positions in the open are only governed by a policy for their medium (walkable, open). Hazards such as fire, fences
 * and damaging blocks keep using the entity's regular pathfinding malus in [OmniPathNodeMaker]. Positions in a fluid
 * are always priced by the policy.
 *
 * Capabilities always win: an occupancy the body can't be in is never allowed, whatever the pricing says.
 *
 * @author Hiroku
 * @since September 30th, 2026
 */
class TraversalPolicy(
    val capabilities: MovementCapabilities,
    /** Shown in debugging. */
    val description: String,
    /** The cost of entering an occupancy in a fluid, or null if it's forbidden. */
    private val pricing: (Occupancy, FluidKind) -> Float?,
) {
    /** The cost of entering a node in the given occupancy, or null if this request won't go there. */
    fun cost(occupancy: Occupancy, fluid: FluidKind): Float? {
        if (!occupancy.isStandable || !capabilities.canOccupy(occupancy)) {
            return null
        }
        return pricing(occupancy, fluid)
    }

    fun allows(occupancy: Occupancy, fluid: FluidKind) = cost(occupancy, fluid) != null

    override fun toString() = "TraversalPolicy($description)"

    companion object {
        /** Path cost of something at the limit of what the entity will put up with. Comparable to the old fluid malus. */
        const val DISCOMFORT_COST = 16F
        /** Cost per node where the head is under a fluid the entity can't breathe in. Roughly "time spent drowning". */
        const val HELD_BREATH_COST = 4F
        /** Cost per node of being in lava without fire immunity. */
        const val BURNING_COST = 4F

        /**
         * The usual policy: occupancies cost their discomfort, and anything more uncomfortable than the intent tolerates
         * is forbidden. Whatever the entity is currently standing in is tolerated so that it can make its way out,
         * unless it's something it would never enter voluntarily (burning, holding its breath); that's for escapes.
         *
         * @param within If given, the movement is confined to occupancies matching it, e.g. a swimmer wandering within
         * water. Choosing that medium was already the decision, so the intent's tolerance doesn't apply within it.
         * Hazards (burning, sinking) stay forbidden.
         */
        fun forIntent(
            entity: LivingEntity,
            intent: MovementIntent,
            within: ((Occupancy, FluidKind) -> Boolean)? = null
        ): TraversalPolicy {
            val capabilities = MovementCapabilities.of(entity)
            val preferences = MediumPreferences.of(entity)
            val level = entity.level()
            // The same position the pathfinder starts from, so the exemption below covers the actual start node.
            val box = entity.boundingBox
            val pos = BlockPos(Mth.floor(box.minX), Mth.floor(box.minY + 0.5), Mth.floor(box.minZ))
            val currentOccupancy = Occupancy.at(capabilities, entity.eyeHeight, pos, level::getBlockState)
            val currentFluid = FluidKind.of(level.getFluidState(pos))
            return TraversalPolicy(capabilities, intent.name.lowercase()) { occupancy, fluid ->
                if (within != null && !within(occupancy, fluid)) {
                    return@TraversalPolicy null
                }
                val discomfort = preferences.discomfort(occupancy, fluid, capabilities) ?: return@TraversalPolicy null
                val tolerated = within != null || discomfort <= intent.tolerance ||
                        (occupancy == currentOccupancy && fluid == currentFluid)
                if (tolerated) discomfort * DISCOMFORT_COST else null
            }
        }

        /**
         * A policy for an entity trying to get out of a fluid that's killing it: one it can neither swim nor breathe in,
         * or lava without fire immunity. Being in that fluid is allowed but expensive, so the shortest way out wins.
         * Anything else is held to [MovementIntent.URGENT] so it doesn't escape into something worse.
         */
        fun escapeFluid(entity: LivingEntity, fluid: FluidKind): TraversalPolicy {
            val capabilities = MovementCapabilities.of(entity)
            val preferences = MediumPreferences.of(entity)
            return TraversalPolicy(capabilities, "escape ${fluid.name.lowercase()}") { occupancy, nodeFluid ->
                if (nodeFluid != fluid || fluid == FluidKind.NONE) {
                    val discomfort = preferences.discomfort(occupancy, nodeFluid, capabilities) ?: return@TraversalPolicy null
                    return@TraversalPolicy if (discomfort > MovementIntent.URGENT.tolerance) null else discomfort * DISCOMFORT_COST
                }
                when {
                    capabilities.isHarmedBy(fluid) -> BURNING_COST
                    occupancy == Occupancy.WADE -> 1F
                    occupancy == Occupancy.SWIM -> 2F
                    occupancy.isSubmerged -> HELD_BREATH_COST
                    else -> 0F
                }
            }
        }
    }
}
