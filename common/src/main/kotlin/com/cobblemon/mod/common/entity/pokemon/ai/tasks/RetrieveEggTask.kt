package com.cobblemon.mod.common.entity.pokemon.ai.tasks

import com.cobblemon.mod.common.CobblemonItemComponents
import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.item.PokemonEggItem
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.WalkTarget
import net.minecraft.world.phys.Vec3
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.behavior.BehaviorControl
import net.minecraft.world.entity.ai.behavior.OneShot
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder
import net.minecraft.world.entity.ai.behavior.declarative.Trigger
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity

// pathing to found egg item in the world
object RetrieveEggTask {

    fun create(): OneShot<PokemonEntity> {
        return BehaviorBuilder.create {
            it.group(
                it.present(CobblemonMemories.PATERNAL_NEST_LOCATION),
                it.registered(CobblemonMemories.EGG_IN_NEST),
                it.present(CobblemonMemories.EGG_LOCATION),
                it.absent(MemoryModuleType.WALK_TARGET)
            ).apply(it) { nestMemory, eggInNest, eggLocation, walkTarget ->
                Trigger { world, entity, time ->
                    if (entity !is PathfinderMob || !entity.isAlive) return@Trigger false

                    val eggInNest = entity.brain.getMemory(CobblemonMemories.EGG_IN_NEST).orElse(null)
                    if (eggInNest != null && eggInNest == true) {
                        return@Trigger false
                    }

                    val eggPos: BlockPos = (eggLocation.value() as? BlockPos) ?: return@Trigger false
                    val eggCenter = Vec3.atCenterOf(eggPos)

                    // If already within pickup range, cancel task
                    if (entity.distanceToSqr(eggCenter) <= 2.0) {
                        return@Trigger false
                    }

                    // Move towards the egg
                    walkTarget.set(WalkTarget(eggCenter, 1.0F, 1))
                    // todo set look target

                    return@Trigger true
                }
            }
        }
    }
}
