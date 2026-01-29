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
import com.cobblemon.mod.common.entity.npc.NPCEntity
import com.cobblemon.mod.common.util.asExpression
import com.mojang.datafixers.util.Either
import net.minecraft.world.entity.LivingEntity

/**
 * A sense that checks if an NPC is currently in a battle. Maintains the NPC_BATTLING memory.
 *
 * @author Hiroku
 * @since December 29th, 2025
 */
class NPCBattlingSense(
    var cooldownTicks: Int = 10
) : Sense {
    override var ticksUntilNextExecution: Int = 20
    override val memories = setOf(CobblemonMemories.NPC_BATTLING)

    override fun merge(other: Sense): Boolean {
        if (other is NPCBattlingSense) {
            this.cooldownTicks = minOf(this.cooldownTicks, other.cooldownTicks)
            return true
        } else {
            return false
        }
    }

    override fun tick(entity: LivingEntity, brain: BiggerBrain) {
        if (entity !is NPCEntity) {
            return // Surely they didn't put this on a non-NPC
        }

        val isBattling = entity.isInBattle()
        if (isBattling) {
            brain.setMemoryIfChanged(CobblemonMemories.NPC_BATTLING, value = true)
        } else {
            brain.eraseMemoryIfPresent(CobblemonMemories.NPC_BATTLING)
        }

        ticksUntilNextExecution = cooldownTicks
    }

    class Config : SenseConfig {
        var cooldownTicks: ExpressionOrEntityVariable = Either.left("2".asExpression())

        override fun getVariables(
            entity: LivingEntity,
            behaviourConfigurationContext: BehaviourConfigurationContext
        ) = listOf(cooldownTicks).asVariables()

        override fun create(entity: LivingEntity, behaviourConfigurationContext: BehaviourConfigurationContext): Sense {
            val cooldownTicks = cooldownTicks.resolveInt(behaviourConfigurationContext.runtime)
            return NPCBattlingSense(cooldownTicks = cooldownTicks)
        }
    }
}