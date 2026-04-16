/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.entity.ai

import com.cobblemon.mod.common.entity.OmniPathingEntity
import java.util.Objects
import java.util.function.Supplier
import java.util.function.ToDoubleFunction
import net.minecraft.core.BlockPos
import net.minecraft.tags.FluidTags
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.util.GoalUtils
import net.minecraft.world.entity.ai.util.RandomPos
import net.minecraft.world.phys.Vec3

/*
 * This is a rewrite of Minecraft's DefaultRandomPos tuned for
 * water and lava walkers.
 * The only real difference is movePosUpOutOfSolid(), which
 * moves the target pos out of liquids.
 */
class CobblemonRandomSurfacePos {
    companion object {
        fun getPos(mob: PathfinderMob, radius: Int, verticalRange: Int): Vec3? {
            Objects.requireNonNull(mob)
            return getPos(mob, radius, verticalRange) { pos ->
                if (pos != null) {
                    mob.getWalkTargetValue(pos).toDouble()
                } else {
                    0.0
                }
            }
        }

        fun getPos(mob: PathfinderMob, radius: Int, yRange: Int, toDoubleFunction: ToDoubleFunction<BlockPos?>): Vec3? {
            val restricted = GoalUtils.mobRestricted(mob, radius.toDouble())
            return RandomPos.generateRandomPos(
                Supplier {
                    val direction = RandomPos.generateRandomDirection(mob.random, radius, yRange)
                    val target = generateRandomPosTowardDirection(mob, radius, restricted, direction)
                    if (target == null) {
                        null
                    } else {
                        movePosUpOutOfSolid(mob, target)
                    }
                },
                ToDoubleFunction { pos -> toDoubleFunction.applyAsDouble(pos) }
            )
        }

        fun getPosTowards(mob: PathfinderMob, radius: Int, yRange: Int, vectorPosition: Vec3): Vec3? {
            val direction = vectorPosition.subtract(mob.x, mob.y, mob.z)
            val restricted = GoalUtils.mobRestricted(mob, radius.toDouble())
            return getPosInDirection(mob, radius, yRange, direction, restricted)
        }

        private fun getPosInDirection(
            mob: PathfinderMob,
            radius: Int,
            yRange: Int,
            vectorPosition: Vec3,
            shortCircuit: Boolean
        ): Vec3? {
            return RandomPos.generateRandomPos(
                mob,
                Supplier {
                    val direction = RandomPos.generateRandomDirectionWithinRadians(
                        mob.random,
                        0.0,
                        radius.toDouble(),
                        yRange,
                        0,
                        vectorPosition.x,
                        vectorPosition.z,
                        Math.PI / 2.0
                    ) ?: return@Supplier null
                    val target = generateRandomPosTowardDirection(mob, radius, shortCircuit, direction)
                    if (target == null) {
                        null
                    } else {
                        movePosUpOutOfSolid(mob, target)
                    }
                }
            )
        }

        fun generateRandomPosTowardDirection(
            mob: PathfinderMob,
            radius: Int,
            shortCircuit: Boolean,
            pos: BlockPos
        ): BlockPos? {
            val target = RandomPos.generateRandomPosTowardDirection(mob, radius.toDouble(), mob.random, pos)
            return if (!GoalUtils.isOutsideLimits(target, mob)
                && !GoalUtils.isRestricted(shortCircuit, mob, target)
                && !GoalUtils.isNotStable(mob.navigation, target)
            ) {
                target
            } else {
                null
            }
        }

        fun movePosUpOutOfSolid(mob: PathfinderMob, pos: BlockPos): BlockPos? {
            val moved = RandomPos.moveUpOutOfSolid(pos, mob.level().maxY.toInt()) { blockPos ->
                if (GoalUtils.isSolid(mob, blockPos)) {
                    true
                } else {
                    val fluidState = mob.level().getFluidState(blockPos)
                    !fluidState.isEmpty && (
                        mob !is OmniPathingEntity ||
                            (mob.canWalkOnWater() && fluidState.`is`(FluidTags.WATER)) ||
                            (mob.canWalkOnLava() && fluidState.`is`(FluidTags.LAVA))
                        )
                }
            }
            return if (!GoalUtils.hasMalus(mob, moved)) moved else null
        }
    }
}
