package com.cobblemon.mod.common.entity.pokemon.ai.tasks

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.ai.BrainConfigurationContext
import com.cobblemon.mod.common.api.ai.asVariables
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMostSpecificMoLangValue
import com.cobblemon.mod.common.api.npc.configuration.MoLangConfigVariable
import com.cobblemon.mod.common.client.keybind.CobblemonBlockingKeyBinding
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.withQueryValue
import com.mojang.datafixers.kinds.IdF
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.behavior.BehaviorControl
import net.minecraft.world.entity.ai.behavior.BlockPosTracker
import net.minecraft.world.entity.ai.behavior.OneShot
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder
import net.minecraft.world.entity.ai.behavior.declarative.Trigger
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.WalkTarget
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3

object GuardNestTask {
    fun create(): OneShot<PokemonEntity> {
        return BehaviorBuilder.create {
            it.group(
                it.present(MemoryModuleType.LOOK_TARGET),
                it.absent(MemoryModuleType.WALK_TARGET),
                it.present(CobblemonMemories.PATERNAL_NEST_LOCATION),
                it.registered(CobblemonMemories.EGG_IN_NEST), // todo I want this to make it so the task only runs when the nest has the egg
                it.present(CobblemonMemories.EGG_LOCATION),
                it.absent(CobblemonMemories.EGG_THIEF),
                it.registered(MemoryModuleType.NEAREST_VISIBLE_PLAYER),
                it.registered(MemoryModuleType.ANGRY_AT),
                it.absent(MemoryModuleType.ATTACK_TARGET),
                it.registered(CobblemonMemories.IRRITATED_AT)
            ).apply(it) { lookTarget, walkTarget, paternalNest, eggInNest, eggLocation, eggThief, nearestVisiblePlayer, angryAt, attackTarget, irritatedAt ->
                Trigger { world, entity, time ->
                    if (entity !is PathfinderMob || !entity.isAlive || entity !is PokemonEntity) return@Trigger false

                    val eggInNest = entity.brain.getMemory(CobblemonMemories.EGG_IN_NEST).orElse(null)
                    if (eggInNest != null && eggInNest == false) {
                        return@Trigger false
                    }
                    //runtime.withQueryValue("entity", entity.asMostSpecificMoLangValue())

                    // Retrieve nest location and mate entity from memory
                    //val nestLocation = it.get(nestMemory)
                    val nestLocation: BlockPos = (paternalNest.value() as? IdF<BlockPos>)?.value() ?: return@Trigger false
                    val nestCenter = Vec3.atCenterOf(nestLocation)

                    // todo maybe cancel task if player is close to nest and set angry at task to that player
                    val nearestPlayerOptional = entity.brain.getMemory(MemoryModuleType.NEAREST_VISIBLE_PLAYER)
                    val player = nearestPlayerOptional.orElse(null) as? ServerPlayer
                    val irritatedAtTarget = entity.brain.getMemory(CobblemonMemories.IRRITATED_AT)

                    if (player != null) {
                        val playerDistanceSquared = player.position().distanceToSqr(nestCenter)

                        if (!irritatedAtTarget.isPresent && playerDistanceSquared <= 15 * 15) {
                            // Player is within 15 blocks, set angry and cry
                            entity.cry()

                            // set irritated at target to player near egg
                            irritatedAt.set(player.uuid)

                            // Display angry particles above parent
                            (world as net.minecraft.server.level.ServerLevel).sendParticles(
                                net.minecraft.core.particles.ParticleTypes.ANGRY_VILLAGER,
                                entity.getRandomX(1.0), // Particle X position with slight randomness
                                entity.getRandomY() + 0.5, // Slightly raised Y position
                                entity.getRandomZ(1.0), // Particle Z position with slight randomness
                                4 + kotlin.random.Random.nextInt(2), // Particle count
                                0.2 * entity.random.nextGaussian(), // X velocity with slight variance
                                0.1 * entity.random.nextGaussian(), // Y velocity with upwards variance
                                0.2 * entity.random.nextGaussian(), // Z velocity with slight variance
                                0.02 // Particle speed
                            )
                        }

                        if (playerDistanceSquared <= 8 * 8) {
                            // Player is within 8 blocks, attack
                            entity.cry()

                            // Display angry particles above parent
                            (world as net.minecraft.server.level.ServerLevel).sendParticles(
                                net.minecraft.core.particles.ParticleTypes.ANGRY_VILLAGER,
                                entity.getRandomX(1.0), // Particle X position with slight randomness
                                entity.getRandomY() + 0.5, // Slightly raised Y position
                                entity.getRandomZ(1.0), // Particle Z position with slight randomness
                                4 + kotlin.random.Random.nextInt(2), // Particle count
                                0.2 * entity.random.nextGaussian(), // X velocity with slight variance
                                0.1 * entity.random.nextGaussian(), // Y velocity with upwards variance
                                0.2 * entity.random.nextGaussian(), // Z velocity with slight variance
                                0.02 // Particle speed
                            )
                            angryAt.set(player.uuid)
                            attackTarget.set(player)
                            return@Trigger false // Cancel the task
                        }
                    }



                    // Scan for a valid walkable position within an 8-block sphere
                    val validPosition = findValidWalkablePosition(world, nestCenter, entity, 8)
                    if (validPosition == null) {
                        return@Trigger false // No valid position found
                    }

                    // Set the entity's walk and look targets
                    walkTarget.set(WalkTarget(validPosition, /*speedMultiplier.resolveFloat()*/ 1.0F, 1))
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
}
