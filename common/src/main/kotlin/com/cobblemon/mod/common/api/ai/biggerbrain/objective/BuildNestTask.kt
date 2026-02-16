/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.biggerbrain.objective

import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.ai.biggerbrain.BiggerBrain
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.Objective
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.ObjectiveStatus
import com.cobblemon.mod.common.api.nest.WildNestTypes
import com.cobblemon.mod.common.api.text.green
import com.cobblemon.mod.common.block.WildNestBlock
import com.cobblemon.mod.common.block.entity.NestBlockEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.phys.Vec3
import kotlin.jvm.optionals.getOrNull

class BuildNestTask : Objective {
    override fun getDescription(entity: LivingEntity) = "Build nest"

    override fun perform(
        entity: LivingEntity,
        brain: BiggerBrain,
        activeObjectiveTags: MutableSet<String>
    ): ObjectiveStatus {
        if (entity !is PokemonEntity || !entity.isAlive) return ObjectiveStatus.INVALID

        if (brain.getMemory(CobblemonMemories.NEST_LOCATION) != null) {
            brain.sendDebugMessage("[BuildNest] Nest already assigned; skipping.")
            return ObjectiveStatus.INVALID
        }
        if (brain.getMemory(CobblemonMemories.PATERNAL_NEST_LOCATION) != null) {
            brain.sendDebugMessage("[BuildNest] Paternal nest already set; skipping.")
            return ObjectiveStatus.INVALID
        }
        if (brain.hasMemoryValue(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET)) {
            brain.sendDebugMessage("[BuildNest] In combat; skipping.")
            return ObjectiveStatus.INVALID
        }

        val mate = brain.getMemory(CobblemonMemories.POTENTIAL_MATE) as? PokemonEntity ?: return ObjectiveStatus.INVALID
        val combinedTypes = WildNestTypes.combinedTypes(entity, mate)
        if (combinedTypes.isEmpty()) {
            brain.sendDebugMessage("[BuildNest] No combined nest types; skipping.")
            return ObjectiveStatus.INVALID
        }

        val idealNestPos: BlockPos = brain.getMemory(CobblemonMemories.IDEAL_NEST_LOCATION)
            ?: return ObjectiveStatus.INVALID
        val nestCenter = Vec3.atCenterOf(idealNestPos)

        if (brain.getMemory(CobblemonMemories.READY_TO_BUILD_NEST) != true) {
            brain.sendDebugMessage("[BuildNest] Not ready to build; skipping.")
            return ObjectiveStatus.INVALID
        }

        if (entity.distanceToSqr(nestCenter) > 2.0 || mate.distanceToSqr(nestCenter) > 2.0) {
            brain.sendDebugMessage("[BuildNest] Parents not in range of ideal nest $idealNestPos.")
            return ObjectiveStatus.INACTIVE
        }

        val world = entity.level() as? ServerLevel ?: return ObjectiveStatus.INVALID
        val placement = WildNestTypes.resolvePlacement(
            combinedTypes,
            world.getBlockState(idealNestPos.below()),
            world.getFluidState(idealNestPos)
        ) ?: return ObjectiveStatus.INVALID

        val nestBlock = WildNestTypes.blockFor(placement.type)
        val nestState = nestBlock.defaultBlockState()
            .setValue(WildNestBlock.VARIANT, placement.variant)
            .setValue(BlockStateProperties.WATERLOGGED, placement.waterlogged)

        world.setBlock(idealNestPos, nestState, 3)
        (world.getBlockEntity(idealNestPos) as NestBlockEntity).nestClaimedBy = Pair(entity.uuid, mate.uuid)
        brain.sendDebugMessage("[BuildNest] Built nest at $idealNestPos with ${placement.type}/${placement.variant}.")

        brain.eraseMemory(CobblemonMemories.READY_TO_BUILD_NEST)
        brain.eraseMemory(CobblemonMemories.IDEAL_NEST_LOCATION)
        mate.biggerBrain.eraseMemory(CobblemonMemories.READY_TO_BUILD_NEST)
        mate.biggerBrain.eraseMemory(CobblemonMemories.IDEAL_NEST_LOCATION)

        world.players().forEach { player ->
            player.displayClientMessage("A Pokémon has built a nest!".green(), true)
        }

        return ObjectiveStatus.ACTIVE
    }
}
