package com.cobblemon.mod.common.entity.pokemon.ai.tasks

import com.cobblemon.mod.common.CobblemonItemComponents
import com.cobblemon.mod.common.CobblemonItems
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
object PickUpEggTask {

    fun create(): OneShot<PokemonEntity> {
        return BehaviorBuilder.create {
            it.group(
                it.present(CobblemonMemories.PATERNAL_NEST_LOCATION),
                it.present(CobblemonMemories.READY_TO_GRAB_EGG),
                it.registered(CobblemonMemories.EGG_IN_NEST),
                it.present(CobblemonMemories.EGG_LOCATION),
                it.absent(MemoryModuleType.WALK_TARGET)
            ).apply(it) { nestMemory, readyToGrabEgg, eggInNest, eggMemory, walkTarget ->
                Trigger { world, entity, time ->
                    if (entity !is PathfinderMob || !entity.isAlive) return@Trigger false

                    val eggInNest = entity.brain.getMemory(CobblemonMemories.EGG_IN_NEST).orElse(null)
                    if (eggInNest != null && eggInNest == true) {
                        return@Trigger false
                    }

                    val eggPos: BlockPos = entity.brain.getMemory(CobblemonMemories.EGG_LOCATION).get()
                    val eggCenter = Vec3.atCenterOf(eggPos)

                    // Ensure Pokémon is close enough
                    if (entity.distanceToSqr(eggCenter) > 2.0) {
                        entity.brain.eraseMemory(CobblemonMemories.READY_TO_GRAB_EGG)
                        return@Trigger false
                    }

                    // Find the egg item entity at the location
                    val nearbyEggs = world.getEntitiesOfClass(ItemEntity::class.java, entity.boundingBox.inflate(1.5)) { itemEntity ->
                        itemEntity.blockPosition() == eggPos && itemEntity.item.item == CobblemonItems.POKEMON_EGG
                    }

                    val eggItem = nearbyEggs.firstOrNull() ?: return@Trigger false

                    // Ensure the egg has the correct component data
                    val eggComponent = eggItem.item.get(CobblemonItemComponents.EGG_DATA) ?: return@Trigger false

                    // Verify the Pokémon is one of the parents
                    val isParent = eggComponent.egg?.parentOne?.uuid == entity.pokemon.uuid ||
                            eggComponent.egg?.parentTwo?.uuid == entity.pokemon.uuid

                    if (!isParent) return@Trigger false // Ignore if not the parent

                    // Pick up the egg and set it in main hand
                    entity.setItemInHand(InteractionHand.MAIN_HAND, eggItem.item.copy())

                    // Remove the egg from the world
                    eggItem.remove(Entity.RemovalReason.DISCARDED)

                    // Clear egg-related memories
                    entity.brain.eraseMemory(CobblemonMemories.EGG_LOCATION)
                    entity.brain.setMemory(CobblemonMemories.HOLDING_EGG, true)
                    entity.brain.eraseMemory(CobblemonMemories.EGG_SEARCH_COUNTDOWN)
                    entity.brain.eraseMemory(CobblemonMemories.READY_TO_GRAB_EGG)

                    return@Trigger true
                }
            }
        }
    }
}
