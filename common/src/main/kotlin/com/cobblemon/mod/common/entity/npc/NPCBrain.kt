/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.entity.npc

import com.cobblemon.mod.common.api.ai.BrainConfigurationContext
import com.cobblemon.mod.common.api.ai.config.ApplyPresets
import com.cobblemon.mod.common.api.ai.config.BrainConfig
import com.cobblemon.mod.common.api.npc.NPCClass
import net.minecraft.world.entity.ai.Brain

object NPCBrain {
    fun configure(npcEntity: NPCEntity, npcClass: NPCClass, brain: Brain<out NPCEntity>) {
        var brainConfigurations: List<BrainConfig> = npcClass.ai
        if (npcEntity.behavioursAreCustom) {
            brainConfigurations = listOf(ApplyPresets().apply { presets.addAll(npcEntity.behaviours) })
        }

        val ctx = BrainConfigurationContext()
        ctx.apply(npcEntity, brainConfigurations)
        npcEntity.behaviours.clear()
        npcEntity.behaviours.addAll(ctx.appliedBrainPresets)
    }
}