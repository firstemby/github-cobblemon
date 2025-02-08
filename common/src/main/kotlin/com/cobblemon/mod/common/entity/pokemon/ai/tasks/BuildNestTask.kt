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
                    val nestItem = Nest.entries.firstNotNullOfOrNull { it.craftedNestResult(heldItem) }
                    val readyToBuild = entity.brain.getMemory(CobblemonMemories.READY_TO_BUILD_NEST).orElse(false)
                    if (!readyToBuild) {
                        return@Trigger false
                    }

                    if (nestItem == null) {
                        return@Trigger false
                    }

                    // Find the ground position for the nest TODO find a better way to determine a suitable nest placement spot (maybe with a sensor)
                    val nestPos = entity.blockPosition().below() // Assume placement on the block below

                    // Ensure the area is valid for nest placement
                    if (!world.getBlockState(nestPos).isAir) {
                        return@Trigger false // Cancel if the block isn't air
                    }

                    // Place the nest block
                    world.setBlock(nestPos, nestItem.block.defaultBlockState(), 3)

                    // Remove the materials from the Pokémon's hand
                    entity.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY)

                    // clear memories
                    entity.brain.eraseMemory(CobblemonMemories.READY_TO_BUILD_NEST)

                    // Notify nearby players for debug :D
                    (world as ServerLevel).players().forEach { player ->
                        player.displayClientMessage("A Pokémon has built a nest!".green(), true)
                    }

                    return@Trigger true
                }
            }
        }
    }
}
