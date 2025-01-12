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
import com.cobblemon.mod.common.api.ai.asVariables
import com.cobblemon.mod.common.api.ai.config.task.TaskConfig
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMostSpecificMoLangValue
import com.cobblemon.mod.common.api.molang.MoLangFunctions.setup
import com.cobblemon.mod.common.api.npc.configuration.MoLangConfigVariable
import com.cobblemon.mod.common.util.asExpression
import com.cobblemon.mod.common.util.resolveBoolean
import com.cobblemon.mod.common.util.withQueryValue
import com.mojang.datafixers.util.Either
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.schedule.Activity

class AddTasksToActivity : BrainConfig {
    val activity = Activity.IDLE
    val condition: ExpressionOrEntityVariable = Either.left("true".asExpression())
    val tasksByPriority = mutableMapOf<Int, List<TaskConfig>>()
    override val variables: List<MoLangConfigVariable>
        get() = tasksByPriority.values.flatten().flatMap { it.variables } + listOf(condition).asVariables()


    override fun configure(entity: LivingEntity, brainConfigurationContext: BrainConfigurationContext) {
        val runtime = MoLangRuntime().setup()
        runtime.withQueryValue("entity", entity.asMostSpecificMoLangValue())
        if (!runtime.resolveBoolean(condition.map({ it }, { "q.entity.config.${it.variableName}".asExpression() }))) return

        val activity = brainConfigurationContext.getOrCreateActivity(activity)
        tasksByPriority.forEach { (priority, taskConfigs) ->
            val tasks = taskConfigs.flatMap { it.createTasks(entity, brainConfigurationContext) }
            activity.addTasks(priority, *tasks.toTypedArray())
        }
    }
}