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
 * Loaded from datapacks at `data/<namespace>/battle_ai_configs/<name>.json`.
 *
 * Supports inheritance via the [parent] field - child configs override parent values.
 *
 * @property id The unique identifier for this config (set during loading)
 * @property parent Optional parent config to inherit from
 * @property moveBias Base bias for selecting moves (higher = more likely to attack)
 * @property statusMoveBias Bias modifier for status moves
 * @property switchBias Base bias for switching Pokémon
 * @property itemBias Base bias for using items
 * @property gimmickBias Base bias for using gimmicks (Mega, Dynamax, Tera, Z-Move)
 * @property maxSelectMargin Margin for selecting from top-scored actions (0.0-1.0)
 * @property skill Configuration for AI skill/intelligence settings
 * @property teamSynergy Configuration for doubles/multi team synergy
 * @property prediction Configuration for opponent prediction/memory
 * @property personality Configuration for AI personality variance
 * @property actionPriorities Priority rules for action selection
 * @property moveScoringRules Rules for modifying move scores
 * @property switchConditions Conditions that trigger switching consideration
 * @property itemUsageRules Rules for item usage
 * @property gimmickRules Rules for gimmick usage
 * @property onTurnStart Optional Molang script run at turn start
 * @property onMoveSelect Optional Molang script run during move selection
 * @property onSwitchEvaluate Optional Molang script run during switch evaluation
 * @property onBattleEnd Optional Molang script run at battle end
 *
 * @author Amo Aster
 * @since December 2025
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
        /** Default configuration with sensible defaults */
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

    /**
     * Creates a resolved config by merging this config with its parent.
     * - Scalar values: child overrides parent; null child inherits parent.
     * - Rule lists: null = inherit parent list, empty = clear parent rules,
     *   non-empty = child rules + parent rules (child first).
     */
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

    /**
     * Gets the effective move bias with defaults applied.
     */
    fun getEffectiveMoveBias(): Double = moveBias ?: DEFAULT.moveBias!!

    /**
     * Gets the effective status move bias with defaults applied.
     */
    fun getEffectiveStatusMoveBias(): Double = statusMoveBias ?: DEFAULT.statusMoveBias!!

    /**
     * Gets the effective switch bias with defaults applied.
     */
    fun getEffectiveSwitchBias(): Double = switchBias ?: DEFAULT.switchBias!!

    /**
     * Gets the effective item bias with defaults applied.
     */
    fun getEffectiveItemBias(): Double = itemBias ?: DEFAULT.itemBias!!

    /**
     * Gets the effective gimmick bias with defaults applied.
     */
    fun getEffectiveGimmickBias(): Double = gimmickBias ?: DEFAULT.gimmickBias!!

    /**
     * Gets the effective max select margin with defaults applied.
     */
    fun getEffectiveMaxSelectMargin(): Double = maxSelectMargin ?: DEFAULT.maxSelectMargin!!

    /**
     * Gets the effective skill configuration with defaults applied.
     */
    fun getEffectiveSkill(): SkillConfig = skill ?: DEFAULT.skill!!

    /**
     * Gets the effective skill level with defaults applied.
     */
    fun getEffectiveSkillLevel(): Int = getEffectiveSkill().level

    /**
     * Checks if the AI should predict opponent moves with defaults applied.
     */
    fun shouldPredictOpponentMoves(): Boolean = getEffectiveSkill().predictOpponentMoves

    /**
     * Checks if the AI should consider type matchups with defaults applied.
     */
    fun shouldConsiderTypeMatchups(): Boolean = getEffectiveSkill().considerTypeMatchups

    /**
     * Checks if the AI should use damage calculation for move scoring.
     * This is an opt-in feature that can be more accurate but computationally expensive.
     */
    fun shouldUseDamageCalc(): Boolean = getEffectiveSkill().useDamageCalc
}
