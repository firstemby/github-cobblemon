/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.config

import com.bedrockk.molang.runtime.MoLangRuntime
import com.cobblemon.mod.common.api.ai.BehaviourConfigurationContext
import com.cobblemon.mod.common.api.ai.ExpressionOrEntityVariable
import com.cobblemon.mod.common.api.molang.ExpressionLike
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMostSpecificMoLangValue
import com.cobblemon.mod.common.api.molang.MoLangFunctions.setup
import com.cobblemon.mod.common.api.npc.configuration.MoLangConfigVariable
import com.cobblemon.mod.common.util.asExpression
import com.cobblemon.mod.common.util.asExpressionLike
import com.cobblemon.mod.common.util.resolve
import com.cobblemon.mod.common.util.withQueryValue
import com.mojang.datafixers.util.Either
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.sensing.SensorType

class CustomScriptBehaviourConfig : BehaviourConfig {
    companion object {
        val runtime = MoLangRuntime().setup()
    }

    var condition: ExpressionOrEntityVariable = Either.left("true".asExpression())
    val variables: List<MoLangConfigVariable> = emptyList()
    val script: ExpressionLike = "0".asExpressionLike()
    val memories = emptySet<MemoryModuleType<*>>()
    val sensors = emptySet<SensorType<*>>()

    override fun getVariables(entity: LivingEntity) = variables
    override fun configure(entity: LivingEntity, behaviourConfigurationContext: BehaviourConfigurationContext) {
        if (!checkCondition(entity, condition)) return
        behaviourConfigurationContext.addMemories(memories)
        behaviourConfigurationContext.addSensors(sensors)
        runtime.withQueryValue("entity", entity.asMostSpecificMoLangValue())
        runtime.resolve(script)
    }
}