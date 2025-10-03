/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.spawning.prospecting

import com.cobblemon.mod.common.CobblemonPoiTypes
import com.cobblemon.mod.common.api.fishing.SpawnBait
import com.cobblemon.mod.common.api.spawning.influence.BucketNormalizingInfluence
import com.cobblemon.mod.common.api.spawning.influence.SpatialSpawningZoneInfluence
import com.cobblemon.mod.common.api.spawning.influence.SpawnBaitInfluence
import com.cobblemon.mod.common.api.spawning.influence.SpawningZoneInfluence
import com.cobblemon.mod.common.api.spawning.influence.UnconditionalSpawningZoneInfluence
import com.cobblemon.mod.common.api.spawning.influence.detector.SpawningInfluenceDetector
import com.cobblemon.mod.common.api.spawning.spawner.Spawner
import com.cobblemon.mod.common.api.spawning.spawner.SpawningZoneInput
import com.cobblemon.mod.common.block.PokeSnackBlock
import com.cobblemon.mod.common.block.entity.PokeSnackBlockEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.net.messages.client.effect.PokeSnackBlockParticlesPacket
import com.cobblemon.mod.common.util.math.pow
import com.cobblemon.mod.common.util.toBlockPos
import kotlin.math.ceil
import kotlin.math.sqrt
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Holder
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.ai.village.poi.PoiManager
import net.minecraft.world.entity.ai.village.poi.PoiType
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import kotlin.math.abs

object LurePokeSnackDetector : SpawningInfluenceDetector {

    @JvmField
    val RANGE: Int = 48

    @JvmField
    val POKE_SNACK_CRUMBED_ASPECT = "poke_snack_crumbed"

    override fun detectFromInput(spawner: Spawner, input: SpawningZoneInput): MutableList<SpawningZoneInfluence> {
        val world = input.world
        val listOfInfluences = mutableListOf<SpawningZoneInfluence>()

        val searchRange = RANGE + ceil(sqrt(((input.length pow 2) + (input.width pow 2)).toDouble())).toInt()
        val lurePokeSnackPositions = world.poiManager.findAll(
            { holder: Holder<PoiType> -> holder.`is`(CobblemonPoiTypes.POKE_SNACK_KEY) },
            { true },
            input.getCenter().toBlockPos(),
            searchRange,
            PoiManager.Occupancy.ANY
        ).toList()

        var highestLureTier = 0

        for (lurePokeSnackPos in lurePokeSnackPositions) {
            val blockState = world.getBlockState(lurePokeSnackPos)
            if (blockState.block !is PokeSnackBlock) continue

            val blockEntity = world.getBlockEntity(lurePokeSnackPos) as? PokeSnackBlockEntity ?: continue
            val baitEffects = blockEntity.getBaitEffects()

            highestLureTier = maxOf(highestLureTier, baitEffects.filter { it.type == SpawnBait.Effects.RARITY_BUCKET }.maxOfOrNull { it.value }?.toInt() ?: 0)

            listOfInfluences.add(
                SpatialSpawningZoneInfluence(
                    pos = lurePokeSnackPos,
                    radius = RANGE.toFloat(),
                    influence = SpawnBaitInfluence(
                        effects = baitEffects,
                        onUsed = { usedAmount, entity ->
                            entity?.let {
                                // Prevent triggering twice on same entity
                                if (usedAmount % 2 == 0) {
                                    tryBite(world, lurePokeSnackPos)
                                    moveAndApplyAspect(world, lurePokeSnackPos, entity)
                                }
                            }
                        }
                    )
                )
            )
        }

        if (highestLureTier > 0) {
            listOfInfluences.add(UnconditionalSpawningZoneInfluence(influence = BucketNormalizingInfluence(tier = highestLureTier)))
        }

        return listOfInfluences
    }

    private fun tryBite(level: ServerLevel, blockPos: BlockPos) {
       if (level.isLoaded(blockPos)) {
           val blockState = level.getBlockState(blockPos)
           val block = blockState.block as? PokeSnackBlock ?: return

           block.eat(level, blockPos, blockState, null)
        }
    }

    private fun moveAndApplyAspect(level: ServerLevel, blockPos: BlockPos, entity: PokemonEntity?) {
        entity?.let {
            entity.pokemon.forcedAspects += POKE_SNACK_CRUMBED_ASPECT
            attemptSafeMove(level, blockPos, entity)
        }
    }

    fun attemptSafeMove(level: ServerLevel, startPos: BlockPos, entity: PokemonEntity) {
        val inWater = entity.isInWater
        val maxDistance = 10  // maximum search radius
        val maxPositions = 10 // maximum number of positions at the closest distance

        var foundPositions: List<BlockPos> = emptyList()

        // Search layer by layer, starting from the closest
        for (distance in 1..maxDistance) {
            val positionsAtThisDistance = mutableListOf<BlockPos>()

            for (dx in -distance..distance) {
                for (dy in -distance..distance) {
                    for (dz in -distance..distance) {
                        // Consider only positions on the surface of the cube at current distance
                        if (listOf(dx, dy, dz).count { abs(it) != 0 } == distance) {
                            val candidatePos = startPos.offset(dx, dy, dz)
                            if (!candidatePos.equals(startPos.relative(Direction.UP))) {
                                // Check collision
                                val currentBox = entity.boundingBox.move(
                                    candidatePos.x - entity.x,
                                    candidatePos.y - entity.y,
                                    candidatePos.z - entity.z
                                )

                                if (level.noCollision(entity, currentBox)) {
                                    val blockBelowPos = candidatePos.below()
                                    val blockState = level.getBlockState(blockBelowPos)

                                    val isWater = blockState.block == Blocks.WATER
                                    if (inWater) {
                                        if (isWater) {
                                            positionsAtThisDistance.add(candidatePos)
                                        }
                                    } else {
                                        if (!blockState.isAir) {
                                            positionsAtThisDistance.add(candidatePos)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (positionsAtThisDistance.isNotEmpty()) {
                // Limit to 10 nearest positions (all at this distance)
                foundPositions = if (positionsAtThisDistance.size > maxPositions) {
                    positionsAtThisDistance.shuffled().take(maxPositions)
                } else {
                    positionsAtThisDistance
                }
                break // Found the nearest layer with safe positions, stop expanding
            }
        }

        if (foundPositions.isNotEmpty()) {
            val chosenPos = foundPositions.random()
            val centerX = chosenPos.x + 0.5
            val centerZ = chosenPos.z + 0.5
            entity.moveTo(Vec3(centerX, chosenPos.y.toDouble(), centerZ))

            PokeSnackBlockParticlesPacket(startPos, chosenPos).sendToPlayersAround(
                startPos.x.toDouble(),
                startPos.y.toDouble(),
                startPos.z.toDouble(),
                64.0,
                level.dimension()
            )
        }
    }

    override fun detectFromBlock(level: ServerLevel, pos: BlockPos, blockState: BlockState) = emptyList<SpawningZoneInfluence>()
}
