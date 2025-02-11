package com.cobblemon.mod.common.entity.pokemon.ai.tasks

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.ai.BrainConfigurationContext
import com.cobblemon.mod.common.api.ai.asVariables
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMostSpecificMoLangValue
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

object PlaceEggInNestTask {
    fun create(): OneShot<PokemonEntity> {
        /*runtime.withQueryValue("entity", entity.asMostSpecificMoLangValue())
        if (!condition.resolveBoolean() || entity !is PokemonEntity) return null
*/
        return BehaviorBuilder.create {
            it.group(
                it.present(CobblemonMemories.PATERNAL_NEST_LOCATION),
                it.present(CobblemonMemories.HOLDING_EGG),
                it.registered(CobblemonMemories.EGG_IN_NEST),
                it.absent(MemoryModuleType.WALK_TARGET)
            ).apply(it) { nestMemory, holdingEgg, EggInNest, walkTarget ->
                Trigger { world, entity, time ->
                    if (entity !is PathfinderMob || !entity.isAlive) return@Trigger false

                    //runtime.withQueryValue("entity", entity.asMostSpecificMoLangValue())

                    // run this trigger if the egg is not in nest
                    val eggInNest = entity.brain.getMemory(CobblemonMemories.EGG_IN_NEST).orElse(null)
                    if (eggInNest != null && eggInNest == true) {
                        return@Trigger false
                    }

                    val nestLocation: BlockPos = (nestMemory.value() as? IdF<BlockPos>)?.value() ?: return@Trigger false

                    val nestEntity = when {
                        world.getBlockEntity(nestLocation) == null-> null
                        else -> (world.getBlockEntity(nestLocation) as NestBlockEntity)
                    }
                    if (nestEntity == null)
                        return@Trigger false

                    // Convert nest location to Vec3
                    val targetVec = Vec3.atCenterOf(nestLocation)

                    // Check proximity to the nest
                    if (entity.distanceToSqr(targetVec) > 2.0) {
                        return@Trigger false
                    }

                    // Ensure Pokémon is still holding an egg in hand
                    val heldItem = entity.getItemInHand(InteractionHand.MAIN_HAND)
                    if (heldItem.isEmpty || heldItem.item.asItem() != CobblemonItems.POKEMON_EGG.asItem()) {
                        return@Trigger false // Cancel if no egg in hand
                    }

                    // Ensure the nest slot is empty before placing the egg
                    if (nestEntity.getItem(0) != ItemStack.EMPTY) {
                        return@Trigger false // Cancel if nest is already occupied
                    }

                    // Place the egg into the nest
                    nestEntity.setItem(heldItem.copy())

                    // Remove the egg from the Pokémon's hand
                    entity.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY)

                    // Remove the "holding egg" memory since the egg is now placed
                    entity.brain.eraseMemory(CobblemonMemories.HOLDING_EGG)
                    entity.brain.setMemory(CobblemonMemories.EGG_IN_NEST, true) // todo maybe do all these sets in the Nest or egg sensors

                    // Notify nearby players
                    (world as ServerLevel).players().forEach { player ->
                        player.displayClientMessage("The Pokémon placed an egg in its nest!".green(), true)
                    }


                    return@Trigger true
                }
            }
        }
    }
}
