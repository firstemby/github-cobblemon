package com.cobblemon.mod.common.entity.pokemon.ai.tasks

import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.behavior.BlockPosTracker
import net.minecraft.world.entity.ai.behavior.OneShot
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder
import net.minecraft.world.entity.ai.behavior.declarative.Trigger
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.WalkTarget
import net.minecraft.world.phys.Vec3

object AttackEggThiefTask {
    fun create(): OneShot<PokemonEntity> {
        return BehaviorBuilder.create {
            it.group(
                it.present(MemoryModuleType.LOOK_TARGET),
                it.absent(MemoryModuleType.WALK_TARGET),
                it.absent(CobblemonMemories.EGG_IN_NEST), // todo I want this to make it so the task only runs when the nest has the egg
                it.absent(CobblemonMemories.EGG_LOCATION),
                it.present(CobblemonMemories.EGG_THIEF),
                it.registered(MemoryModuleType.ANGRY_AT),
                it.absent(MemoryModuleType.ATTACK_TARGET),
            ).apply(it) { lookTarget, walkTarget, eggInNest, eggLocation, eggThief, angryAt, attackTarget ->
                Trigger { world, entity, time ->
                    if (entity !is PathfinderMob || !entity.isAlive || entity !is PokemonEntity) return@Trigger false

                    //runtime.withQueryValue("entity", entity.asMostSpecificMoLangValue())

                    val eggThief = entity.brain.getMemory(CobblemonMemories.EGG_THIEF).get()


                    // cry at the player
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

                    angryAt.set(eggThief.uuid)
                    attackTarget.set(eggThief)

                    return@Trigger true
                }
            }
        }
    }
}
