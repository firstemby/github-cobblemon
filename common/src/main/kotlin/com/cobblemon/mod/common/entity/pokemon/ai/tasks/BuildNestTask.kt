/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.entity.pokemon.ai.tasks

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.ai.asVariables
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMostSpecificMoLangValue
import com.cobblemon.mod.common.api.nest.Nest
import com.cobblemon.mod.common.api.npc.configuration.MoLangConfigVariable
import com.cobblemon.mod.common.api.pokemon.breeding.Egg
import com.cobblemon.mod.common.api.text.green
import com.cobblemon.mod.common.block.entity.NestBlockEntity
import com.cobblemon.mod.common.breeding.BreedingLogicManager
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.withQueryValue
import com.mojang.datafixers.kinds.IdF
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
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
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import kotlin.jvm.optionals.getOrElse
import kotlin.jvm.optionals.getOrNull

object BuildNestTask {
    fun create(): OneShot<PokemonEntity> {
        /*runtime.withQueryValue("entity", entity.asMostSpecificMoLangValue())
        if (!condition.resolveBoolean() || entity !is PokemonEntity) return null
*/
        return BehaviorBuilder.create {
            it.group(
                    it.absent(CobblemonMemories.NEST_LOCATION),
                    it.absent(CobblemonMemories.PATERNAL_NEST_LOCATION),
                    it.absent(MemoryModuleType.ATTACK_TARGET),
                    it.present(CobblemonMemories.POTENTIAL_MATE),
                    it.present(CobblemonMemories.READY_TO_BUILD_NEST),
                    it.present(CobblemonMemories.IDEAL_NEST_LOCATION),
                    it.absent(MemoryModuleType.NEAREST_VISIBLE_PLAYER),
                    it.registered(MemoryModuleType.WALK_TARGET)
            ).apply(it) { nestMemory, paternalNestMemory, attackTarget, potentialMate, readyToBuildNest, idealLocation, nearbyVisiblePlayer, walkTarget ->
                Trigger { world, entity, time ->
                    if (entity !is PathfinderMob || !entity.isAlive) return@Trigger false

                    val mate = entity.brain.getMemory(CobblemonMemories.POTENTIAL_MATE).getOrNull() as? PokemonEntity ?: return@Trigger false
                    val idealNestPos: BlockPos = entity.brain.getMemory(CobblemonMemories.IDEAL_NEST_LOCATION).getOrNull()
                            ?: return@Trigger false
                    val nestCenter = Vec3.atCenterOf(idealNestPos)

                    // Pokémon is ready and the craft result is valid
                    if (!entity.brain.getMemory(CobblemonMemories.READY_TO_BUILD_NEST).orElse(false)) return@Trigger false

                    // Both Pokémon are close enough to the nest location - we don't want them to be apart :c
                    if (entity.distanceToSqr(nestCenter) > 2.0 || (mate.distanceToSqr(nestCenter)) > 2.0) {
                        return@Trigger false
                    }

                    // Get the main Pokémon's held item
                    var mainHeldItem = entity.getItemInHand(InteractionHand.MAIN_HAND)

                    // Check if the main Pokémon alone has enough materials to craft the nest
                    var craftResult = Nest.craftedNestResult(mainHeldItem)
                    if (craftResult == null && mate != null) {
                        // If the main Pokémon doesn’t have enough materials, check if the mate can contribute
                        val mateHeldItem = mate.getItemInHand(InteractionHand.MAIN_HAND)

                        // Only combine materials if they are the same item type
                        if (!mainHeldItem.isEmpty && !mateHeldItem.isEmpty && mainHeldItem.item == mateHeldItem.item) {
                            val combinedStackSize = mainHeldItem.count + mateHeldItem.count
                            craftResult = Nest.craftedNestResult(ItemStack(mainHeldItem.item, combinedStackSize))

                            // If a valid craft result is found keep trying to share
                            if (craftResult != null) {
                                val (craftedNest, requiredMaterials) = craftResult

                                var remainingNeeded = requiredMaterials
                                var fromMain = minOf(mainHeldItem.count, remainingNeeded)
                                remainingNeeded -= fromMain

                                var fromMate = minOf(mateHeldItem.count, remainingNeeded)
                                remainingNeeded -= fromMate

                                if (remainingNeeded > 0) return@Trigger false // Not enough materials

                                // Deduct materials from both Pokémon
                                entity.setItemInHand(InteractionHand.MAIN_HAND, if (mainHeldItem.count - fromMain > 0) ItemStack(mainHeldItem.item, mainHeldItem.count - fromMain) else ItemStack.EMPTY)
                                mate.setItemInHand(InteractionHand.MAIN_HAND, if (mateHeldItem.count - fromMate > 0) ItemStack(mateHeldItem.item, mateHeldItem.count - fromMate) else ItemStack.EMPTY)

                                // Place the nest
                                world.setBlock(idealNestPos, craftedNest.block.defaultBlockState(), 3)

                                // claim nest so it can be used by these mons once it is built
                                (world.getBlockEntity(idealNestPos) as NestBlockEntity).nestClaimedBy = Pair(entity.uuid, mate.uuid)

                                // Clear memories
                                entity.brain.eraseMemory(CobblemonMemories.READY_TO_BUILD_NEST)
                                entity.brain.eraseMemory(CobblemonMemories.IDEAL_NEST_LOCATION)
                                mate.brain.eraseMemory(CobblemonMemories.READY_TO_BUILD_NEST)
                                mate.brain.eraseMemory(CobblemonMemories.IDEAL_NEST_LOCATION)

                                // Notify nearby players for debug :D
                                (world as ServerLevel).players().forEach { player ->
                                    player.displayClientMessage("A Pokémon has built a nest using materials from both mates!".green(), true)
                                }

                                return@Trigger true
                            }
                        }
                    }

                    // If the main Pokémon already has enough materials, place the nest normally
                    craftResult?.let { (craftedNest, requiredMaterials) ->
                        // Deduct materials from the main Pokémon only
                        entity.setItemInHand(InteractionHand.MAIN_HAND, if (mainHeldItem.count - requiredMaterials > 0) ItemStack(mainHeldItem.item, mainHeldItem.count - requiredMaterials) else ItemStack.EMPTY)

                        // Place the nest
                        world.setBlock(idealNestPos, craftedNest.block.defaultBlockState(), 3)

                        // Clear memories
                        entity.brain.eraseMemory(CobblemonMemories.READY_TO_BUILD_NEST)

                        // Notify nearby players for debug
                        (world as ServerLevel).players().forEach { player ->
                            player.displayClientMessage("A Pokémon has built a nest!".green(), true)
                        }

                        return@Trigger true
                    }

                    return@Trigger false
                }
            }
        }
    }
}
