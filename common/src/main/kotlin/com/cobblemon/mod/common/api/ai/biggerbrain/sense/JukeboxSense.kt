/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.biggerbrain.sense

import com.bedrockk.molang.Expression
import com.cobblemon.mod.common.CobblemonPoiTypes
import com.cobblemon.mod.common.api.ai.BehaviourConfigurationContext
import com.cobblemon.mod.common.api.ai.ExpressionOrEntityVariable
import com.cobblemon.mod.common.api.ai.asSimplifiedExpression
import com.cobblemon.mod.common.api.ai.asVariables
import com.cobblemon.mod.common.api.ai.biggerbrain.BiggerBrain
import com.cobblemon.mod.common.api.ai.config.sense.SenseConfig
import com.cobblemon.mod.common.api.ai.resolveDouble
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.net.messages.client.animation.PlayPosableAnimationPacket
import com.cobblemon.mod.common.util.asExpression
import com.cobblemon.mod.common.util.resolveBoolean
import com.mojang.datafixers.util.Either
import net.minecraft.core.Holder
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.village.poi.PoiManager
import net.minecraft.world.entity.ai.village.poi.PoiType
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.JukeboxBlockEntity

/**
 * Senses nearby jukeboxes that are currently playing music.
 *
 */
class JukeboxSense(
    var condition: Expression,
    var radiusXZ: Double = 16.0,
    var radiusY: Double = 16.0,
    var cooldownTicks: Int = 20 * 5
) : Sense {
    override var ticksUntilNextExecution: Int = 50
    override val memories = setOf(MemoryModuleType.DANCING)

    override fun merge(other: Sense): Boolean {
        if (other is JukeboxSense) {
            this.condition = other.condition
            this.cooldownTicks = minOf(this.cooldownTicks, other.cooldownTicks)
            this.radiusXZ = other.radiusXZ
            this.radiusY = other.radiusY
            return true
        } else {
            return false
        }
    }

    override fun tick(entity: LivingEntity, brain: BiggerBrain) {

        ticksUntilNextExecution = cooldownTicks
        if (!brain.runtime.resolveBoolean(condition)) {
            return
        }
        val world = entity.level()
        val poiManager = (entity.level() as ServerLevel).poiManager
        val jukeboxPositions = poiManager.findAll(
            { holder: Holder<PoiType> -> holder.`is`(CobblemonPoiTypes.JUKEBOX_KEY) },
            { true },
            entity.blockPosition(),
            10,
            PoiManager.Occupancy.ANY
        ).toList()

        val jukeboxPos = jukeboxPositions.firstOrNull() { blockPos ->
            if (world.getBlockState(blockPos).block != Blocks.JUKEBOX) return@firstOrNull false
            val blockEntity =  world.getBlockEntity(blockPos)
            if (blockEntity !is JukeboxBlockEntity) return@firstOrNull false
            !blockEntity.theItem.isEmpty
        }
        brain.setMemory(MemoryModuleType.DANCING, jukeboxPos != null)
    }

    class Config : SenseConfig {
        var condition: ExpressionOrEntityVariable = Either.left("true".asExpression())
        var radiusXZ: ExpressionOrEntityVariable = Either.left("16.0".asExpression())
        var radiusY: ExpressionOrEntityVariable = Either.left("16.0".asExpression())
        var cooldownTicks: ExpressionOrEntityVariable = Either.left("60".asExpression())

        override fun getVariables(
            entity: LivingEntity,
            behaviourConfigurationContext: BehaviourConfigurationContext
        ) = listOf(
            condition,
            radiusXZ,
            radiusY,
            cooldownTicks
        ).asVariables()

        override fun create(entity: LivingEntity, behaviourConfigurationContext: BehaviourConfigurationContext): Sense {
            return JukeboxSense(
                condition = condition.asSimplifiedExpression(entity),
                radiusXZ = radiusXZ.resolveDouble(behaviourConfigurationContext.runtime),
                radiusY = radiusY.resolveDouble(behaviourConfigurationContext.runtime),
                cooldownTicks = cooldownTicks.resolveDouble(behaviourConfigurationContext.runtime).toInt()
            )
        }
    }
}