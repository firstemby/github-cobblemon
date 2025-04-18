/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.config.task

import com.bedrockk.molang.runtime.value.DoubleValue
import com.cobblemon.mod.common.api.ai.BehaviourConfigurationContext
import com.cobblemon.mod.common.api.ai.asVariables
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMostSpecificMoLangValue
import com.cobblemon.mod.common.api.npc.configuration.MoLangConfigVariable
import com.cobblemon.mod.common.api.scripting.CobblemonScripts
import com.cobblemon.mod.common.util.asIdentifierDefaultingNamespace
import com.cobblemon.mod.common.util.withQueryValue
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.behavior.BehaviorControl
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder
import net.minecraft.world.entity.ai.behavior.declarative.Trigger
import net.minecraft.world.entity.ai.memory.MemoryModuleType

/**
 * Runs a registered MoLang script (from the molang datapack folder) each tick.
 *
 * It's up to the user to not make expensive ass scripts. Remember you can access the game time
 * from q.entity.level.game_time which can be used with a modulo operator to make something intermittent.
 *
 * @author Hiroku
 * @since December 2nd, 2024
 */
class RunScript : SingleTaskConfig {
    companion object {
        const val SCRIPT_CATEGORY = "script"
    }

    val script = stringVariable(SCRIPT_CATEGORY, "script", "cobblemon:dummy_script").asExpressible()
    val variables = mutableListOf<MoLangConfigVariable>()

    override fun getVariables(entity: LivingEntity) = listOf(script).asVariables() + variables

    override fun createTask(
        entity: LivingEntity,
        behaviourConfigurationContext: BehaviourConfigurationContext
    ): BehaviorControl<in LivingEntity>? = BehaviorBuilder.create {
        it.group(
            it.registered(MemoryModuleType.LOOK_TARGET) // I think I need to have at least something here?
        ).apply(it) { _ ->
            Trigger { world, entity, _ ->
                runtime.withQueryValue("entity", entity.asMostSpecificMoLangValue())
                return@Trigger CobblemonScripts.run(script.resolveString().asIdentifierDefaultingNamespace(), runtime) == DoubleValue.ONE
            }
        }
    }
}