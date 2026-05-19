/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.config.objective

import com.cobblemon.mod.common.api.ai.BehaviourConfigurationContext
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.AggressiveBehaviour
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.CircleAround
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.AvoidTarget
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.Dance
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.DefendOwner
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.Defensive
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.EatGrass
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.EatHeldItem
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.FocusDuringBattle
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.FriendlyBehaviour
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.GatherNectar
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.GoToSleep
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.HarvestSweetBerryBush
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.Herd
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.LookAtNearbyEntities
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.MaintainFluidY
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.MoveIntoWater
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.Objective
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.StayAfloat
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.StayNearOwner
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.MoveOutOfWater
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.MoveToRestingPlace
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.PickupItem
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.WanderAround
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.JumpOutOfWater
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.NeutralBehaviour
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.PointToSpawn
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.SkittishBehaviour
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.TerritorialBehaviour
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.WalkTowardsParent
import com.cobblemon.mod.common.api.npc.configuration.MoLangConfigVariable
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.LivingEntity

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
            cobblemonResource("jump_out_of_water") to JumpOutOfWater.Config::class.java,
            cobblemonResource("move_to_resting_place") to MoveToRestingPlace.Config::class.java,
            cobblemonResource("go_to_sleep") to GoToSleep.Config::class.java,
            cobblemonResource("harvest_sweet_berry_bush") to HarvestSweetBerryBush.Config::class.java,
            cobblemonResource("pickup_item") to PickupItem.Config::class.java,
            cobblemonResource("eat_held_item") to EatHeldItem.Config::class.java,
            cobblemonResource("gather_nectar") to GatherNectar.Config::class.java,
            cobblemonResource("dance") to Dance.Config::class.java,
            cobblemonResource("defend_owner") to DefendOwner.Config::class.java,
            cobblemonResource("point_to_spawn") to PointToSpawn.Config::class.java,
            cobblemonResource("circle_around") to CircleAround.Config::class.java,
            cobblemonResource("walk_to_parent") to WalkTowardsParent.Config::class.java,
            cobblemonResource("avoid_target") to AvoidTarget.Config::class.java,
            cobblemonResource("combat_defensive") to Defensive.Config::class.java,
            cobblemonResource("combat_aggressive") to AggressiveBehaviour.Config::class.java,
            cobblemonResource("combat_territorial") to TerritorialBehaviour.Config::class.java,
            cobblemonResource("combat_neutral") to NeutralBehaviour.Config::class.java,
            cobblemonResource("combat_skittish") to SkittishBehaviour.Config::class.java,
            cobblemonResource("behaviour_friendly") to FriendlyBehaviour.Config::class.java,


            )
    }

    /** The variables that this objective config uses. These are used to declare variables on the entity cleanly. */
    fun getVariables(entity: LivingEntity, behaviourConfigurationContext: BehaviourConfigurationContext): List<MoLangConfigVariable>
    fun create(entity: LivingEntity, behaviourConfigurationContext: BehaviourConfigurationContext): Objective?
}