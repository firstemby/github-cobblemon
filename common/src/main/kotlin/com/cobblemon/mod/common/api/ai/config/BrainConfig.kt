/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.config

import com.bedrockk.molang.runtime.MoLangRuntime
import com.cobblemon.mod.common.api.ai.BrainConfigurationContext
import com.cobblemon.mod.common.api.ai.ExpressionOrEntityVariable
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMostSpecificMoLangValue
import com.cobblemon.mod.common.api.molang.MoLangFunctions.setup
import com.cobblemon.mod.common.api.npc.configuration.MoLangConfigVariable
import com.cobblemon.mod.common.util.asExpression
import com.cobblemon.mod.common.util.resolveBoolean
import com.cobblemon.mod.common.util.withQueryValue
import net.minecraft.world.entity.LivingEntity

/**
 * Something that contributes to the construction of an entity's brain. Implementations are expected to make
 * their changes using the provided [BrainConfigurationContext] which helps stage the details of the entity's
 * brain before locking into the highly immutable structure that Mojang uses.
 *
 * A brain config has access to the [LivingEntity] it's for but note that this is, in most cases, an extremely
 * young version of the entity that is not fully initialized and might die if you try getting exotic with it.
 *
 * @see BrainConfigurationContext
 * @author Hiroku
 * @since October 13th, 2024
 */
interface BrainConfig {
    companion object {
        val types = mutableMapOf<String, Class<out BrainConfig>>(
            "script" to ScriptBrainConfig::class.java,
            "add_tasks_to_activity" to AddTasksToActivity::class.java,
            "apply_presets" to ApplyPresets::class.java,
            "set_default_activity" to SetDefaultActivity::class.java,
            "set_core_activities" to SetCoreActivities::class.java,
            "add_variables" to AddVariablesConfig::class.java,
            "set_variables" to SetVariablesConfig::class.java,
        )

    }

    fun checkCondition(entity: LivingEntity, expressionOrEntityVariable: ExpressionOrEntityVariable): Boolean {
        val runtime = MoLangRuntime().setup()
        runtime.withQueryValue("entity", entity.asMostSpecificMoLangValue())
        return runtime.resolveBoolean(expressionOrEntityVariable.map({ it }, { "q.entity.config.${it.variableName}".asExpression() }))
    }

//    fun encode(buffer: RegistryFriendlyByteBuf)
//    fun decode(buffer: RegistryFriendlyByteBuf)
    fun getVariables(entity: LivingEntity): List<MoLangConfigVariable>
    fun configure(entity: LivingEntity, brainConfigurationContext: BrainConfigurationContext)
}
