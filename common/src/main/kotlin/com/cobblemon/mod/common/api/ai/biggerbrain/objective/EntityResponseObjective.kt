/*
 * Copyright (C) 2026 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.biggerbrain.objective

import com.bedrockk.molang.Expression
import com.cobblemon.mod.common.api.ai.biggerbrain.BiggerBrain
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMostSpecificMoLangValue
import com.cobblemon.mod.common.util.asExpression
import com.cobblemon.mod.common.util.mainThreadRuntime
import com.cobblemon.mod.common.util.resolveBoolean
import com.cobblemon.mod.common.util.resolveFloat
import com.cobblemon.mod.common.util.resolveString
import com.cobblemon.mod.common.util.withQueryValue
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities


abstract class EntityResponseObjective(
    val speedMultiplier: Expression = "0.5".asExpression(), // How do we transfer speed to the attack behaviour? Do we want to?
    val engageRange: Expression = "15.0".asExpression(),
    val disengageRange: Expression = "20.0".asExpression(),
    val condition: Expression = "true".asExpression(), // Self/environment condition check
    val targetCondition: Expression = "true".asExpression(), // target condition (will be checked on entity interaction)
    description: Expression = "'EntityResponseObjective'".asExpression(),
) : Objective {
    var trackedEntity: LivingEntity? = null
    private val objectiveDescription: String = mainThreadRuntime.resolveString(description)

    override fun getDescription(entity: LivingEntity) = objectiveDescription


    open fun scanForValidTargets(entity: LivingEntity, brain: BiggerBrain): LivingEntity? {
        // Check if we're in the mood to fight
        if (mainThreadRuntime.withQueryValue("entity", (entity).asMostSpecificMoLangValue())
                .resolveBoolean(condition)
        ) {
            // Scan visible livingEntities
            val entities: NearestVisibleLivingEntities =
                brain.getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES) ?: return null
            val targetEntity = entities.findClosest { it ->
                val runtime = mainThreadRuntime.withQueryValue("target", (it).asMostSpecificMoLangValue())
                val isValidTarget =
                    runtime.resolveBoolean(targetCondition) && runtime.resolveFloat(engageRange) > entity.distanceTo(it)
                isValidTarget
            }.orElse(null)
            if (targetEntity != null) return targetEntity
        }
        return null
    }

    open fun shouldContinue(entity: LivingEntity, brain: BiggerBrain): Boolean {
        trackedEntity ?: return false
        val runtime = mainThreadRuntime.withQueryValue("entity", entity.asMostSpecificMoLangValue())
            .withQueryValue("target", (trackedEntity!!).asMostSpecificMoLangValue())
        return (runtime.resolveBoolean(condition) // self condition
                && runtime.resolveBoolean(targetCondition) // target condition
                && (runtime.resolveFloat(disengageRange) > entity.distanceTo(trackedEntity!!)))
    }
}
