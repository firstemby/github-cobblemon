/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.movement

import com.cobblemon.mod.common.entity.OmniPathingEntity
import net.minecraft.tags.FluidTags
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Fluids

/** The fluids that matter for movement decisions. */
enum class FluidKind {
    NONE,
    WATER,
    LAVA;

    companion object {
        fun of(fluidState: FluidState): FluidKind = when {
            fluidState.isEmpty -> NONE
            fluidState.`is`(FluidTags.WATER) -> WATER
            fluidState.`is`(FluidTags.LAVA) -> LAVA
            else -> NONE
        }

        /** The fluid the entity's eyes are currently in, if any. */
        fun atEyes(entity: LivingEntity): FluidKind = when {
            entity.isEyeInFluid(FluidTags.WATER) -> WATER
            entity.isEyeInFluid(FluidTags.LAVA) -> LAVA
            else -> NONE
        }
    }
}

/**
 * A snapshot of what an entity's body is physically able to do. These are hard limits: no amount of
 * urgency lets an entity swim if it can't swim. Preferences and urgency live in [TraversalPolicy].
 *
 * Lava is viscous enough that every body can move through it freely; what differs is whether it burns.
 * Whether an entity actually likes being in lava (the species' canSwimInLava) is a preference, see [MediumPreferences].
 *
 * @author Hiroku
 * @since September 30th, 2026
 */
data class MovementCapabilities(
    val canWalk: Boolean = true,
    val canFly: Boolean = false,
    val canSwimInWater: Boolean = false,
    val fireImmune: Boolean = false,
    val canBreatheUnderwater: Boolean = false,
    val canBreatheUnderLava: Boolean = false,
    val canWalkOnWater: Boolean = false,
    val canWalkOnLava: Boolean = false,
) {
    fun canSwimIn(fluid: FluidKind) = when (fluid) {
        FluidKind.WATER -> canSwimInWater
        FluidKind.LAVA -> true
        FluidKind.NONE -> false
    }

    /** Whether being in the fluid hurts, regardless of breathing. */
    fun isHarmedBy(fluid: FluidKind) = fluid == FluidKind.LAVA && !fireImmune

    fun canBreatheIn(fluid: FluidKind) = when (fluid) {
        FluidKind.WATER -> canBreatheUnderwater
        FluidKind.LAVA -> canBreatheUnderLava
        FluidKind.NONE -> true
    }

    fun canWalkOn(fluid: FluidKind) = when (fluid) {
        FluidKind.WATER -> canWalkOnWater
        FluidKind.LAVA -> canWalkOnLava
        FluidKind.NONE -> false
    }

    /** Whether this body could ever end up in the given occupancy through its own movement. */
    fun canOccupy(occupancy: Occupancy) = when (occupancy) {
        Occupancy.WALK -> canWalk || canFly
        Occupancy.FLY -> canFly
        Occupancy.WADE, Occupancy.SEABED -> canWalk
        // Lava can always be swum in, and classification only produces swimming in water for water swimmers.
        Occupancy.SWIM, Occupancy.SWIM_SUBMERGED -> true
        Occupancy.SINK, Occupancy.FALL -> false
    }

    companion object {
        fun of(entity: LivingEntity): MovementCapabilities {
            if (entity !is OmniPathingEntity) {
                return MovementCapabilities(canBreatheUnderwater = entity.canBreatheUnderwater(), fireImmune = entity.fireImmune())
            }
            return MovementCapabilities(
                canWalk = entity.canWalk(),
                canFly = entity.canFly(),
                canSwimInWater = entity.canSwimInWater(),
                fireImmune = entity.fireImmune(),
                canBreatheUnderwater = entity.canSwimUnderFluid(Fluids.WATER.defaultFluidState()),
                canBreatheUnderLava = entity.canSwimUnderFluid(Fluids.LAVA.defaultFluidState()),
                canWalkOnWater = entity.canWalkOnWater(),
                canWalkOnLava = entity.canWalkOnLava(),
            )
        }
    }
}
