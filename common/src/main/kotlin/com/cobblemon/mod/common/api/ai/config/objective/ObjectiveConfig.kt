/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.config.objective

import com.cobblemon.mod.common.api.ai.BehaviourConfigurationContext
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.Objective
import com.cobblemon.mod.common.api.npc.configuration.MoLangConfigVariable
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.LivingEntity

interface ObjectiveConfig {
    companion object {
        val types: MutableMap<ResourceLocation, Class<out ObjectiveConfig>> = mutableMapOf(
            cobblemonResource("respect_busy_locks") to RespectBusyLocksConfig::class.java,
            cobblemonResource("focus_during_battle") to FocusDuringBattleConfig::class.java,
            cobblemonResource("look_at_nearby_entities") to LookAtNearbyEntitiesConfig::class.java,
            cobblemonResource("stay_near_owner") to StayNearOwnerConfig::class.java,
            cobblemonResource("wander_around") to WanderAroundConfig::class.java
        )
    }

    /** The variables that this objective config uses. These are used to declare variables on the entity cleanly. */
    fun getVariables(entity: LivingEntity, behaviourConfigurationContext: BehaviourConfigurationContext): List<MoLangConfigVariable>
    fun create(entity: LivingEntity, behaviourConfigurationContext: BehaviourConfigurationContext): Objective?
}