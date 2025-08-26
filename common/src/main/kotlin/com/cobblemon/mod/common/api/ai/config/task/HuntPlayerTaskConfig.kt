/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.config.task

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonFlows.runtime
import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.ai.BehaviourConfigurationContext
import com.cobblemon.mod.common.api.ai.asVariables
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMostSpecificMoLangValue
import com.cobblemon.mod.common.api.npc.configuration.MoLangConfigVariable
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.withQueryValue
import com.mojang.datafixers.kinds.IdF
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.behavior.BehaviorControl
import net.minecraft.world.entity.ai.behavior.BlockPosTracker
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder
import net.minecraft.world.entity.ai.behavior.declarative.Trigger
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.WalkTarget
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import java.util.UUID

/*class HuntPlayerTaskConfig : SingleTaskConfig {
    // todo make new variables
    companion object {
        const val BREED = "breed"
    }

    val condition = booleanVariable(BREED, "breeds", true).asExpressible()
    val speedMultiplier = numberVariable(BREED, "speed_multiplier", 0.6).asExpressible()

    override fun getVariables(entity: LivingEntity, behaviourConfigurationContext: BehaviourConfigurationContext) = listOf(
        condition,
        speedMultiplier
    ).asVariables()

    override fun createTask(
        entity: LivingEntity,
        brainConfigurationContext: BehaviourConfigurationContext
    ): BehaviorControl<in LivingEntity>? {
        runtime.withQueryValue("entity", entity.asMostSpecificMoLangValue())
        if (!condition.resolveBoolean() || entity !is PokemonEntity) return null

        return BehaviorBuilder.create {
            it.group(
                it.present(MemoryModuleType.LOOK_TARGET),
                it.absent(MemoryModuleType.WALK_TARGET),
                it.present(MemoryModuleType.NEAREST_PLAYERS), // this will be null most of the time
                it.present(MemoryModuleType.ANGRY_AT),
                it.registered(MemoryModuleType.ATTACK_TARGET),
                it.registered(MemoryModuleType.DISTURBANCE_LOCATION),
            ).apply(it) { lookTarget, walkTarget, nearestPlayers, angryAt, attackTarget, disturbanceLocation ->
                Trigger { world, entity, time ->
                    if (entity !is PathfinderMob || !entity.isAlive || entity !is PokemonEntity) return@Trigger false

                    runtime.withQueryValue("entity", entity.asMostSpecificMoLangValue())

                    val angryAtUuid = (angryAt.value() as? IdF<UUID?>)?.value() ?: return@Trigger false
                    val disturbancePos = (disturbanceLocation.value() as? IdF<BlockPos>)?.value()

                    val players = (nearestPlayers.value() as? IdF<List<Player?>?>)?.value()
                    val targetPlayer = players?.find { it?.uuid == angryAtUuid }

                    if (targetPlayer != null) {
                        // If the angry player is nearby, set them as the attack target
                        entity.cry()
                        attackTarget.set(targetPlayer)
                        return@Trigger true
                    }

                    // If the angry player is not nearby, search around the Pokémon
                    val maxTicksToSearch = 200L
                    if (time > maxTicksToSearch) {
                        // Timeout after 200 ticks
                        return@Trigger false
                    }

                    // If no disturbance location or it is caused by a nearby player, skip
                    if (disturbancePos != null) {
                        val disturberCausingDisturbance = players?.any {
                            it?.position()?.distanceToSqr(Vec3.atCenterOf(disturbancePos))!! <= 4.0 * 4.0
                        }
                        disturberCausingDisturbance?.let { it1 ->
                            if (!it1) {
                                // Pathfind toward disturbance location
                                walkTarget.set(WalkTarget(Vec3.atCenterOf(disturbancePos), speedMultiplier.resolveFloat(), 1))
                                lookTarget.set(BlockPosTracker(Vec3.atCenterOf(disturbancePos)))
                                return@Trigger true
                            }
                        }
                    }

                    // If no valid disturbance location, pathfind randomly within proximity
                    val validPosition = findValidWalkablePosition(world, entity.position(), entity, 8)
                    if (validPosition == null) {
                        return@Trigger false // No valid position found
                    }

                    walkTarget.set(WalkTarget(validPosition, speedMultiplier.resolveFloat(), 1))
                    lookTarget.set(BlockPosTracker(validPosition.add(0.0, entity.eyeHeight.toDouble(), 0.0)))

                    return@Trigger true
                }
            }
        }
    }

    private fun findValidWalkablePosition(
        world: ServerLevel,
        center: Vec3,
        entity: PathfinderMob,
        radius: Int
    ): Vec3? {
        val random = world.random

        // Try a number of random positions within the radius
        repeat(20) { // Attempt up to 20 random positions
            val offsetX = random.nextDouble() * 2 * radius - radius
            val offsetY = random.nextDouble() * 2 * radius - radius
            val offsetZ = random.nextDouble() * 2 * radius - radius
            val candidate = center.add(offsetX, offsetY, offsetZ)

            // Convert to block position
            val blockPos = BlockPos(candidate.x.toInt(), candidate.y.toInt(), candidate.z.toInt())

            // Check if the position is walkable and not inside a solid block
            if (world.getBlockState(blockPos).isSolidRender(world, blockPos).not() &&
                entity.navigation.isStableDestination(blockPos)
            ) {
                return Vec3.atCenterOf(blockPos) // Return the valid position
            }
        }

        return null // No valid position found
    }
}*/
