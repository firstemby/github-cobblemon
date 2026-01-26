/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.config.objective

import com.cobblemon.mod.common.api.ai.BehaviourConfigurationContext
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.EatGrass
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.FocusDuringBattle
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.GoToSleep
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.Herd
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.LookAtNearbyEntities
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.MaintainFluidY
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.MoveIntoWater
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.Objective
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.StayAfloat
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.StayNearOwner
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.MoveOutOfWater
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.WanderAround
import com.cobblemon.mod.common.api.npc.configuration.MoLangConfigVariable
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.memory.MemoryModuleType

interface ObjectiveConfig {
    companion object {
        val types: MutableMap<ResourceLocation, Class<out ObjectiveConfig>> = mutableMapOf(
            cobblemonResource("focus_during_battle") to FocusDuringBattle.Config::class.java,
            cobblemonResource("look_at_nearby_entities") to LookAtNearbyEntities.Config::class.java,
            cobblemonResource("stay_near_owner") to StayNearOwner.Config::class.java,
            cobblemonResource("wander_around") to WanderAround.Config::class.java,
            cobblemonResource("eat_grass") to EatGrass.Config::class.java,
            cobblemonResource("herd") to Herd.Config::class.java,
            cobblemonResource("stay_afloat") to StayAfloat.Config::class.java,
            cobblemonResource("maintain_fluid_y") to MaintainFluidY.Config::class.java,
            cobblemonResource("move_out_of_water") to MoveOutOfWater.Config::class.java,
            cobblemonResource("move_into_water") to MoveIntoWater.Config::class.java,
            cobblemonResource("go_to_sleep") to GoToSleep.Config::class.java,
            )
    }

    /** The variables that this objective config uses. These are used to declare variables on the entity cleanly. */
    fun getVariables(entity: LivingEntity, behaviourConfigurationContext: BehaviourConfigurationContext): List<MoLangConfigVariable>
    fun create(entity: LivingEntity, behaviourConfigurationContext: BehaviourConfigurationContext): Objective?
}