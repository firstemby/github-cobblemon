/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.config

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonBrainConfigs
import com.cobblemon.mod.common.api.ai.BrainConfigurationContext
import com.cobblemon.mod.common.api.ai.ExpressionOrEntityVariable
import com.cobblemon.mod.common.api.ai.asVariables
import com.cobblemon.mod.common.api.npc.configuration.MoLangConfigVariable
import com.cobblemon.mod.common.util.asExpression
import com.mojang.datafixers.util.Either
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.LivingEntity

class ApplyPresets : BrainConfig {
    val condition: ExpressionOrEntityVariable = Either.left("true".asExpression())
    val presets = mutableListOf<ResourceLocation>()
    override fun getVariables(entity: LivingEntity): List<MoLangConfigVariable> {
        return if (checkCondition(entity, condition)) {
            presets.flatMap {
                CobblemonBrainConfigs.presets[it]?.configurations?.flatMap { it.getVariables(entity) } ?: emptyList()
            } + listOf(condition).asVariables()
        } else {
            listOf(condition).asVariables()
        }
    }

    override fun configure(entity: LivingEntity, brainConfigurationContext: BrainConfigurationContext) {
        if (!checkCondition(entity, condition)) return
        val presetConfigs = presets.flatMap { CobblemonBrainConfigs.presets[it]?.takeIf { it.canBeApplied(entity) }?.configurations ?: return Cobblemon.LOGGER.warn("Preset $it not found") }
        // Why not just add the presets to the context directly?
        // Nested preset application is a thing, and I only want to track the top level presets.
        // i.e. if a preset applies another preset, I don't want to track the inner preset, since it's redundant if we track the top one.
        val originalContextPresets = brainConfigurationContext.appliedBrainPresets.toMutableSet()
        presetConfigs.forEach {
            it.configure(entity, brainConfigurationContext)
        }
        originalContextPresets.addAll(presets)
        brainConfigurationContext.appliedBrainPresets = originalContextPresets
    }
}