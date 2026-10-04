/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.movement

import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.world.level.block.state.BlockState

/**
 * How a specific entity would be occupying a position. The same block of water is [SWIM] for a swimmer,
 * [SEABED] for a non-swimmer standing on the bottom, and [SINK] for a non-swimmer with nothing below it.
 *
 * @author Hiroku
 * @since September 30th, 2026
 */
enum class Occupancy {
    /** Standing on something with no fluid around the feet. */
    WALK,
    /** Airborne, with the ability to fly. */
    FLY,
    /** Standing on the bottom of a fluid it can't swim in, with its head above the surface. */
    WADE,
    /** Standing on the bottom of a fluid it can't swim in, with its head under the surface. */
    SEABED,
    /** In a fluid it can't swim in with nothing to stand on. It can only go down from here. */
    SINK,
    /** Swimming with its head above the surface. */
    SWIM,
    /** Swimming with its head under the surface. */
    SWIM_SUBMERGED,
    /** In the air without the ability to fly, with nothing to stand on. */
    FALL;

    /** Whether an entity can come to rest here. [SINK] and [FALL] are only ever passed through on the way down. */
    val isStandable: Boolean
        get() = this != SINK && this != FALL

    /** Whether the head is under a fluid in this occupancy, which is where breath gets spent. */
    val isSubmerged: Boolean
        get() = this == SEABED || this == SWIM_SUBMERGED

    companion object {
        /**
         * Classifies an occupancy from already-resolved facts about a position.
         *
         * @param fluid The fluid at the entity's feet.
         * @param hasFloor Whether there is something to stand on: a solid block below, or a fluid surface the entity can walk on.
         * @param headFluid The fluid at the entity's eye level.
         */
        fun classify(caps: MovementCapabilities, fluid: FluidKind, hasFloor: Boolean, headFluid: FluidKind): Occupancy {
            val headUnder = headFluid != FluidKind.NONE
            return when {
                fluid == FluidKind.NONE -> when {
                    hasFloor -> WALK
                    caps.canFly -> FLY
                    else -> FALL
                }
                caps.canSwimIn(fluid) -> if (headUnder) SWIM_SUBMERGED else SWIM
                !hasFloor -> SINK
                headUnder -> SEABED
                else -> WADE
            }
        }

        /**
         * Classifies the occupancy at a position using the given block lookup, or returns null if the position
         * is obstructed by a solid block. This only looks at the feet column; hitbox clearance is the pathfinder's job.
         */
        fun at(caps: MovementCapabilities, eyeHeight: Float, pos: BlockPos, getState: (BlockPos) -> BlockState): Occupancy? {
            val state = getState(pos)
            if (state.isSolid) {
                return null
            }
            val fluid = FluidKind.of(state.fluidState)
            val below = getState(pos.below())
            val belowFluid = FluidKind.of(below.fluidState)
            val hasFloor = below.isSolid || (fluid == FluidKind.NONE && caps.canWalkOn(belowFluid))
            return classify(caps, fluid, hasFloor, fluidAtEyes(pos, eyeHeight, getState))
        }

        /**
         * The fluid the eyes would be in when standing at the position. Compares the eye height against the fluid's
         * surface rather than just the block, so a small entity can wade through a thin layer of flowing water.
         */
        private fun fluidAtEyes(pos: BlockPos, eyeHeight: Float, getState: (BlockPos) -> BlockState): FluidKind {
            val eyeOffset = Mth.floor(eyeHeight)
            val eyePos = pos.above(eyeOffset)
            val eyeFluidState = getState(eyePos).fluidState
            val eyeFluid = FluidKind.of(eyeFluidState)
            if (eyeFluid == FluidKind.NONE) {
                return FluidKind.NONE
            }
            // Fluid stacked on top fills the block, otherwise the surface is however high this fluid sits.
            val surface = if (FluidKind.of(getState(eyePos.above()).fluidState) == eyeFluid) 1F else eyeFluidState.ownHeight
            return if (eyeHeight - eyeOffset < surface) eyeFluid else FluidKind.NONE
        }
    }
}
