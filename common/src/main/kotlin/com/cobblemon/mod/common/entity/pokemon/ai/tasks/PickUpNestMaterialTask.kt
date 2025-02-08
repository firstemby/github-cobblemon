package com.cobblemon.mod.common.entity.pokemon.ai.tasks

import com.cobblemon.mod.common.CobblemonItemComponents
import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.nest.Nest
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
import net.minecraft.world.item.ItemStack
import kotlin.jvm.optionals.getOrNull

// pathing to found egg item in the world
object PickUpNestMaterialTask {

    fun create(): OneShot<PokemonEntity> {
        return BehaviorBuilder.create {
            it.group(
                it.absent(CobblemonMemories.NEST_LOCATION),
                it.absent(CobblemonMemories.PATERNAL_NEST_LOCATION),
                it.registered(CobblemonMemories.READY_TO_BUILD_NEST),
                it.absent(MemoryModuleType.ATTACK_TARGET),
                it.present(CobblemonMemories.NESTING_MATERIAL_LOCATION),
                it.present(CobblemonMemories.SENSED_NEST_MATERIAL),
                it.present(CobblemonMemories.POTENTIAL_MATE),
                it.absent(MemoryModuleType.NEAREST_VISIBLE_PLAYER),
                it.registered(MemoryModuleType.WALK_TARGET)
            ).apply(it) { nestMemory, paternalNestMemory, readyToBuildNest, attackTarget, nestingMaterialLocation, sensedNestMaterial, potentialMate, nearbyVisiblePlayer, walkTarget ->
                Trigger { world, entity, time ->
                    if (entity !is PathfinderMob || !entity.isAlive) return@Trigger false

                    val itemPos: BlockPos = entity.brain.getMemory(CobblemonMemories.NESTING_MATERIAL_LOCATION).get()
                    val itemCenter = Vec3.atCenterOf(itemPos)
                    val readyToBuild = entity.brain.getMemory(CobblemonMemories.READY_TO_BUILD_NEST).orElse(false)
                    if (readyToBuild) {
                        return@Trigger false
                    }

                    // Ensure Pokémon is close enough
                    if (entity.distanceToSqr(itemCenter) > 2.0) {
                        //entity.brain.eraseMemory(CobblemonMemories.READY_TO_GRAB_EGG)
                        return@Trigger false
                    }

                    // Find the item entity at the location
                    val sensedNestMaterial = entity.brain.getMemory(CobblemonMemories.SENSED_NEST_MATERIAL).getOrNull() ?: return@Trigger false
                    val newItemStack = sensedNestMaterial.item.copy()
                    val heldItemStack = entity.getItemInHand(InteractionHand.MAIN_HAND)

                    // Logic for handling existing item in hand
                    if (!heldItemStack.isEmpty) {
                        if (heldItemStack.item == newItemStack.item) {
                            // Same item, add amounts
                            val totalAmount = heldItemStack.count + newItemStack.count
                            entity.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(newItemStack.item, totalAmount))
                        } else {
                            // Different item, drop the current held item and replace with new one
                            val dropPos = entity.position()
                            val droppedItem = ItemEntity(world, dropPos.x, dropPos.y, dropPos.z, heldItemStack)
                            world.addFreshEntity(droppedItem)

                            entity.setItemInHand(InteractionHand.MAIN_HAND, newItemStack)
                        }
                    } else {
                        // Nothing in hand, pick up the new item
                        entity.setItemInHand(InteractionHand.MAIN_HAND, newItemStack)
                    }

                    // Remove the item from the world
                    sensedNestMaterial.remove(Entity.RemovalReason.DISCARDED)

                    // Check if materials in hand are enough to craft a nest
                    val craftResult = Nest.craftedNestResult(entity.getItemInHand(InteractionHand.MAIN_HAND))

                    if (craftResult != null) {
                        entity.brain.setMemory(CobblemonMemories.READY_TO_BUILD_NEST, true)
                    }

                    // Clear egg-related memories
                    entity.brain.eraseMemory(CobblemonMemories.NESTING_MATERIAL_LOCATION)
                    entity.brain.eraseMemory(CobblemonMemories.SENSED_NEST_MATERIAL)

                    return@Trigger true
                }
            }
        }
    }
}
