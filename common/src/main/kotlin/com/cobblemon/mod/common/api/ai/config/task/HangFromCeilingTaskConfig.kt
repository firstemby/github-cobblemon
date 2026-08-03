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
import com.cobblemon.mod.common.api.ai.ExpressionOrEntityVariable
import com.cobblemon.mod.common.api.ai.asVariables
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.asExpression
import com.cobblemon.mod.common.util.getMemorySafely
import com.mojang.datafixers.util.Either
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.BlockTags
import net.minecraft.tags.TagKey
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.behavior.BehaviorControl
import net.minecraft.world.entity.ai.behavior.OneShot
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState

class HangFromCeilingTaskConfig : SingleTaskConfig {
    val condition: ExpressionOrEntityVariable = Either.left("true".asExpression())

    override fun getVariables(entity: LivingEntity, behaviourConfigurationContext: BehaviourConfigurationContext) =
        listOf(condition).asVariables()

    override fun createTask(
        entity: LivingEntity,
        behaviourConfigurationContext: BehaviourConfigurationContext
    ): BehaviorControl<in LivingEntity>? {
        if (!condition.resolveBoolean(behaviourConfigurationContext.runtime)) return null
        behaviourConfigurationContext.addMemories(
            CobblemonMemories.HANGING_FROM_CEILING,
            CobblemonMemories.RELEASED_FROM_CEILING,
            CobblemonMemories.HANGING_CEILING_ANCHOR,
            MemoryModuleType.WALK_TARGET
        )

        return object : OneShot<LivingEntity>() {
            override fun trigger(serverLevel: ServerLevel, livingEntity: LivingEntity, gameTime: Long): Boolean {
                val pokemon = livingEntity as? PokemonEntity ?: return false
                if (!pokemon.pokemon.isWild()) return false

                if (pokemon.brain.getMemorySafely(CobblemonMemories.RELEASED_FROM_CEILING).orElse(false)) {
                    if (pokemon.brain.hasMemoryValue(CobblemonMemories.HANGING_FROM_CEILING)) {
                        pokemon.releaseFromCeiling()
                    }
                    return false
                }

                val anchor = pokemon.brain.getMemorySafely(CobblemonMemories.HANGING_CEILING_ANCHOR)
                    .orElse(pokemon.blockPosition())
                if (!serverLevel.getBlockState(anchor.above()).isLeaves()) {
                    pokemon.releaseFromCeiling()
                    pokemon.brain.setMemory(CobblemonMemories.RELEASED_FROM_CEILING, true)
                    return false
                }

                pokemon.brain.setMemory(CobblemonMemories.HANGING_FROM_CEILING, true)
                pokemon.brain.setMemory(CobblemonMemories.HANGING_CEILING_ANCHOR, anchor)
                pokemon.brain.eraseMemory(MemoryModuleType.WALK_TARGET)
                (pokemon as? PathfinderMob)?.navigation?.stop()
                pokemon.setPos(anchor.x + 0.5, anchor.y.toDouble(), anchor.z + 0.5)
                pokemon.setDeltaMovement(0.0, 0.0, 0.0)
                pokemon.isNoGravity = true
                return true
            }
        }
    }

    private fun PokemonEntity.releaseFromCeiling() {
        brain.eraseMemory(CobblemonMemories.HANGING_FROM_CEILING)
        brain.eraseMemory(CobblemonMemories.HANGING_CEILING_ANCHOR)
        isNoGravity = getCurrentPoseType() in PoseType.NO_GRAV_POSES && passengers.isEmpty()
    }

    private fun BlockState.isLeaves() = this.`is`(BlockTags.LEAVES) || this.`is`(COMMON_LEAVES)

    companion object {
        private val COMMON_LEAVES: TagKey<Block> =
            TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("c", "leaves"))
    }
}
