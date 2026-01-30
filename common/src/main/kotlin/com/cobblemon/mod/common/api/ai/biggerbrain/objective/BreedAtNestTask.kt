/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.biggerbrain.objective

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.ai.biggerbrain.BiggerBrain
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.Objective
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.ObjectiveStatus
import com.cobblemon.mod.common.api.nest.WildNestType
import com.cobblemon.mod.common.api.nest.WildNestTypes
import com.cobblemon.mod.common.api.pokemon.breeding.Egg
import com.cobblemon.mod.common.api.text.green
import com.cobblemon.mod.common.block.WildNestBlock
import com.cobblemon.mod.common.block.entity.NestBlockEntity
import com.cobblemon.mod.common.breeding.BreedingLogicManager
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3

class BreedAtNestTask : Objective {
    override fun getDescription(entity: LivingEntity) = "Breed at nest"

    override fun perform(
        entity: LivingEntity,
        brain: BiggerBrain,
        activeObjectiveTags: MutableSet<String>
    ): ObjectiveStatus {
        if (entity !is PokemonEntity || !entity.isAlive) return ObjectiveStatus.INVALID

        val paternalNestLoc = brain.getMemory(CobblemonMemories.PATERNAL_NEST_LOCATION)
        if (entity.pokemon.isWild() && paternalNestLoc != null) {
            brain.sendDebugMessage("[BreedAtNest] Wild Pokémon already has paternal nest; skipping.")
            return ObjectiveStatus.INVALID
        }

        val nestLocation = brain.getMemory(CobblemonMemories.NEST_LOCATION) ?: return ObjectiveStatus.INVALID
        val mate = brain.getMemory(CobblemonMemories.POTENTIAL_MATE) as? PokemonEntity ?: return ObjectiveStatus.INVALID

        val world = entity.level() as? ServerLevel ?: return ObjectiveStatus.INVALID
        val nestEntity = world.getBlockEntity(nestLocation) as? NestBlockEntity ?: return ObjectiveStatus.INVALID

        val allowedTypes = allowedNestTypes(entity, mate)
        val nestType = (nestEntity.blockState.block as? WildNestBlock)?.type ?: return ObjectiveStatus.INVALID
        if (!isNestTypeAllowed(nestType, entity, allowedTypes)) {
            brain.sendDebugMessage("[BreedAtNest] Nest type $nestType not allowed. Allowed=$allowedTypes.")
            return ObjectiveStatus.INVALID
        }
        val isNestWaterlogged = nestEntity.blockState.getOptionalValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED).orElse(false)
        if (!isWaterGroupCompatible(entity, mate, isNestWaterlogged)) {
            brain.sendDebugMessage("[BreedAtNest] Water-group mismatch for nest at $nestLocation (waterlogged=$isNestWaterlogged).")
            return ObjectiveStatus.INVALID
        }

        val targetVec = Vec3.atCenterOf(nestLocation)
        if (entity.distanceToSqr(targetVec) > 2.0 && mate.distanceToSqr(targetVec) > 2.0) {
            brain.sendDebugMessage("[BreedAtNest] Parents not in range of nest $nestLocation.")
            return ObjectiveStatus.INACTIVE
        }

        if (nestEntity.getItem(0) != ItemStack.EMPTY) {
            brain.sendDebugMessage("[BreedAtNest] Nest at $nestLocation already has an egg.")
            return ObjectiveStatus.INVALID
        }

        val parentOne = entity.pokemon
        val parentTwo = mate.pokemon
        val breedingResult = BreedingLogicManager.breed(parentOne, parentTwo)
        val form = BreedingLogicManager.calculateForm(parentOne, parentTwo)

        val egg = breedingResult.pokemon?.let { result ->
            Egg(
                species = result.species.resourceIdentifier,
                parentOne = parentOne,
                parentTwo = parentTwo,
                form = form.name.lowercase(),
                timeToHatch = 6000F
            )
        } ?: return ObjectiveStatus.INVALID

        nestEntity.setItem(egg.createEggItemStack())
        brain.sendDebugMessage("[BreedAtNest] Egg placed at $nestLocation.")

        if (Cobblemon.config.canDestinyKnotBreak) {
            if (entity.pokemon.heldItem.item == CobblemonItems.DESTINY_KNOT.asItem())
                entity.pokemon.heldItemNoCopy().damageValue += 1
            if (mate.pokemon.heldItem.item == CobblemonItems.DESTINY_KNOT.asItem())
                mate.pokemon.heldItemNoCopy().damageValue += 1
        }

        if (nestEntity.ownerUUID == null) {
            nestEntity.parents = Pair(entity.uuid, mate.uuid)
            nestEntity.nestClaimedBy = Pair(entity.uuid, mate.uuid)
        }

        parentOne.breedingCooldown = BreedingLogicManager.resetBreedingCooldown(parentOne)
        parentTwo.breedingCooldown = BreedingLogicManager.resetBreedingCooldown(parentTwo)

        world.sendParticles(
            net.minecraft.core.particles.ParticleTypes.HEART,
            entity.getRandomX(1.0),
            entity.getRandomY() + 0.5,
            entity.getRandomZ(1.0),
            4 + kotlin.random.Random.nextInt(2),
            0.2 * entity.random.nextGaussian(),
            0.1 * entity.random.nextGaussian(),
            0.2 * entity.random.nextGaussian(),
            0.02
        )
        world.sendParticles(
            net.minecraft.core.particles.ParticleTypes.HEART,
            mate.getRandomX(1.0),
            mate.getRandomY() + 0.5,
            mate.getRandomZ(1.0),
            4 + kotlin.random.Random.nextInt(2),
            0.2 * mate.random.nextGaussian(),
            0.1 * mate.random.nextGaussian(),
            0.2 * mate.random.nextGaussian(),
            0.02
        )

        brain.setMemory(CobblemonMemories.PATERNAL_NEST_LOCATION, nestLocation)
        brain.setMemory(CobblemonMemories.EGG_IN_NEST, true)
        mate.biggerBrain.setMemory(CobblemonMemories.PATERNAL_NEST_LOCATION, nestLocation)
        mate.biggerBrain.setMemory(CobblemonMemories.EGG_IN_NEST, true)

        world.players().forEach { player ->
            player.displayClientMessage("An egg has been laid in the nest!".green(), true)
        }

        return ObjectiveStatus.ACTIVE
    }

    private fun allowedNestTypes(entity: PokemonEntity, mate: PokemonEntity): Set<WildNestType> {
        val types = WildNestTypes.combinedTypes(entity, mate)
        val mutable = types.toMutableSet()
        if (!entity.pokemon.isWild() && !mate.pokemon.isWild()) {
            mutable.add(WildNestType.PLAYER)
        }
        return mutable
    }

    private fun isNestTypeAllowed(
        nestType: WildNestType,
        entity: PokemonEntity,
        allowedTypes: Set<WildNestType>
    ): Boolean {
        if (nestType == WildNestType.PLAYER) {
            return !entity.pokemon.isWild() && allowedTypes.contains(WildNestType.PLAYER)
        }
        return allowedTypes.contains(nestType)
    }

    private fun isWaterGroupCompatible(
        entity: PokemonEntity,
        mate: PokemonEntity,
        isNestWaterlogged: Boolean
    ): Boolean {
        val waterEggGroups = setOf("water_1", "water_2", "water_3")
        val entityEggGroups = entity.pokemon.species.eggGroups.map { it.name.lowercase() }
        val mateEggGroups = mate.pokemon.species.eggGroups.map { it.name.lowercase() }
        val entityHasWater = entityEggGroups.any { it in waterEggGroups }
        val mateHasWater = mateEggGroups.any { it in waterEggGroups }
        val entityOnlyWater = entityEggGroups.all { it in waterEggGroups }
        val mateOnlyWater = mateEggGroups.all { it in waterEggGroups }

        return if (isNestWaterlogged) {
            entityHasWater && mateHasWater
        } else {
            !(entityOnlyWater || mateOnlyWater)
        }
    }
}
