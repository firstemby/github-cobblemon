/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.config.task

import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.ai.BehaviourConfigurationContext
import com.cobblemon.mod.common.api.ai.ExpressionOrEntityVariable
import com.cobblemon.mod.common.api.ai.asVariables
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMostSpecificMoLangValue
import com.cobblemon.mod.common.entity.ai.CobblemonWalkTarget
import com.cobblemon.mod.common.util.asExpression
import com.cobblemon.mod.common.util.resolveFloat
import com.cobblemon.mod.common.util.withQueryValue
import com.mojang.datafixers.util.Either
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.behavior.BehaviorControl
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder
import net.minecraft.world.entity.ai.behavior.declarative.Trigger
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.util.LandRandomPos
import net.minecraft.world.level.pathfinder.PathType

class WanderTaskConfig : SingleTaskConfig {
    companion object {
        const val WANDER = "wander" // Category
    }

    val condition = booleanVariable(WANDER, "wanders", true).asExpressible()
    val wanderChance = numberVariable(WANDER, "wander_chance", 1/(20 * 6F)).asExpressible()
    val horizontalRange = numberVariable(WANDER, "horizontal_wander_range", 10).asExpressible()
    val verticalRange = numberVariable(WANDER, "vertical_wander_range", 5).asExpressible()
    val speedMultiplier = numberVariable(SharedEntityVariables.MOVEMENT_CATEGORY, SharedEntityVariables.WALK_SPEED, 0.35).asExpressible()
    val avoidTargetingAir: ExpressionOrEntityVariable = Either.left("true".asExpression()) // Whether to avoid air blocks when wandering
    val minimumHeight: ExpressionOrEntityVariable = Either.left("0".asExpression()) // Height off the ground
    val maximumHeight: ExpressionOrEntityVariable = Either.left("-1".asExpression()) // Height off the ground

    override fun getVariables(entity: LivingEntity) = listOf(
        condition,
        wanderChance,
        horizontalRange,
        verticalRange,
        speedMultiplier,
        avoidTargetingAir,
        minimumHeight,
        maximumHeight
    ).asVariables()

    private fun applyHeightConstraints(
        pos: BlockPos,
        minimumHeight: Int,
        maximumHeight: Int,
        world: ServerLevel
    ): BlockPos {
        if (minimumHeight <= 0 && maximumHeight == -1) {
            return pos // It ain't a hoverer
        }

        val block = world.getBlockState(pos)
        val altitude = if (!block.isAir) {
            0
        } else {
            (1..64).firstOrNull {
                val newPos = pos.below(it)
                return@firstOrNull !world.getBlockState(newPos).isAir
            } ?: Int.MAX_VALUE
        }

        if (altitude > maximumHeight && maximumHeight >= 0) {
            val excess = altitude - maximumHeight
            val excessFromMinimum = altitude - minimumHeight
            val correction = world.random.nextIntBetweenInclusive(excess, excessFromMinimum)
            return pos.atY(pos.y - correction)
        } else if (altitude < minimumHeight && minimumHeight >= 0) {
            val deficit = minimumHeight - altitude
            val deficitFromMaximum = maximumHeight - altitude
            val correction = world.random.nextIntBetweenInclusive(deficit, deficitFromMaximum)
            return pos.atY(pos.y + correction)
        } else {
            return pos
        }
    }

    override fun createTask(
        entity: LivingEntity,
        behaviourConfigurationContext: BehaviourConfigurationContext
    ): BehaviorControl<in LivingEntity>? {
        runtime.withQueryValue("entity", entity.asMostSpecificMoLangValue())
        if (!condition.resolveBoolean()) return null

        val wanderChanceExpression = wanderChance.asSimplifiedExpression(entity)

        behaviourConfigurationContext.addMemories(
            MemoryModuleType.WALK_TARGET,
            MemoryModuleType.LOOK_TARGET,
            CobblemonMemories.PATH_COOLDOWN
        )

        return BehaviorBuilder.create {
            it.group(
                it.absent(MemoryModuleType.WALK_TARGET),
                it.registered(MemoryModuleType.LOOK_TARGET),
                it.absent(CobblemonMemories.PATH_COOLDOWN)
            ).apply(it) { walkTarget, lookTarget, pathCooldown ->
                Trigger { world, entity, time ->
                    if (entity !is PathfinderMob || entity.isUnderWater) {
                        return@Trigger false
                    }

                    runtime.withQueryValue("entity", entity.asMostSpecificMoLangValue())
                    val wanderChance = runtime.resolveFloat(wanderChanceExpression)
                    if (wanderChance <= 0 || world.random.nextFloat() > wanderChance) {
                        return@Trigger false
                    }

                    pathCooldown.setWithExpiry(true, 40L)
                    val avoidsTargetingAir = avoidTargetingAir.resolveBoolean()

//                    val targetVec = getLandTarget(entity) ?: return@Trigger true
                    val targetVec = LandRandomPos.getPos(
                        entity,
                        horizontalRange.resolveInt(),
                        verticalRange.resolveInt()
                    ) ?: return@Trigger false

                    val minimumHeight = minimumHeight.resolveInt()
                    val maximumHeight = maximumHeight.resolveInt()

                    val pos = applyHeightConstraints(
                        pos = BlockPos.containing(targetVec),
                        minimumHeight = minimumHeight,
                        maximumHeight = maximumHeight,
                        world = world
                    )
                    walkTarget.set(
                        CobblemonWalkTarget(
                            pos = pos,
                            speedModifier = speedMultiplier.resolveFloat(),
                            completionRange = 0,
                            nodeTypeFilter = { nodeType -> nodeType !in listOf(PathType.WATER, PathType.WATER_BORDER) },
                            destinationNodeTypeFilter = { nodeType -> !avoidsTargetingAir || nodeType !in listOf(PathType.OPEN) }
                        )
                    )
                    lookTarget.erase()
                    return@Trigger true
                }
            }
        }
    }

    /*
     * This method is from the old wander goal. It is very verbose but reliably finds decent land wander targets. Its only advantage over
     * the LandRandomPos method (with our CobblemonWalkTarget) is that it doesn't lead entities in the direction of water.
     */
//    fun getLandTarget(entity: PathfinderMob): Vec3? {
//        val roamDistanceCondition: (BlockPos) -> Boolean = if (entity is PokemonEntity) ({ entity.tethering?.canRoamTo(it) != false }) else ({ true })
//        val iterable: Iterable<BlockPos> = BlockPos.randomBetweenClosed(entity.random, 64, entity.blockX - 10, entity.blockY, entity.blockZ - 10, entity.blockX + 10, entity.blockY, entity.blockZ + 10)
//        val condition: (BlockState, BlockPos) -> Boolean = { _, pos -> entity.canFit(pos) && roamDistanceCondition(pos) }
//        val iterator = iterable.iterator()
//        position@
//        while (iterator.hasNext()) {
//            val pos = iterator.next().mutable()
//            var blockState = entity.level().getBlockState(pos)
//
//            val maxSteps = 16
//            var steps = 0
//            var good = false
//            if (!blockState.isSolid && !blockState.liquid()) {
//                pos.move(0, -1, 0)
//                var previousWasAir = true
//                while (steps++ < maxSteps && pos.y > entity.level().minBuildHeight) {
//                    if (pos.y <= entity.level().minBuildHeight) {
//                        continue@position
//                    }
//                    blockState = entity.level().getBlockState(pos)
//                    if (blockState.isSolid && !blockState.`is`(BlockTags.LEAVES) && previousWasAir) {
//                        pos.move(0, 1, 0)
//                        blockState = entity.level().getBlockState(pos)
//                        good = true
//                        break
//                    } else {
//                        previousWasAir = blockState.isAir
//                    }
//                    pos.move(0, -1, 0)
//                }
//            } else {
//                var previousWasSolid = blockState.isSolid && !blockState.`is`(BlockTags.LEAVES)
//                pos.move(0, 1, 0)
//                while (steps++ < maxSteps) {
//                    if (pos.y >= entity.level().maxBuildHeight) {
//                        continue@position
//                    }
//                    blockState = entity.level().getBlockState(pos)
//                    if (blockState.isAir && previousWasSolid) {
//                        good = true
//                        break
//                    }
//                    previousWasSolid = blockState.isSolid && !blockState.`is`(BlockTags.LEAVES)
//                    pos.move(0, 1, 0)
//                }
//            }
//
//            if (good && condition(blockState, pos)) {
//                return pos.toVec3d()
//            }
//        }
//
//        return null
//    }
}