package com.cobblemon.mod.common.entity.pokemon.ai.tasks

import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.WalkTarget
import net.minecraft.world.phys.Vec3
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.behavior.OneShot
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder
import net.minecraft.world.entity.ai.behavior.declarative.Trigger

// pathing to found nest material item in the world
object PathToNestMaterialTask {

    fun create(): OneShot<PokemonEntity> {
        return BehaviorBuilder.create {
            it.group(
                it.absent(CobblemonMemories.NEST_LOCATION),
                it.absent(CobblemonMemories.PATERNAL_NEST_LOCATION),
                it.registered(CobblemonMemories.READY_TO_BUILD_NEST),
                it.absent(MemoryModuleType.ATTACK_TARGET),
                it.present(CobblemonMemories.POTENTIAL_MATE),
                it.present(CobblemonMemories.NESTING_MATERIAL_LOCATION),
                it.absent(MemoryModuleType.NEAREST_VISIBLE_PLAYER),
                it.absent(MemoryModuleType.WALK_TARGET)
            ).apply(it) { nestMemory, paternalNestMemory, readyToBuildNest, attackTarget, potentialMate, nestingMaterialLocation, nearbyVisiblePlayers, walkTarget ->
                Trigger { world, entity, time ->
                    if (entity !is PathfinderMob || !entity.isAlive) return@Trigger false

                    val itemPos: BlockPos = entity.brain.getMemory(CobblemonMemories.NESTING_MATERIAL_LOCATION).get() ?: return@Trigger false
                    val itemCenter = Vec3.atCenterOf(itemPos)
                    val readyToBuild = entity.brain.getMemory(CobblemonMemories.READY_TO_BUILD_NEST).orElse(false)
                    if (readyToBuild) {
                        return@Trigger false
                    }

                    // If already within pickup range, cancel task
                    if (entity.distanceToSqr(itemCenter) <= 2.0) {
                        //entity.brain.setMemory(CobblemonMemories.READY_TO_GRAB_EGG, true)
                        return@Trigger false
                    }

                    // clear this memory
                    //entity.brain.eraseMemory(CobblemonMemories.READY_TO_GRAB_EGG)

                    // Move towards the nesting material
                    walkTarget.set(WalkTarget(itemCenter, 1.0F, 1))
                    // todo set look target

                    return@Trigger true
                }
            }
        }
    }
}
