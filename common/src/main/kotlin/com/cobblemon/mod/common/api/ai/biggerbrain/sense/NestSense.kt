/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.biggerbrain.sense

import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.ai.BehaviourConfigurationContext
import com.cobblemon.mod.common.api.ai.ExpressionOrEntityVariable
import com.cobblemon.mod.common.api.ai.asVariables
import com.cobblemon.mod.common.api.ai.biggerbrain.BiggerBrain
import com.cobblemon.mod.common.api.ai.config.sense.SenseConfig
import com.cobblemon.mod.common.api.ai.resolveInt
import com.cobblemon.mod.common.api.breeding.CobblemonBreeding
import com.cobblemon.mod.common.api.nest.WildNestType
import com.cobblemon.mod.common.api.nest.WildNestTypes
import com.cobblemon.mod.common.block.WildNestBlock
import com.cobblemon.mod.common.block.entity.NestBlockEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.asExpression
import com.mojang.datafixers.util.Either
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.level.block.entity.BlockEntity

class NestSense(
    private var cooldownTicks: Int = 200,
    private var radiusXZ: Int = 20,
    private var radiusY: Int = 5
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
            this.radiusXZ = maxOf(this.radiusXZ, other.radiusXZ)
            this.radiusY = maxOf(this.radiusY, other.radiusY)
            return true
        }
        return false
    }

    override fun tick(entity: LivingEntity, brain: BiggerBrain) {
        ticksUntilNextExecution = cooldownTicks
        if (entity !is PokemonEntity) return
        val world = entity.level() as? ServerLevel ?: return

        val paternalNestLocation = brain.getMemory(CobblemonMemories.PATERNAL_NEST_LOCATION)

        // early memory check so we can end early
        if (paternalNestLocation != null) {
            // end early if the nest itself isnt loaded in
            if (!world.isLoaded(paternalNestLocation)) {
                return
            }
            // check to make sure there is a nest still at that spot
            val blockAtLocation = world.getBlockEntity(paternalNestLocation)
            if (blockAtLocation !is NestBlockEntity) { // nest is gone so we kill all nest and egg memories
                brain.eraseMemory(CobblemonMemories.PATERNAL_NEST_LOCATION)
                brain.eraseMemory(CobblemonMemories.EGG_IN_NEST)
                brain.eraseMemory(CobblemonMemories.EGG_LOCATION)
            } else if (blockAtLocation.parents.first == entity.uuid || blockAtLocation.parents.second == entity.uuid) {
                // is the pokemon one of the parents of this nest? Yes? Sick let's remember that. We might want to check memories beforehand though so we don't need to set memories each time? idk todo look into
                brain.setMemory(CobblemonMemories.PATERNAL_NEST_LOCATION, paternalNestLocation)
                brain.setMemory(CobblemonMemories.NEST_LOCATION, paternalNestLocation)

                // does our nest have an egg todo we should maybe also check that not just any egg is in the nest but if it is trhe egg of the parents (check uuid of the egg components later)
                if (blockAtLocation.getItem().isEmpty) {
                    brain.setMemory(CobblemonMemories.EGG_IN_NEST, false)
                } else {
                    brain.setMemory(CobblemonMemories.EGG_IN_NEST, true)
                }
                return
            } else { // no longer parents of the nest clear
                brain.eraseMemory(CobblemonMemories.PATERNAL_NEST_LOCATION)
                brain.setMemory(CobblemonMemories.EGG_IN_NEST, false)
                brain.eraseMemory(CobblemonMemories.EGG_LOCATION)
            }
        }

        val nearbyNest = findNearbyNest(entity, brain, world, entity.blockPosition())
        if (nearbyNest != null) {
            brain.setMemory(CobblemonMemories.NEST_LOCATION, nearbyNest)
        } else {
            brain.eraseMemory(CobblemonMemories.NEST_LOCATION)
        }
    }

    private fun findNearbyNest(pokemonEntity: PokemonEntity, brain: BiggerBrain, world: ServerLevel, entityPos: BlockPos): BlockPos? {
        return BlockPos.betweenClosed(
            entityPos.offset(-radiusXZ, -radiusY, -radiusXZ),
            entityPos.offset(radiusXZ, radiusY, radiusXZ)
        ).firstOrNull { pos ->
            val blockEntity: BlockEntity? = world.getBlockEntity(pos)
            if (blockEntity !is NestBlockEntity) return@firstOrNull false

            if (pokemonEntity.isBattling || pokemonEntity.isBattleClone() || (!pokemonEntity.pokemon.isWild() && pokemonEntity.tethering?.tetheringId == null)) return@firstOrNull false

            // we might need to check if a nest nearby was forgotten about
            if (blockEntity.parents.first == pokemonEntity.uuid || blockEntity.parents.second == pokemonEntity.uuid) {
                brain.setMemory(CobblemonMemories.PATERNAL_NEST_LOCATION, pos)
                return@firstOrNull true
            }

            val nestBlockEntity = blockEntity
            val allowedTypes = allowedNestTypes(pokemonEntity, brain)
            val nestType = (nestBlockEntity.blockState.block as? WildNestBlock)?.type

            // is the nest a valid type allowed by the mates
            if (nestType == null || !isNestTypeAllowed(nestType, pokemonEntity, allowedTypes))
                return@firstOrNull false

            // can the mates get to the nest?
            if (!canPathfindToTarget(pokemonEntity, nestBlockEntity.blockPos)) return@firstOrNull false

            val isWildNest = nestBlockEntity.ownerUUID == null
            val isPlayerNest = nestBlockEntity.ownerUUID != null
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

            // skip any empty wild nests that are claimed
            if (isWildNest && isEggless && ((nestParentOneActive != null || nestParentTwoActive != null) || (isClaimed && (nestClaimerOne != null && !isClaimerOne && nestClaimerTwo != null && !isClaimerTwo)))) {
                return@firstOrNull false
            }

            // we switched to only if the nest is waterlogged now rather than our old egg group check
            if (isNestWaterlogged && WildNestType.WATERY !in allowedTypes) {
                return@firstOrNull false
            }

            // pastured mons to only use free player nests and wild mons to use naturally made nests
            return@firstOrNull when {
                pokemonEntity.tethering?.tetheringId != null -> isPlayerNest && isEggless
                else -> isWildNest && isEggless
            }
        }
    }

    private fun canPathfindToTarget(entity: PathfinderMob, targetPos: BlockPos): Boolean {
        val path = entity.navigation.createPath(targetPos, 0)
        return path != null && path.distToTarget < 1.75f
    }
    private fun allowedNestTypes(entity: PokemonEntity, brain: BiggerBrain): Set<WildNestType> {
        val mate = brain.getMemory(CobblemonMemories.POTENTIAL_MATE) as? PokemonEntity
        val mutable = WildNestTypes.typesFor((mate?.let { CobblemonBreeding.getMother(entity.pokemon, it.pokemon, isSimulated = true) } ?: entity.pokemon).species).toMutableSet()
        if (!entity.pokemon.isWild() && mate != null && !mate.pokemon.isWild()) {
            mutable.add(WildNestType.PLAYER)
            mutable.add(WildNestType.KELP)
        }
        return mutable
    }

    private fun isNestTypeAllowed(
        nestType: WildNestType,
        entity: PokemonEntity,
        allowedTypes: Set<WildNestType>
    ): Boolean {
        if (nestType == WildNestType.PLAYER || nestType == WildNestType.KELP) {
            return !entity.pokemon.isWild() && allowedTypes.contains(nestType)
        }
        return allowedTypes.contains(nestType)
    }

    class Config : SenseConfig {
        var cooldownTicks: ExpressionOrEntityVariable = Either.left("200".asExpression())
        var radiusXZ: ExpressionOrEntityVariable = Either.left("20".asExpression())
        var radiusY: ExpressionOrEntityVariable = Either.left("5".asExpression())

        override fun getVariables(
            entity: LivingEntity,
            behaviourConfigurationContext: BehaviourConfigurationContext
        ) = listOf(cooldownTicks, radiusXZ, radiusY).asVariables()

        override fun create(entity: LivingEntity, behaviourConfigurationContext: BehaviourConfigurationContext): Sense {
            return NestSense(
                cooldownTicks = cooldownTicks.resolveInt(behaviourConfigurationContext.runtime),
                radiusXZ = radiusXZ.resolveInt(behaviourConfigurationContext.runtime),
                radiusY = radiusY.resolveInt(behaviourConfigurationContext.runtime)
            )
        }
    }
}
