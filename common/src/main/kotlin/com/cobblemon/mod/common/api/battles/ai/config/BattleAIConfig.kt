/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.battles.ai.config

import com.cobblemon.mod.common.api.molang.ExpressionLike
import com.google.gson.annotations.SerializedName
import net.minecraft.resources.ResourceLocation

/**
 * Main configuration class for the configurable battle AI system.
 * Supports inheritance via the [parent] field - child configs override parent values.
 */
data class BattleAIConfig(
    val id: ResourceLocation? = null,
    val parent: ResourceLocation? = null,

    @SerializedName("move_bias")
    val moveBias: Double? = null,
    @SerializedName("status_move_bias")
    val statusMoveBias: Double? = null,
    @SerializedName("switch_bias")
    val switchBias: Double? = null,
    @SerializedName("item_bias")
    val itemBias: Double? = null,
    @SerializedName("gimmick_bias")
    val gimmickBias: Double? = null,
    @SerializedName("max_select_margin")
    val maxSelectMargin: Double? = null,

    val skill: SkillConfig? = null,

    @SerializedName("team_synergy")
    val teamSynergy: TeamSynergyConfig? = null,
    val prediction: PredictionConfig? = null,
    val personality: PersonalityConfig? = null,

    @SerializedName("action_priorities")
    val actionPriorities: List<ActionPriority>? = null,
    @SerializedName("move_scoring_rules")
    val moveScoringRules: List<MoveScoringRule>? = null,
    @SerializedName("switch_conditions")
    val switchConditions: List<SwitchCondition>? = null,
    @SerializedName("item_usage_rules")
    val itemUsageRules: List<ItemUsageRule>? = null,
    @SerializedName("gimmick_rules")
    val gimmickRules: List<GimmickRule>? = null,

    @SerializedName("on_turn_start")
    val onTurnStart: ExpressionLike? = null,
    @SerializedName("on_move_select")
    val onMoveSelect: ExpressionLike? = null,
    @SerializedName("on_switch_evaluate")
    val onSwitchEvaluate: ExpressionLike? = null,
    @SerializedName("on_battle_end")
    val onBattleEnd: ExpressionLike? = null
) {
    companion object {
        val DEFAULT = BattleAIConfig(
            moveBias = 1.0,
            statusMoveBias = 0.85,
            switchBias = 0.85,
            itemBias = 0.85,
            gimmickBias = 1.0,
            maxSelectMargin = 0.25,
            skill = SkillConfig.DEFAULT,
            teamSynergy = TeamSynergyConfig.DEFAULT,
            prediction = PredictionConfig.DEFAULT,
            personality = PersonalityConfig.DEFAULT,
            actionPriorities = emptyList(),
            moveScoringRules = emptyList(),
            switchConditions = emptyList(),
            itemUsageRules = emptyList(),
            gimmickRules = emptyList()
        )
    }

    /** Merges this config with the given parent, with child values taking precedence. */
    fun resolveWithParent(parentConfig: BattleAIConfig): BattleAIConfig {
        return BattleAIConfig(
            id = this.id,
            parent = null,
            moveBias = this.moveBias ?: parentConfig.moveBias,
            statusMoveBias = this.statusMoveBias ?: parentConfig.statusMoveBias,
            switchBias = this.switchBias ?: parentConfig.switchBias,
            itemBias = this.itemBias ?: parentConfig.itemBias,
            gimmickBias = this.gimmickBias ?: parentConfig.gimmickBias,
            maxSelectMargin = this.maxSelectMargin ?: parentConfig.maxSelectMargin,
            skill = this.skill ?: parentConfig.skill,
            teamSynergy = this.teamSynergy ?: parentConfig.teamSynergy,
            prediction = this.prediction ?: parentConfig.prediction,
            personality = this.personality ?: parentConfig.personality,
            actionPriorities = mergeRuleLists(this.actionPriorities, parentConfig.actionPriorities),
            moveScoringRules = mergeRuleLists(this.moveScoringRules, parentConfig.moveScoringRules),
            switchConditions = mergeRuleLists(this.switchConditions, parentConfig.switchConditions),
            itemUsageRules = mergeRuleLists(this.itemUsageRules, parentConfig.itemUsageRules),
            gimmickRules = mergeRuleLists(this.gimmickRules, parentConfig.gimmickRules),
            onTurnStart = this.onTurnStart ?: parentConfig.onTurnStart,
            onMoveSelect = this.onMoveSelect ?: parentConfig.onMoveSelect,
            onSwitchEvaluate = this.onSwitchEvaluate ?: parentConfig.onSwitchEvaluate,
            onBattleEnd = this.onBattleEnd ?: parentConfig.onBattleEnd
        )
    }

    private fun <T> mergeRuleLists(child: List<T>?, parent: List<T>?): List<T>? {
        if (child == null) return parent
        if (child.isEmpty()) return child
        return child + (parent ?: emptyList())
    }

    fun getEffectiveMoveBias(): Double = moveBias ?: DEFAULT.moveBias!!
    fun getEffectiveStatusMoveBias(): Double = statusMoveBias ?: DEFAULT.statusMoveBias!!
    fun getEffectiveSwitchBias(): Double = switchBias ?: DEFAULT.switchBias!!
    fun getEffectiveItemBias(): Double = itemBias ?: DEFAULT.itemBias!!
    fun getEffectiveGimmickBias(): Double = gimmickBias ?: DEFAULT.gimmickBias!!
    fun getEffectiveMaxSelectMargin(): Double = maxSelectMargin ?: DEFAULT.maxSelectMargin!!
    fun getEffectiveSkill(): SkillConfig = skill ?: DEFAULT.skill!!
    fun getEffectiveSkillLevel(): Int = getEffectiveSkill().level
    fun shouldPredictOpponentMoves(): Boolean = getEffectiveSkill().predictOpponentMoves
    fun shouldConsiderTypeMatchups(): Boolean = getEffectiveSkill().considerTypeMatchups
    fun shouldUseDamageCalc(): Boolean = getEffectiveSkill().useDamageCalc
}
