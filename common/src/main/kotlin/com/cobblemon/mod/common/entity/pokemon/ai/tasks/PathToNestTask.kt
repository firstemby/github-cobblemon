package com.cobblemon.mod.common.entity.pokemon.ai.tasks

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.ai.BrainConfigurationContext
import com.cobblemon.mod.common.api.ai.asVariables
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMostSpecificMoLangValue
import com.cobblemon.mod.common.api.npc.configuration.MoLangConfigVariable
import com.cobblemon.mod.common.block.entity.NestBlockEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.withQueryValue
import com.mojang.datafixers.kinds.IdF
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
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
import kotlin.jvm.optionals.getOrNull

object PathToNestTask {
    fun create(): OneShot<PokemonEntity> {
        /*runtime.withQueryValue("entity", entity.asMostSpecificMoLangValue())
        if (!condition.resolveBoolean() || entity !is PokemonEntity) return null*/

        return BehaviorBuilder.create {
            it.group(
                it.present(MemoryModuleType.LOOK_TARGET), // todo This might need to be it.absent but only it.present works atm
                it.absent(MemoryModuleType.WALK_TARGET),
                it.present(CobblemonMemories.NEST_LOCATION),
                it.present(CobblemonMemories.POTENTIAL_MATE),
                it.registered(CobblemonMemories.PATERNAL_NEST_LOCATION),
                it.registered(CobblemonMemories.EGG_IN_NEST),
                it.registered(CobblemonMemories.HOLDING_EGG),
            ).apply(it) { lookTarget, walkTarget, nestMemory, mateMemory, paternalNestLoc, eggInNest, holdingEgg ->
                Trigger { world, entity, time ->
                    if (entity !is PathfinderMob || !entity.isAlive) return@Trigger false

                    val isHoldingEgg = entity.brain.getMemory(CobblemonMemories.HOLDING_EGG).orElse(false)
                    val eggInNest = entity.brain.getMemory(CobblemonMemories.EGG_IN_NEST).orElse(false)
                    val paternalNestLoc = entity.brain.getMemory(CobblemonMemories.PATERNAL_NEST_LOCATION).orElse(null)

                    if (isHoldingEgg && !eggInNest && paternalNestLoc != null) {
                        val paternalNestVec = Vec3.atCenterOf(paternalNestLoc)

                        // Check if Pokémon are close enough to the nest
                        if (entity.distanceToSqr(paternalNestVec) <= 2.0) {
                            return@Trigger false // Task ends when both are close enough
                        }

                        // Set walk target to move towards the paternal nest
                        walkTarget.set(WalkTarget(paternalNestVec, 1.0F, 1))

                        // Set look target to focus on the paternal nest
                        lookTarget.set(BlockPosTracker(paternalNestLoc))

                        return@Trigger true
                    }

                    // Don't do anything if the pokemon has a breeding cooldown
                    if ((entity as PokemonEntity).pokemon.breedingCooldown != 0) return@Trigger false

                    //runtime.withQueryValue("entity", entity.asMostSpecificMoLangValue())

                    // Retrieve nest location and mate entity from memory
                    //val nestLocation = it.get(nestMemory)
                    val nestLocation: BlockPos = (nestMemory.value() as? IdF<BlockPos>)?.value() ?: return@Trigger false

                    val nestEntity = when {
                        world.getBlockEntity(nestLocation) == null-> null
                        else -> (world.getBlockEntity(nestLocation) as NestBlockEntity)
                    }
                    if (nestEntity == null)
                        return@Trigger false

                    //val mate = it.get(mateMemory) as? PokemonEntity ?: return@Trigger false
                    val mate = (mateMemory.value() as? IdF<PokemonEntity>)?.value() ?: return@Trigger false

                    // todo try to find a way to only have pairs of mates instead of an orgy

                    // Create a target vector at the nest location
                    val targetVec = Vec3.atCenterOf(nestLocation)

                    // Check if Pokémon are close enough to the nest
                    if (entity.distanceToSqr(targetVec) <= 2.0 && mate.distanceToSqr(targetVec) <= 2.0) {
                        return@Trigger false // Task ends when both are close enough
                    }

                    // cancel task if nest is taken
                    if (nestEntity.getItem(0) != ItemStack.EMPTY) {
                        return@Trigger false // Task ends when both are close enough
                    }

                    if ((entity as PokemonEntity).tethering?.tetheringId != null) {
                        val test1 = 1 // for breakpointing here to test my pastured mons
                    }


                    // try to get the main pokemon to path and look at the nest

                    walkTarget.set(WalkTarget(targetVec, /*speedMultiplier.resolveFloat()*/ 1.0F, 1))
                    lookTarget.set(BlockPosTracker(targetVec.add(0.0, entity.eyeHeight.toDouble(), 0.0)))

                    mate.brain.setMemory(
                        MemoryModuleType.WALK_TARGET,
                        WalkTarget(targetVec, /*speedMultiplier.resolveFloat()*/ 1.0F, 1)
                    )
                    mate.brain.setMemory(MemoryModuleType.LOOK_TARGET, BlockPosTracker(targetVec.add(0.0, entity.eyeHeight.toDouble(), 0.0)))

                    return@Trigger true
                }
            }
        }
    }
}

