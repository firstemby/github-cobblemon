/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai

import com.cobblemon.mod.common.api.ai.config.BehaviourConfig
import com.cobblemon.mod.common.entity.MoLangScriptingEntity
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.schedule.Activity
import net.minecraft.world.entity.schedule.Schedule

class BehaviourConfigurationContext {
    var appliedBehaviours: Set<ResourceLocation> = setOf()

    var defaultActivity = Activity.IDLE
    var coreActivities = setOf(Activity.CORE)
    val activities = mutableListOf<ActivityConfigurationContext>()
    val schedule = Schedule.EMPTY

    fun getOrCreateActivity(activity: Activity): ActivityConfigurationContext {
        return activities.firstOrNull { it.activity == activity } ?: ActivityConfigurationContext(activity).also(activities::add)
    }

    fun apply(entity: LivingEntity, behaviourConfigs: List<BehaviourConfig>) {
        val brain = entity.brain

        if (entity is MoLangScriptingEntity) {
            entity.registerVariables(behaviourConfigs.flatMap { it.getVariables(entity) })
            entity.initializeScripting()
        }

        // Setup the brain config
        behaviourConfigs.forEach { it.configure(entity, this) }

        // Apply the brain config
        activities.forEach { it.apply(entity) }
        brain.setCoreActivities(coreActivities)
        brain.setDefaultActivity(defaultActivity)
        brain.schedule = schedule
        brain.setActiveActivityIfPossible(defaultActivity)
    }
}