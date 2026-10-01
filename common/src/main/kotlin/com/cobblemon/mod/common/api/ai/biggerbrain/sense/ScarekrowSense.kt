/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.biggerbrain.sense

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.ai.BehaviourConfigurationContext
import com.cobblemon.mod.common.api.ai.ExpressionOrEntityVariable
import com.cobblemon.mod.common.api.ai.asVariables
import com.cobblemon.mod.common.api.ai.biggerbrain.BiggerBrain
import com.cobblemon.mod.common.api.ai.config.sense.SenseConfig
import com.cobblemon.mod.common.api.ai.resolveInt
import com.cobblemon.mod.common.api.tags.CobblemonBlockTags
import com.cobblemon.mod.common.util.asExpression
import com.cobblemon.mod.common.util.distanceTo
import com.cobblemon.mod.common.util.getMemorySafely
import com.mojang.datafixers.util.Either
import net.minecraft.core.BlockPos
import net.minecraft.core.Vec3i
import net.minecraft.world.entity.LivingEntity

class ScarekrowSense(
    private var cooldownTicks: Int = SCAN_INTERVAL
) : Sense {
    override var ticksUntilNextExecution: Int = cooldownTicks
    override val memories = setOf(
        CobblemonMemories.NEARBY_SCAREKROW,
    )

    override fun merge(other: Sense): Boolean {
        if (other is ScarekrowSense) {
            this.cooldownTicks = minOf(this.cooldownTicks, other.cooldownTicks)
            return true
        }
        return false
    }

    override fun tick(entity: LivingEntity, brain: BiggerBrain) {
        ticksUntilNextExecution = cooldownTicks
        val brain = entity.brain

        val searchRadius = Cobblemon.config.scarekrowRadius
        val currPos: BlockPos? = brain.getMemorySafely(CobblemonMemories.NEARBY_SCAREKROW).orElse(null)

        if (currPos != null && entity.level().getBlockState(currPos).`is`(CobblemonBlockTags.SCAREKROWS)) {
            val distance = entity.distanceTo(currPos)
            if (distance > searchRadius) {
                brain.eraseMemory(CobblemonMemories.NEARBY_SCAREKROW)
            }
        }

        val centerPos = entity.blockPosition()
        var scarekrowPos: BlockPos? = null
        var shortestDist = Double.MAX_VALUE
        val world = entity.level()
        BlockPos.betweenClosedStream(
            centerPos.offset(-searchRadius, -searchRadius, -searchRadius),
            centerPos.offset(searchRadius, searchRadius, searchRadius)
        ).forEach { pos ->
            val state = world.getBlockState(pos)
            if (state.`is`(CobblemonBlockTags.SCAREKROWS)) {
                val distance = pos.distSqr(Vec3i(centerPos.x, centerPos.y, centerPos.z))
                if (distance < shortestDist) {
                    scarekrowPos = BlockPos(pos)
                    shortestDist = distance
                }
            }
        }

        if (scarekrowPos != null) {
            brain.setMemory(CobblemonMemories.NEARBY_SCAREKROW, scarekrowPos)
        } else {
            brain.eraseMemory(CobblemonMemories.NEARBY_SCAREKROW)
        }
    }

    class Config : SenseConfig {
        var condition: ExpressionOrEntityVariable = Either.left("true".asExpression())
        var cooldownTicks: ExpressionOrEntityVariable = Either.left("3".asExpression())

        override fun getVariables(
            entity: LivingEntity,
            behaviourConfigurationContext: BehaviourConfigurationContext
        ) = listOf(cooldownTicks).asVariables()

        override fun create(entity: LivingEntity, behaviourConfigurationContext: BehaviourConfigurationContext): Sense {
            val cooldownTicks = cooldownTicks.resolveInt(behaviourConfigurationContext.runtime)
            return ScarekrowSense(
                cooldownTicks = cooldownTicks
            )
        }
    }

    companion object {
        const val SCAN_INTERVAL = 3
    }


}
