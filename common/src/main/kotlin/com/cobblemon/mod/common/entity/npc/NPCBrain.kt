/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.entity.npc

import com.cobblemon.mod.common.api.ai.BehaviourConfigurationContext
import com.cobblemon.mod.common.api.ai.config.ApplyBehaviours
import com.cobblemon.mod.common.api.ai.config.BehaviourConfig
import com.cobblemon.mod.common.api.npc.NPCClass
import net.minecraft.world.entity.ai.Brain

object NPCBrain {
    fun configure(npcEntity: NPCEntity, npcClass: NPCClass, brain: Brain<out NPCEntity>) {
        var behaviourConfigurations: List<BehaviourConfig> = npcClass.behaviours
        if (npcEntity.behavioursAreCustom) {
            behaviourConfigurations = listOf(ApplyBehaviours().apply { behaviours.addAll(npcEntity.behaviours) })
        }

        val ctx = BehaviourConfigurationContext()
        ctx.apply(npcEntity, behaviourConfigurations)
        npcEntity.behaviours.clear()
        npcEntity.behaviours.addAll(ctx.appliedBehaviours)
    }
}