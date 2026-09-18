/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.biggerbrain.sense

import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.ai.biggerbrain.BiggerBrain
import com.cobblemon.mod.common.api.ai.config.sense.SenseConfig
import com.cobblemon.mod.common.api.nest.WildNestType
import com.cobblemon.mod.common.api.nest.WildNestTypes
import com.cobblemon.mod.common.block.WildNestBlock
import com.cobblemon.mod.common.block.entity.NestBlockEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.level.block.entity.BlockEntity

class NestSense(
    private var cooldownTicks: Int = 200
) : Sense {
    override var ticksUntilNextExecution: Int = 200
    override val memories = setOf(
        CobblemonMemories.NEST_LOCATION,
        CobblemonMemories.PATERNAL_NEST_LOCATION,
        CobblemonMemories.EGG_IN_NEST,
        CobblemonMemories.EGG_LOCATION,
        CobblemonMemories.NEST_DESPAWN_IMMUNE_UNTIL_TICK
    )

    override fun merge(other: Sense): Boolean {
        if (other is NestSense) {
            this.cooldownTicks = minOf(this.cooldownTicks, other.cooldownTicks)
            return true
        }
        return false
    }

    override fun tick(entity: LivingEntity, brain: BiggerBrain) {
        ticksUntilNextExecution = cooldownTicks
        if (entity !is PokemonEntity) return
        val world = entity.level() as? ServerLevel ?: return

        val paternalNestLocation = brain.getMemory(CobblemonMemories.PATERNAL_NEST_LOCATION)

        if (paternalNestLocation != null) {
            val blockAtLocation = world.getBlockEntity(paternalNestLocation)
            if (blockAtLocation !is NestBlockEntity) {
                debug(brain, "Paternal nest missing at $paternalNestLocation; clearing memories.")
                brain.eraseMemory(CobblemonMemories.PATERNAL_NEST_LOCATION)
                brain.eraseMemory(CobblemonMemories.EGG_IN_NEST)
                brain.eraseMemory(CobblemonMemories.EGG_LOCATION)
            } else if (blockAtLocation.parents.first == entity.uuid || blockAtLocation.parents.second == entity.uuid) {
                debug(brain, "Paternal nest confirmed at $paternalNestLocation.")
                brain.setMemory(CobblemonMemories.PATERNAL_NEST_LOCATION, paternalNestLocation)
                brain.setMemory(CobblemonMemories.NEST_LOCATION, paternalNestLocation)

                if (blockAtLocation.getItem().isEmpty) {
                    brain.setMemory(CobblemonMemories.EGG_IN_NEST, false)
                } else {
                    brain.setMemory(CobblemonMemories.EGG_IN_NEST, true)
                }
                return
            } else {
                debug(brain, "Paternal nest no longer owned by this Pokémon; clearing memories.")
                brain.eraseMemory(CobblemonMemories.PATERNAL_NEST_LOCATION)
                brain.setMemory(CobblemonMemories.EGG_IN_NEST, false)
                brain.eraseMemory(CobblemonMemories.EGG_LOCATION)
            }
        }

        val nearbyNest = findNearbyNest(entity, brain, world, entity.blockPosition())
        if (nearbyNest != null) {
            debug(brain, "Nearby nest selected at $nearbyNest.")
            brain.setMemory(CobblemonMemories.NEST_LOCATION, nearbyNest)
        } else {
            debug(brain, "No valid nearby nest found.")
            brain.eraseMemory(CobblemonMemories.NEST_LOCATION)
        }
    }

    private fun findNearbyNest(pokemonEntity: PokemonEntity, brain: BiggerBrain, world: ServerLevel, entityPos: BlockPos): BlockPos? {
        return BlockPos.betweenClosed(
            entityPos.offset(-20, -5, -20),
            entityPos.offset(20, 5, 20)
        ).firstOrNull { pos ->
            val blockEntity: BlockEntity? = world.getBlockEntity(pos)
            if (blockEntity !is NestBlockEntity) return@firstOrNull false

            if (pokemonEntity.isBattling || pokemonEntity.isBattleClone() || (!pokemonEntity.pokemon.isWild() && pokemonEntity.tethering?.tetheringId == null)) return@firstOrNull false

            val nestBlockEntity = blockEntity
            val allowedTypes = allowedNestTypes(pokemonEntity, brain)
            val nestType = (nestBlockEntity.blockState.block as? WildNestBlock)?.type
            if (nestType == null) {
                debug(brain, "Rejecting nest at $pos: missing WildNestBlock type.")
                return@firstOrNull false
            }
            if (!isNestTypeAllowed(nestType, pokemonEntity, allowedTypes)) {
                debug(brain, "Rejecting nest at $pos: type=$nestType not in $allowedTypes.")
                return@firstOrNull false
            }
            if (!canPathfindToTarget(pokemonEntity, nestBlockEntity.blockPos)) return@firstOrNull false

            val isWildNest = nestBlockEntity.ownerUUID == null
            val isOwnedNest = nestBlockEntity.ownerUUID != null
            val isClaimed = nestBlockEntity.nestClaimedBy.first != null || nestBlockEntity.nestClaimedBy.second != null
            val isEggless = nestBlockEntity.getItem(0).isEmpty
            val isNestWaterlogged = nestBlockEntity.blockState.getOptionalValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED).orElse(false)
            val mate = brain.getMemory(CobblemonMemories.POTENTIAL_MATE) as? PokemonEntity
            val nestClaimerOne = nestBlockEntity.nestClaimedBy.first?.let { world.getEntity(it) }
            val nestClaimerTwo = nestBlockEntity.nestClaimedBy.second?.let { world.getEntity(it) }
            val isClaimerOne = pokemonEntity.uuid == nestClaimerOne?.uuid
            val isClaimerTwo = pokemonEntity.uuid == nestClaimerTwo?.uuid
            val nestParentOneActive = nestBlockEntity.parents.first?.let { world.getEntity(it) }
            val nestParentTwoActive = nestBlockEntity.parents.second?.let { world.getEntity(it) }

            if (isWildNest && isEggless && ((nestParentOneActive != null || nestParentTwoActive != null) || (isClaimed && (nestClaimerOne != null && !isClaimerOne && nestClaimerTwo != null && !isClaimerTwo)))) {
                debug(brain, "Rejecting nest at $pos: claimed or active parents present.")
                return@firstOrNull false
            }

            if (!isWaterGroupCompatible(pokemonEntity, mate, isNestWaterlogged)) {
                debug(brain, "Rejecting nest at $pos: water-group compatibility failed (waterlogged=$isNestWaterlogged).")
                return@firstOrNull false
            }

            return@firstOrNull when {
                pokemonEntity.tethering?.tetheringId != null -> isOwnedNest && isEggless
                else -> isWildNest && isEggless
            }
        }
    }

    private fun canPathfindToTarget(entity: PathfinderMob, targetPos: BlockPos): Boolean {
        val path = entity.navigation.createPath(targetPos, 0)
        return path != null && path.distToTarget < 1.75f
    }

    private fun isWaterGroupCompatible(
        entity: PokemonEntity,
        mate: PokemonEntity?,
        isNestWaterlogged: Boolean
    ): Boolean {
        val waterEggGroups = setOf("water_1", "water_2", "water_3")
        val entityEggGroups = entity.pokemon.species.eggGroups.map { it.name.lowercase() }
        val entityHasWater = entityEggGroups.any { it in waterEggGroups }
        val entityOnlyWater = entityEggGroups.all { it in waterEggGroups }

        val mateEggGroups = mate?.pokemon?.species?.eggGroups?.map { it.name.lowercase() } ?: emptyList()
        val mateHasWater = mateEggGroups.any { it in waterEggGroups }
        val mateOnlyWater = mateEggGroups.isNotEmpty() && mateEggGroups.all { it in waterEggGroups }

        return if (isNestWaterlogged) {
            entityHasWater && mateHasWater
        } else {
            !(entityOnlyWater || mateOnlyWater)
        }
    }

    private fun allowedNestTypes(entity: PokemonEntity, brain: BiggerBrain): Set<WildNestType> {
        val mate = brain.getMemory(CobblemonMemories.POTENTIAL_MATE) as? PokemonEntity
        val types = if (mate != null) {
            WildNestTypes.combinedTypes(entity, mate)
        } else {
            WildNestTypes.typesFor(entity.pokemon.species)
        }
        val mutable = types.toMutableSet()
        if (!entity.pokemon.isWild() && mate != null && !mate.pokemon.isWild()) {
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

    private fun debug(brain: BiggerBrain, message: String) {
        brain.sendDebugMessage("[NestSense] $message")
    }

    class Config : SenseConfig {
        override fun create(entity: LivingEntity, behaviourConfigurationContext: com.cobblemon.mod.common.api.ai.BehaviourConfigurationContext): Sense? {
            return NestSense()
        }
    }
}
