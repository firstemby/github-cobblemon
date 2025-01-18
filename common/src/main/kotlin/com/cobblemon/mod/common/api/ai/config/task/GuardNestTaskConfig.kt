package com.cobblemon.mod.common.api.ai.config.task

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.ai.BrainConfigurationContext
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

class GuardNestTaskConfig : SingleTaskConfig {
    // todo make new variables
    companion object {
        const val BREED = "breed"
    }

    val condition = booleanVariable(BREED, "breeds", true).asExpressible()
    val speedMultiplier = numberVariable(BREED, "speed_multiplier", 0.6).asExpressible()

    override val variables: List<MoLangConfigVariable>
        get() = listOf(condition, speedMultiplier).asVariables()

    override fun createTask(
        entity: LivingEntity,
        brainConfigurationContext: BrainConfigurationContext
    ): BehaviorControl<in LivingEntity>? {
        runtime.withQueryValue("entity", entity.asMostSpecificMoLangValue())
        if (!condition.resolveBoolean() || entity !is PokemonEntity) return null

        return BehaviorBuilder.create {
            it.group(
                it.present(MemoryModuleType.LOOK_TARGET),
                it.absent(MemoryModuleType.WALK_TARGET),
                it.present(CobblemonMemories.PATERNAL_NEST_LOCATION),
                it.present(MemoryModuleType.NEAREST_VISIBLE_PLAYER), // if player is invisible they do not patrol
                it.absent(MemoryModuleType.ANGRY_AT),
                it.absent(MemoryModuleType.ATTACK_TARGET)
            ).apply(it) { lookTarget, walkTarget, paternalNest, nearestVisiblePlayer, angryAt, attackTarget ->
                Trigger { world, entity, time ->
                    if (entity !is PathfinderMob || !entity.isAlive || entity !is PokemonEntity) return@Trigger false

                    runtime.withQueryValue("entity", entity.asMostSpecificMoLangValue())

                    // Retrieve nest location and mate entity from memory
                    //val nestLocation = it.get(nestMemory)
                    val nestLocation: BlockPos = (paternalNest.value() as? IdF<BlockPos>)?.value() ?: return@Trigger false
                    val nestCenter = Vec3.atCenterOf(nestLocation)

                    // todo maybe cancel task if player is close to nest and set angry at task to that player
                    val player = (nearestVisiblePlayer.value() as? IdF<Player>)?.value()
                    if (player != null) {
                        // Cancel task if a player is within 8 blocks of the nest and get mad at them
                        if (player.position().distanceToSqr(nestCenter) <= 8 * 8) {
                            // todo have pokemon cry to indicate change in behavior
                            entity.cry()
                            angryAt.set(player.uuid)
                            attackTarget.set(player)
                            return@Trigger false
                        }
                    }


                    // Scan for a valid walkable position within an 8-block sphere
                    val validPosition = findValidWalkablePosition(world, nestCenter, entity, 8)
                    if (validPosition == null) {
                        return@Trigger false // No valid position found
                    }

                    // Set the entity's walk and look targets
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
}
