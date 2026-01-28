/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.entity.pokemon.ai.tasks

import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.nest.WildNestTypes
import com.cobblemon.mod.common.block.WildNestBlock
import com.cobblemon.mod.common.api.text.green
import com.cobblemon.mod.common.block.entity.NestBlockEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.behavior.BehaviorControl
import net.minecraft.world.entity.ai.behavior.OneShot
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder
import net.minecraft.world.entity.ai.behavior.declarative.Trigger
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.phys.Vec3
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
                    it.registered(MemoryModuleType.WALK_TARGET)
            ).apply(it) { nestMemory, paternalNestMemory, attackTarget, potentialMate, readyToBuildNest, idealLocation, walkTarget ->
                Trigger { world, entity, time ->
                    if (entity !is PathfinderMob || !entity.isAlive) return@Trigger false

                    val mate = entity.brain.getMemory(CobblemonMemories.POTENTIAL_MATE).getOrNull() as? PokemonEntity ?: return@Trigger false
                    val combinedTypes = WildNestTypes.combinedTypes(entity, mate)
                    if (combinedTypes.isEmpty()) return@Trigger false
                    val idealNestPos: BlockPos = entity.brain.getMemory(CobblemonMemories.IDEAL_NEST_LOCATION).getOrNull()
                            ?: return@Trigger false
                    val nestCenter = Vec3.atCenterOf(idealNestPos)

                    // Pokémon is ready and the craft result is valid
                    if (!entity.brain.getMemory(CobblemonMemories.READY_TO_BUILD_NEST).orElse(false)) return@Trigger false

                    // Both Pokémon are close enough to the nest location - we don't want them to be apart :c
                    if (entity.distanceToSqr(nestCenter) > 2.0 || (mate.distanceToSqr(nestCenter)) > 2.0) {
                        return@Trigger false
                    }

                    val placement = WildNestTypes.resolvePlacement(
                        combinedTypes,
                        world.getBlockState(idealNestPos.below()),
                        world.getFluidState(idealNestPos)
                    ) ?: return@Trigger false
                    val nestBlock = WildNestTypes.blockFor(placement.type)
                    val nestState = nestBlock.defaultBlockState()
                        .setValue(WildNestBlock.VARIANT, placement.variant)
                        .setValue(BlockStateProperties.WATERLOGGED, placement.waterlogged)

                    // Place the nest
                    world.setBlock(idealNestPos, nestState, 3)

                    // claim nest so it can be used by these mons once it is built
                    (world.getBlockEntity(idealNestPos) as NestBlockEntity).nestClaimedBy = Pair(entity.uuid, mate.uuid)

                    // Clear memories
                    entity.brain.eraseMemory(CobblemonMemories.READY_TO_BUILD_NEST)
                    entity.brain.eraseMemory(CobblemonMemories.IDEAL_NEST_LOCATION)
                    mate.brain.eraseMemory(CobblemonMemories.READY_TO_BUILD_NEST)
                    mate.brain.eraseMemory(CobblemonMemories.IDEAL_NEST_LOCATION)

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
