/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.config.task

import com.cobblemon.mod.common.api.ai.BehaviourConfigurationContext
import com.cobblemon.mod.common.api.ai.ExpressionOrEntityVariable
import com.cobblemon.mod.common.api.ai.asVariables
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMostSpecificMoLangValue
import com.cobblemon.mod.common.entity.npc.ai.LookAtBattlingPokemonTask
import com.cobblemon.mod.common.util.asExpression
import com.cobblemon.mod.common.util.withQueryValue
import com.mojang.datafixers.util.Either
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.behavior.BehaviorControl

class LookAtBattlingPokemonTaskConfig : SingleTaskConfig {
    companion object {
        const val LOOK_AT_BATTLING_POKEMON = "look_at_battling_pokemon"
    }

    val condition = booleanVariable(SharedEntityVariables.BATTLING_CATEGORY, LOOK_AT_BATTLING_POKEMON, true).asExpressible()
    val minDurationTicks: ExpressionOrEntityVariable = Either.left("40".asExpression())
    val maxDurationTicks: ExpressionOrEntityVariable = Either.left("80".asExpression())

    override fun getVariables(entity: LivingEntity) = listOf(
        condition,
        minDurationTicks,
        maxDurationTicks
    ).asVariables()

    override fun createTask(
        entity: LivingEntity,
        behaviourConfigurationContext: BehaviourConfigurationContext
    ): BehaviorControl<in LivingEntity>? {
        runtime.withQueryValue("entity", entity.asMostSpecificMoLangValue())
        if (!resolveBooleanVariable(LOOK_AT_BATTLING_POKEMON)) return null
        return LookAtBattlingPokemonTask.create(
            minDurationTicks = minDurationTicks.resolveInt(),
            maxDurationTicks = maxDurationTicks.resolveInt()
        )
    }
}