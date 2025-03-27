/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.config.task

import com.bedrockk.molang.runtime.MoLangRuntime
import com.cobblemon.mod.common.api.ai.BrainConfigurationContext
import com.cobblemon.mod.common.api.ai.ExpressionOrEntityVariable
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMostSpecificMoLangValue
import com.cobblemon.mod.common.api.molang.MoLangFunctions.setup
import com.cobblemon.mod.common.api.npc.configuration.MoLangConfigVariable
import com.cobblemon.mod.common.util.asExpression
import com.cobblemon.mod.common.util.lang
import com.cobblemon.mod.common.util.resolveBoolean
import com.cobblemon.mod.common.util.resolveDouble
import com.cobblemon.mod.common.util.resolveFloat
import com.cobblemon.mod.common.util.resolveInt
import com.cobblemon.mod.common.util.resolveString
import com.cobblemon.mod.common.util.withQueryValue
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.behavior.BehaviorControl

/**
 * A configuration for a brain task. Its purpose is to generate a list of tasks to add to the brain of
 * an entity when it spawns.
 *
 * This is essentially a builder for tasks.
 *
 * @author Hiroku
 * @since October 14th, 2024
 */
interface TaskConfig {
    companion object {
        val types = mutableMapOf<String, Class<out TaskConfig>>(
            "one_of" to OneOfTaskConfig::class.java,
            "wander" to WanderTaskConfig::class.java,
            "water_wander" to WaterWanderTaskConfig::class.java,
            "air_wander" to AirWanderTaskConfig::class.java,
            "look_at_target" to LookAtTargetTaskConfig::class.java,
            "follow_walk_target" to FollowWalkTargetTaskConfig::class.java,
            "random" to RandomTaskConfig::class.java,
            "stay_afloat" to StayAfloatTaskConfig::class.java,
            "look_at_entities" to LookAtEntitiesTaskConfig::class.java,
            "do_nothing" to DoNothingTaskConfig::class.java,
            "get_angry_at_attacker" to GetAngryAtAttackerTaskConfig::class.java,
            "stop_being_angry_if_attacker_dead" to StopBeingAngryIfAttackerDeadTaskConfig::class.java,
            "switch_npc_to_battle" to SwitchToNPCBattleTaskConfig::class.java,
            "look_at_battling_pokemon" to LookAtBattlingPokemonTaskConfig::class.java,
            "switch_npc_from_battle" to SwitchFromNPCBattleTaskConfig::class.java,
            "switch_pokemon_to_battle" to SwitchToPokemonBattleTaskConfig::class.java,
            "look_at_targeted_battle_pokemon" to LookAtTargetedBattlePokemonTaskConfig::class.java,
            "switch_pokemon_from_battle" to SwitchFromPokemonBattleTaskConfig::class.java,
            "go_to_healing_machine" to GoToHealingMachineTaskConfig::class.java,
            "heal_using_healing_machine" to HealUsingHealingMachineTaskConfig::class.java,
            "all_of" to AllOfTaskConfig::class.java,
            "attack_angry_at" to AttackAngryAtTaskConfig::class.java,
            "move_to_attack_target" to MoveToAttackTargetTaskConfig::class.java,
            "melee_attack" to MeleeAttackTaskConfig::class.java,
            "switch_from_fight" to SwitchFromFightTaskConfig::class.java,
            "switch_to_fight" to SwitchToFightTaskConfig::class.java,
            "switch_to_chatting" to SwitchToChattingTaskConfig::class.java,
            "switch_from_chatting" to SwitchFromChattingTaskConfig::class.java,
            "look_at_speaker" to LookAtSpeakerTaskConfig::class.java,
            "switch_to_action_effect" to SwitchToActionEffectTaskConfig::class.java,
            "switch_from_action_effect" to SwitchFromActionEffectTaskConfig::class.java,
            "exit_battle_when_hurt" to ExitBattleWhenHurtTaskConfig::class.java,
            "switch_to_panic_when_hurt" to SwitchToPanicWhenHurtTaskConfig::class.java,
            "switch_to_panic_when_hostiles_nearby" to SwitchToPanicWhenHostilesNearbyTaskConfig::class.java,
            "calm_down" to CalmDownTaskConfig::class.java,
            "flee_attacker" to FleeAttackerTaskConfig::class.java,
            "flee_nearest_hostile" to FleeNearestHostileTaskConfig::class.java,
            "run_script" to RunScript::class.java,
            "look_in_direction" to LookInDirectionTaskConfig::class.java,
            "wake_up" to WakeUpTaskConfig::class.java,
            "go_to_sleep" to GoToSleepTaskConfig::class.java,
            "find_resting_place" to FindRestingPlaceTaskConfig::class.java,
            "move_to_owner" to MoveToOwnerTaskConfig::class.java,
            "switch_to_sleep_on_trainer_bed" to SwitchToSleepOnTrainerBedTaskConfig::class.java,
            "switch_from_sleep_on_trainer_bed" to SwitchFromSleepOnTrainerBedTaskConfig::class.java,
            "sleep_if_on_trainer_bed" to SleepIfOnTrainerBedTaskConfig::class.java,
            "point_to_spawn" to PointToSpawnTaskConfig::class.java,
            "eat_grass" to EatGrassTaskConfig::class.java,
            "find_air" to FindAirTaskConfig::class.java,
            "go_to_land" to GoToLandTaskConfig::class.java
        )

        val runtime = MoLangRuntime().setup()
    }

    val runtime: MoLangRuntime
        get() = Companion.runtime

    fun checkCondition(entity: LivingEntity, expressionOrEntityVariable: ExpressionOrEntityVariable): Boolean {
        runtime.withQueryValue("entity", entity.asMostSpecificMoLangValue())
        return expressionOrEntityVariable.resolveBoolean()
    }

    fun ExpressionOrEntityVariable.asExpression() = map({ it }, { "q.entity.config.${it.variableName}".asExpression() })
    fun ExpressionOrEntityVariable.resolveString() = runtime.resolveString(asExpression())
    fun ExpressionOrEntityVariable.resolveBoolean() = runtime.resolveBoolean(asExpression())
    fun ExpressionOrEntityVariable.resolveInt() = runtime.resolveInt(asExpression())
    fun ExpressionOrEntityVariable.resolveDouble() = runtime.resolveDouble(asExpression())
    fun ExpressionOrEntityVariable.resolveFloat() = runtime.resolveFloat(asExpression())

    private fun variable(category: String, name: String, type: MoLangConfigVariable.MoLangVariableType, default: String) = MoLangConfigVariable(
        variableName = name,
        category = lang("entity.variable.category.$category"),
        displayName = lang("entity.variable.$name.name"),
        description = lang("entity.variable.$name.desc"),
        type = type,
        defaultValue = default
    )

    fun stringVariable(category: String, name: String, default: String) = variable(category = category, name = name, type = MoLangConfigVariable.MoLangVariableType.TEXT, default = default)
    fun numberVariable(category: String, name: String, default: Number) = variable(category = category, name = name, type = MoLangConfigVariable.MoLangVariableType.NUMBER, default = default.toString())
    fun booleanVariable(category: String, name: String, default: Boolean) = variable(category = category, name = name, type = MoLangConfigVariable.MoLangVariableType.BOOLEAN, default = default.toString())

    fun getVariableExpression(name: String) = "q.entity.config.$name".asExpression()
    fun resolveStringVariable(name: String) = runtime.resolveString(getVariableExpression(name))
    fun resolveBooleanVariable(name: String) = runtime.resolveBoolean(getVariableExpression(name))
    fun resolveNumberVariable(name: String) = runtime.resolveDouble(getVariableExpression(name))

    /** The variables that this task config uses. These are used to declare variables on the entity cleanly. */
    fun getVariables(entity: LivingEntity): List<MoLangConfigVariable>
    /** Given the entity in construction, returns a list of tasks. */
    fun createTasks(entity: LivingEntity, brainConfigurationContext: BrainConfigurationContext): List<BehaviorControl<in LivingEntity>>
}