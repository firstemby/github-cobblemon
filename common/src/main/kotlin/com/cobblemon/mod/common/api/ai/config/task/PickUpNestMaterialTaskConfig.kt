/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.config.task

import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.ai.BehaviourConfigurationContext
import com.cobblemon.mod.common.api.ai.WrapperLivingEntityTask
import com.cobblemon.mod.common.api.ai.asVariables
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMostSpecificMoLangValue
import com.cobblemon.mod.common.api.npc.configuration.MoLangConfigVariable
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.entity.pokemon.ai.tasks.PathToNestTask
import com.cobblemon.mod.common.entity.pokemon.ai.tasks.PickUpNestMaterialTask
import com.cobblemon.mod.common.util.withQueryValue
import com.mojang.datafixers.kinds.IdF
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.behavior.BehaviorControl
import net.minecraft.world.entity.ai.behavior.BlockPosTracker
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder
import net.minecraft.world.entity.ai.behavior.declarative.Trigger
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.WalkTarget
import net.minecraft.world.phys.Vec3

class PickUpNestMaterialTaskConfig : SingleTaskConfig {
    companion object {
        const val BREED = "breed" // Category
    }

    val condition = booleanVariable(BREED, "breeds", true).asExpressible()
    val speedMultiplier = numberVariable(BREED, "speed_multiplier", 0.6).asExpressible()

    override fun getVariables(entity: LivingEntity) = listOf(
        condition,
        speedMultiplier
    ).asVariables()

    override fun createTask(
        entity: LivingEntity,
        brainConfigurationContext: BehaviourConfigurationContext
    ): BehaviorControl<in LivingEntity>? {
        return WrapperLivingEntityTask(PickUpNestMaterialTask.create(), PokemonEntity::class.java)
    }
}

    /*override fun createTask(
        entity: LivingEntity,
        brainConfigurationContext: BrainConfigurationContext
    ): BehaviorControl<in LivingEntity>? {
        runtime.withQueryValue("entity", entity.asMostSpecificMoLangValue())
        if (!condition.resolveBoolean() || entity !is PokemonEntity) return null

        return BehaviorBuilder.create {
            it.group(
                 it.present(MemoryModuleType.LOOK_TARGET), // todo This might need to be it.absent but only it.present works atm
                it.absent(MemoryModuleType.WALK_TARGET),
                it.present(CobblemonMemories.NEST_LOCATION),
                it.present(CobblemonMemories.POTENTIAL_MATE)
            ).apply(it) { lookTarget, walkTarget, nestMemory, mateMemory ->
                Trigger { world, entity, time ->
                    if (entity !is PathfinderMob || !entity.isAlive) return@Trigger false

                    // Don't do anything if the pokemon has a breeding cooldown
                    if ((entity as PokemonEntity).pokemon.breedingCooldown != 0) return@Trigger false

                    runtime.withQueryValue("entity", entity.asMostSpecificMoLangValue())

                    // Retrieve nest location and mate entity from memory
                    //val nestLocation = it.get(nestMemory)
                    val nestLocation: BlockPos = (nestMemory.value() as? IdF<BlockPos>)?.value() ?: return@Trigger false

                    //val mate = it.get(mateMemory) as? PokemonEntity ?: return@Trigger false
                    val mate = (mateMemory.value() as? IdF<PokemonEntity>)?.value() ?: return@Trigger false

                    // todo try to find a way to only have pairs of mates instead of an orgy

                    // Create a target vector at the nest location
                    val targetVec = Vec3.atCenterOf(nestLocation)

                    // Check if Pokémon are close enough to the nest
                    if (entity.distanceToSqr(targetVec) <= 2.0 && mate.distanceToSqr(targetVec) <= 2.0) {
                        return@Trigger false // Task ends when both are close enough
                    }

                    *//*if ((entity as PokemonEntity).tethering.tetheringId != null) {
                        val test1 = 1 // for breakpointing here to test my pastured mons
                    }*//*

                    // try to get the main pokemon to path and look at the nest

                    walkTarget.set(WalkTarget(targetVec, speedMultiplier.resolveFloat(), 1))
                    lookTarget.set(BlockPosTracker(targetVec.add(0.0, entity.eyeHeight.toDouble(), 0.0)))

                    // try to find a way to get the mate to pathfind and look at the nest too...
                    mate.brain.setMemory(
                        MemoryModuleType.WALK_TARGET,
                        WalkTarget(targetVec, speedMultiplier.resolveFloat(), 1)
                    )
                    mate.brain.setMemory(MemoryModuleType.LOOK_TARGET, BlockPosTracker(targetVec.add(0.0, entity.eyeHeight.toDouble(), 0.0)))

                    return@Trigger true
                }
            }
        }
    }
}*/