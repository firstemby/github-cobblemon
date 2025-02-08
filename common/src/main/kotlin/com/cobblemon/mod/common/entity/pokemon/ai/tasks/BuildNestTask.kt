package com.cobblemon.mod.common.entity.pokemon.ai.tasks

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.ai.BrainConfigurationContext
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
                    it.absent(MemoryModuleType.NEAREST_VISIBLE_PLAYER),
                    it.registered(MemoryModuleType.WALK_TARGET)
            ).apply(it) { nestMemory, paternalNestMemory, attackTarget, potentialMate, readyToBuildNest, nearbyVisiblePlayer, walkTarget ->
                Trigger { world, entity, time ->
                    if (entity !is PathfinderMob || !entity.isAlive) return@Trigger false

                    //runtime.withQueryValue("entity", entity.asMostSpecificMoLangValue())


                    // Get the held item and validate that it can make a nest
                    val heldItem = entity.getItemInHand(InteractionHand.MAIN_HAND)
                    val craftResult = Nest.craftedNestResult(heldItem)

                    if (!entity.brain.getMemory(CobblemonMemories.READY_TO_BUILD_NEST).orElse(false) || craftResult == null) {
                        return@Trigger false
                    }

                    val (craftedNest, remainingItems) = craftResult

                    // Find ground position for the nest TODO we might want a sensor to sense a perfect position for the nest based on species etc
                    val nestPos = entity.blockPosition().below()

                    // Ensure placement area is valid TODO have better validation for the placement area maybe?
                    if (!world.getBlockState(nestPos).isAir && 1 == 2) {
                        return@Trigger false
                    }

                    // Place the nest block
                    world.setBlock(nestPos, craftedNest.block.defaultBlockState(), 3)

                    // Update held stack with remaining materials
                    if (remainingItems > 0) {
                        entity.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(heldItem.item, remainingItems))
                    } else {
                        entity.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY)
                    }

                    // Clear memories
                    entity.brain.eraseMemory(CobblemonMemories.READY_TO_BUILD_NEST)

                    // Notify nearby players for debug
                    (world as ServerLevel).players().forEach { player ->
                        player.displayClientMessage("A Pokémon has built a nest!".green(), true)
                    }

                    return@Trigger true
                }
            }
        }
    }
}
