/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.biggerbrain.objective

import com.bedrockk.molang.Expression
import com.cobblemon.mod.common.CobblemonBlocks
import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.ai.BehaviourConfigurationContext
import com.cobblemon.mod.common.api.ai.ExpressionOrEntityVariable
import com.cobblemon.mod.common.api.ai.asSimplifiedExpression
import com.cobblemon.mod.common.api.ai.asVariables
import com.cobblemon.mod.common.api.ai.biggerbrain.BiggerBrain
import com.cobblemon.mod.common.api.ai.biggerbrain.LookingTracker
import com.cobblemon.mod.common.api.ai.biggerbrain.RegularMovementTracker
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.Distraction
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.DistractionType
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.comfortDistraction
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.idleBoredomDistraction
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.EntityFixation
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.LookAndMoveFixation
import com.cobblemon.mod.common.api.ai.config.objective.ObjectiveConfig
import com.cobblemon.mod.common.api.ai.config.task.PollinateFlowerTaskConfig
import com.cobblemon.mod.common.api.ai.resolveInt
import com.cobblemon.mod.common.block.SaccharineLeafBlock
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.asExpression
import com.cobblemon.mod.common.util.distanceTo
import com.cobblemon.mod.common.util.getMemorySafely
import com.cobblemon.mod.common.util.mainThreadRuntime
import com.cobblemon.mod.common.util.resolveFloat
import com.mojang.datafixers.util.Either
import net.minecraft.core.BlockPos
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.memory.MemoryStatus
import net.minecraft.world.entity.ai.memory.WalkTarget
import net.minecraft.world.level.block.BeehiveBlock
import net.minecraft.world.level.block.entity.BeehiveBlockEntity
import net.minecraft.world.phys.Vec3

/**
 * Mimics the vanilla bee behavior, with added interactions with Saccharine leaves.
 * Only wild bees are allowed to enter hives because of dupe risks with pasture blocks.
 *
 * @author JazzMcNade
 * @since February 23th, 2026
 */
class GatherNectar(
    val ticksBetweenAttempts: Int = 60,
    val maxDistance: Expression = "14.0".asExpression(),
    val speedMultiplier: Expression = "0.2".asExpression()
) : Objective {
    var workerFixation: EntityFixation? = null
    var shelterFixation: EntityFixation? = null
    var remainingCooldownTicks = ticksBetweenAttempts

    override fun getDescription(entity: LivingEntity) = "Worker Bee"
    override fun perform(
        entity: LivingEntity,
        brain: BiggerBrain,
        activeObjectiveTags: MutableSet<String>
    ): ObjectiveStatus {
        if (entity !is PokemonEntity) {
            return ObjectiveStatus.INVALID
        } else if (brain.isFixation(shelterFixation)) {
            // Never preempt the shelter fixation
            remainingCooldownTicks = ticksBetweenAttempts
            return ObjectiveStatus.FIXATED
        } else if (!entity.biggerBrain.hasMemoryValue(CobblemonMemories.HIVE_LOCATION) && !entity.biggerBrain.hasMemoryValue(CobblemonMemories.NEARBY_SACC_LEAVES)) {
            return ObjectiveStatus.INACTIVE
        }

        val world = entity.level()

        if ((world.isRaining || world.isNight) && brain.distract(comfortDistraction)) {
            // Return to hive or do nothing
            if (entity.pokemon.isWild()) {
                // Only wilds can path to hives
                val hivePos = entity.biggerBrain.getMemory(CobblemonMemories.HIVE_LOCATION)
                if (hivePos != null && wantsToEnterHive(entity)) {
                    // Deliver to hive
                    if (!tryEnterHive(entity)) { // Attempt to enter the hive, if we fail, probably too far away
                        val hiveEntrance = getHiveEntrance(hivePos, entity)
                        if (hiveEntrance != null) {
                            // Move to hive
                            shelterFixation = tryMovementFixation(entity, hiveEntrance, "Sheltering in Hive", ::onHiveMovementComplete)
                        } else {
                            // Blacklist the hive
                            dropAndBlackListHive(entity)
                        }

                    } // no else the entity is gone and is stored in the hive
                }
            } else {
                return ObjectiveStatus.INACTIVE
            }

        } else if (brain.isFixation(workerFixation)) {
            // Already fixated on the work
            remainingCooldownTicks = ticksBetweenAttempts
            return ObjectiveStatus.FIXATED
        } else {
            // Perform nectar gathering/delivery
            if (!brain.distract(idleBoredomDistraction)) {
                return ObjectiveStatus.BLOCKED
            }
            if (entity.biggerBrain.hasMemoryValue(CobblemonMemories.HAS_NECTAR) ) {
                // Deliver nectar to hive or Sacc leaves
                val hivePos = entity.biggerBrain.getMemory(CobblemonMemories.HIVE_LOCATION)
                if (hivePos != null && wantsToEnterHive(entity) && entity.pokemon.isWild()) {
                    // Deliver to hive
                    if (!tryEnterHive(entity)) { // Attempt to enter the hive, if we fail, probably too far away
                        val hiveEntrance = getHiveEntrance(hivePos, entity)
                        if (hiveEntrance != null) {
                            // Move to hive
                            workerFixation = tryMovementFixation(entity, hiveEntrance, "Delivering nectar to hive", ::onHiveMovementComplete)
                        } else {
                            // Blacklist the hive
                            dropAndBlackListHive(entity)
                        }

                    } // no else the entity is gone and is stored in the hive
                } else {
                    // Deliver to Saccharine Leaves
                    val sacLeafPos : BlockPos = entity.biggerBrain.getMemory(CobblemonMemories.NEARBY_SACC_LEAVES)
                        ?: return ObjectiveStatus.INACTIVE
                    if (entity.blockPosition() != sacLeafPos) {
                        workerFixation = tryMovementFixation(entity, sacLeafPos, "Delivering nectar to Saccharine Leaves")
                        return ObjectiveStatus.ACTIVE
                    } else {
                        // Do nectar deposit fixation
                        workerFixation = brain.fixate(DepositNectarInSaccharineLeavesFixation(entity))
                        return ObjectiveStatus.ACTIVE
                    }
                }

            } else {
                // Gather Nectar
                val flowerPos =
                    entity.biggerBrain.getMemory(CobblemonMemories.NEARBY_FLOWER) ?: return ObjectiveStatus.INACTIVE
                val distance = entity.distanceTo(flowerPos)
                val maxDistance = mainThreadRuntime.resolveFloat(maxDistance)
                if (distance < 1.0) {
                    // Pollinate/Gather nectar from the flower
                    if (workerFixation !is PollinateFlowerFixation && brain.distract(idleBoredomDistraction)) {
                        workerFixation = brain.fixate(PollinateFlowerFixation(entity))
                        return ObjectiveStatus.FIXATED
                    }

                } else if (distance < maxDistance) {
                    // Move to the flower
                    if (remainingCooldownTicks <= 0 && brain.distract(idleBoredomDistraction) && !brain.hasMemoryValue(
                            CobblemonMemories.PATH_TO_NEARBY_FLOWER_COOLDOWN
                        )
                    ) {
                        remainingCooldownTicks = ticksBetweenAttempts
                        workerFixation = tryMovementFixation(entity, flowerPos, "Moving to flower")
                    }
                }
            }
        }

        if (remainingCooldownTicks > 0) {
            remainingCooldownTicks--
        }

        return ObjectiveStatus.ACTIVE
    }



    /*
     * This Fixation has movement, but it doesn't utilize pathfinding, and it's more about flavor than accomplishing a goal
     *
     *
     */
    class PollinateFlowerFixation(
        entity: PathfinderMob,
    ) : EntityFixation {
        var successfulPollinationTicks = 0
        var lastSoundPlayedTick = 0
        var hoverPos: Vec3? = null
        override fun getDescription(entity: LivingEntity) = "Pollinating Flower"
        override fun canDistract(distraction: Distraction, entity: LivingEntity): Boolean {
            return distraction.isDamage || distraction.isForce || distraction.isComfort || entity.brain.hasMemoryValue(
                CobblemonMemories.HAS_NECTAR
            )
        }

        override fun onDistract(distraction: Distraction, entity: LivingEntity) {
            hoverPos = null
            if (successfulPollinationTicks > REQUIRED_SUCCESSFUL_POLLINATION_TICKS) {
                entity.brain.setMemory(CobblemonMemories.HAS_NECTAR, true)
            }
            successfulPollinationTicks = 0
            entity.brain.setMemoryWithExpiry(
                CobblemonMemories.PATH_TO_NEARBY_FLOWER_COOLDOWN, true,
                PollinateFlowerTaskConfig.Companion.PATH_TO_FLOWER_COOLDOWN_TICKS
            )

        }

        override fun tick(entity: LivingEntity, brain: BiggerBrain): Boolean {
            if (entity !is PokemonEntity) return false
            val flowerPos = entity.brain.getMemorySafely(CobblemonMemories.NEARBY_FLOWER).orElse(null)

            if (!(flowerPos != null && Vec3.atCenterOf(flowerPos).distanceTo(entity.position()) <= 1.0)) {
                onStop(entity)
                return false
            }
            ++successfulPollinationTicks

            if (successfulPollinationTicks > REQUIRED_SUCCESSFUL_POLLINATION_TICKS) {
                onStop(entity)
                return false
            }

            if (entity.random.nextFloat() < 0.05f && successfulPollinationTicks > this.lastSoundPlayedTick + 60) {
                this.lastSoundPlayedTick = successfulPollinationTicks
                entity.playSound(SoundEvents.BEE_POLLINATE, 1.0f, 1.0f)
            }


            val bl = hoverPos == null || entity.position().distanceTo(hoverPos!!) <= 0.1
            val bl3 = entity.random.nextInt(40) == 0
            if (bl && bl3) {
                // mimics the wriggling over a flower the vanilla bees do
                if (hoverPos == null) {
                    hoverPos = flowerPos.bottomCenter.add(0.0, 0.4, 0.0)
                }

                hoverPos = flowerPos.bottomCenter.add(
                    Vec3(
                        (entity.random.nextDouble() * 2.0 - 1.0) * (1.0 / 5.0),
                        0.3 + (entity.random.nextDouble() * 2.0 - 1.0) * (1.0 / 7.0),
                        (entity.random.nextDouble() * 2.0 - 1.0) * (1.0 / 5.0)
                    )
                )
                entity.getMoveControl()
                    .setWantedPosition(hoverPos!!.x(), hoverPos!!.y(), hoverPos!!.z(), 0.35)
                entity.lookControl.setLookAt(flowerPos.bottomCenter.add(0.0, 0.5, 0.0))
            } else if (!bl) {
                entity.getMoveControl()
                    .setWantedPosition(hoverPos!!.x(), hoverPos!!.y(), hoverPos!!.z(), 0.35)
            }
            entity.lookControl.setLookAt(flowerPos.bottomCenter.add(0.0, 0.5, 0.0))
            return true
        }

        fun onStop(entity: LivingEntity) {
            hoverPos = null
            if (successfulPollinationTicks > REQUIRED_SUCCESSFUL_POLLINATION_TICKS) {
                entity.brain.setMemory(CobblemonMemories.HAS_NECTAR, true)
            }
            successfulPollinationTicks = 0
            entity.brain.setMemoryWithExpiry(
                CobblemonMemories.PATH_TO_NEARBY_FLOWER_COOLDOWN, true,
                PATH_TO_FLOWER_COOLDOWN_TICKS
            )

        }
    }

    class DepositNectarInSaccharineLeavesFixation(
        entity: PathfinderMob,
    ) : EntityFixation {
        var successfulPollinationTicks = 0
        var lastSoundPlayedTick = 0
        var hoverPos: Vec3? = null
        override fun getDescription(entity: LivingEntity) = "Pollinating Flower"
        override fun canDistract(distraction: Distraction, entity: LivingEntity): Boolean {
            return distraction.isDamage || distraction.isForce || distraction.isComfort || entity.brain.hasMemoryValue(
                CobblemonMemories.HAS_NECTAR
            )
        }

        override fun onDistract(distraction: Distraction, entity: LivingEntity) {
            hoverPos = null
            if (successfulPollinationTicks > REQUIRED_SUCCESSFUL_POLLINATION_TICKS) {
                entity.brain.setMemory(CobblemonMemories.HAS_NECTAR, true)
            }
            successfulPollinationTicks = 0
            entity.brain.setMemoryWithExpiry(
                CobblemonMemories.PATH_TO_NEARBY_FLOWER_COOLDOWN, true,
                PollinateFlowerTaskConfig.Companion.PATH_TO_FLOWER_COOLDOWN_TICKS
            )

        }

        override fun tick(entity: LivingEntity, brain: BiggerBrain): Boolean {
            if (entity !is PokemonEntity) return false
            ++successfulPollinationTicks

            if (successfulPollinationTicks > REQUIRED_SUCCESSFUL_POLLINATION_TICKS) {
                val blockPos = brain.getMemory(CobblemonMemories.NEARBY_SACC_LEAVES)
                blockPos?.let {
                    val world = entity.level()
                    val blockState = world.getBlockState(blockPos)
                    if (blockState.block == CobblemonBlocks.SACCHARINE_LEAVES) {
                        val saccAge = blockState.getValue( SaccharineLeafBlock.AGE)
                        if (saccAge < SaccharineLeafBlock.MAX_AGE) {
                            brain.eraseMemory(CobblemonMemories.HAS_NECTAR)
                            val newBlockState = blockState.setValue(SaccharineLeafBlock.AGE,  saccAge + 1)
                            world.setBlock(blockPos, newBlockState, 3)
                        }
                    }
                }
                return false
            }

            if (entity.random.nextFloat() < 0.05f && successfulPollinationTicks > this.lastSoundPlayedTick + 60) {
                this.lastSoundPlayedTick = successfulPollinationTicks
                entity.playSound(SoundEvents.BEEHIVE_DRIP, 1.0f, 1.0f)
            }
            val leavesPos = brain.getMemory(CobblemonMemories.NEARBY_SACC_LEAVES) ?: return false

            val bl = hoverPos == null || entity.position().distanceTo(hoverPos!!) <= 0.1
            val bl3 = entity.random.nextInt(40) == 0
            if (bl && bl3) {
                // mimics the wriggling over a flower the vanilla bees do
                if (hoverPos == null) {
                    hoverPos = leavesPos.bottomCenter.add(0.0, 0.4, 0.0)
                }
                hoverPos = leavesPos.bottomCenter.add(Vec3((entity.random.nextDouble() * 2.0 - 1.0) * (1.0/5.0), 0.3 + (entity.random.nextDouble() * 2.0 - 1.0) * (1.0/7.0), (entity.random.nextDouble() * 2.0 - 1.0) * (1.0/5.0)))
                entity.getMoveControl()
                    .setWantedPosition(hoverPos!!.x(), hoverPos!!.y(), hoverPos!!.z(), 0.35)
                entity.lookControl.setLookAt(leavesPos.bottomCenter.add(0.0,0.5,0.0))
            } else if (!bl) {
                entity.getMoveControl()
                    .setWantedPosition(hoverPos!!.x(), hoverPos!!.y(), hoverPos!!.z(), 0.35)
            }
            return true
        }
    }

    class Config : ObjectiveConfig {
        val ticksBetweenAttempts: ExpressionOrEntityVariable = Either.left("60".asExpression())
        val maxDistance: ExpressionOrEntityVariable = Either.left("10.0".asExpression())
        val speedMultiplier: ExpressionOrEntityVariable = Either.left("0.4".asExpression())

        override fun getVariables(
            entity: LivingEntity,
            behaviourConfigurationContext: BehaviourConfigurationContext
        ) = listOf(
            ticksBetweenAttempts,
            maxDistance,
            speedMultiplier
        ).asVariables()

        override fun create(
            entity: LivingEntity,
            behaviourConfigurationContext: BehaviourConfigurationContext
        ): Objective {
            val ticksBetweenAttempts = ticksBetweenAttempts.resolveInt(behaviourConfigurationContext.runtime)
            return GatherNectar(
                ticksBetweenAttempts = ticksBetweenAttempts,
                maxDistance = maxDistance.asSimplifiedExpression(entity),
                speedMultiplier = speedMultiplier.asSimplifiedExpression(entity)
            )
        }
    }

    fun onHiveMovementComplete(entity: LivingEntity, brain: BiggerBrain) {
        if (entity !is PokemonEntity) return
        if(!tryEnterHive(entity)) {
            dropAndBlackListHive(entity)
        }
    }

    fun tryMovementFixation(
        entity: PokemonEntity,
        blockPos: BlockPos,
        description : String,
        onMovementComplete : ((entity: LivingEntity, brain: BiggerBrain) -> Unit)? = null,
    ) : EntityFixation? {
        val speedMultiplier = mainThreadRuntime.resolveFloat(speedMultiplier)

        val movementTracker = RegularMovementTracker.attempt(
            entity = entity,
            walkTarget = WalkTarget(
                blockPos,
                speedMultiplier,
                0
            )
        )
        if (movementTracker != null) {
            val fixation = entity.biggerBrain.fixate(
                LookAndMoveFixation(
                    distractionTypes = setOf(
                        DistractionType.DAMAGE,
                        DistractionType.COMFORT,
                        DistractionType.FORCE
                    ),
                    lookingTracker = LookingTracker.fromNavigationPathOrWalkTarget(
                        entity,
                        movementTracker.walkTarget
                    ),
                    movementTracker = movementTracker,
                    description = description,
                    onMovementComplete = onMovementComplete
                )
            )
            return fixation
        }
        return null
    }

    fun getHiveEntrance(hiveLocation: BlockPos, entity: LivingEntity) : BlockPos? {
        val sidesByClosest = listOf(
            hiveLocation.north(),
            hiveLocation.south(),
            hiveLocation.east(),
            hiveLocation.west()
        ).sortedBy { it.distSqr(entity.blockPosition()) }

        val world = entity.level()
        var openSide = BlockPos.ZERO
        for (side in sidesByClosest) {
            if (world.getBlockState(side).isAir) {
                openSide = side
                break
            }
        }
        if (openSide == BlockPos.ZERO) {
            if (world.getBlockState(hiveLocation.above()).isAir) {
                // If the above position is also air, we can use it
                openSide = hiveLocation.above() // Fallback to above if no open sides found
            } else if (world.getBlockState(hiveLocation.below()).isAir) {
                // If the below position is also air, we can use it
                openSide = hiveLocation.below() // Fallback to below if no open sides found
            } else {
                // If no open sides or above/below positions are found, we cannot proceed
                return null
            }
        }
        return openSide
    }

    fun tryEnterHive(entity: LivingEntity): Boolean {
        if (entity !is PokemonEntity || !wantsToEnterHive(entity)) {
            return false
        }

        val hiveLocation = entity.biggerBrain.getMemory(CobblemonMemories.HIVE_LOCATION) ?: return false
        val targetVec = Vec3.atCenterOf(hiveLocation)

        // if we are not close to it then end early
        if (entity.distanceToSqr(targetVec) >= 1.4) {
            return false
        }

        val state = entity.level().getBlockState(hiveLocation)
        val block = state.block
        if (block is BeehiveBlock) {
            val blockEntity = entity.level().getBlockEntity(hiveLocation)
            if (blockEntity !is BeehiveBlockEntity) return false
            if (blockEntity.isFull) {
                // Erase Hive memory and give the bee a moment to locate a new hive
                dropAndBlackListHive(entity)
            } else {
                blockEntity.addOccupant(entity)
                return true
            }
        }
        return false
    }

    fun wantsToEnterHive(entity: LivingEntity): Boolean {
        val result = !entity.brain.checkMemory(CobblemonMemories.HIVE_COOLDOWN, MemoryStatus.VALUE_PRESENT)
                && (entity.level().isRaining || entity.level().isNight || entity.brain.getMemory(CobblemonMemories.HAS_NECTAR)
            .orElse(false))
        if (result) {
            val blockPos = entity.brain.getMemorySafely(CobblemonMemories.HIVE_LOCATION).orElse(null)
            val blockEntity = entity.level().getBlockEntity(blockPos)
            return blockEntity is BeehiveBlockEntity && !blockEntity.isFireNearby
        }
        return false
    }

    fun dropAndBlackListHive(entity: PokemonEntity) {
        val hivePos = entity.biggerBrain.getMemory(CobblemonMemories.HIVE_LOCATION)
        if (hivePos == null) return
        val blackList : MutableList<BlockPos> = entity.biggerBrain.getMemory(CobblemonMemories.HIVE_BLACKLIST)?.toMutableList() ?: emptyList<BlockPos>().toMutableList()

        blackList.add(hivePos)
        if (blackList.count() > 3) {
            blackList.removeFirst()
        }
        entity.brain.eraseMemory(CobblemonMemories.HIVE_LOCATION)
        entity.brain.setMemory(CobblemonMemories.HIVE_BLACKLIST, blackList)

    }

    private fun pathfindDirectlyTowards(pos: BlockPos, entity: LivingEntity): Boolean {
        if (entity !is PathfinderMob) return false
        val nav = entity.navigation
        val path = nav. createPath(pos, 0)
        return path != null && path.canReach()
    }


    companion object {
        const val POLLINATE = "pollinate"
        const val PATH_TO_FLOWER_COOLDOWN_TICKS: Long = 200
        const val HAS_NECTAR_ASPECT = "has_nectar"
        const val MIN_DURATION: Int = 300
        const val MAX_DURATION: Int = 400
        const val REQUIRED_SUCCESSFUL_POLLINATION_TICKS = 250
    }
}