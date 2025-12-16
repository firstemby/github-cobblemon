/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.battles.ai.config

import com.cobblemon.mod.common.api.molang.ExpressionLike
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.resources.ResourceLocation
import java.util.Optional

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

    val moveBias: Double? = null,
    val statusMoveBias: Double? = null,
    val switchBias: Double? = null,
    val itemBias: Double? = null,
    val gimmickBias: Double? = null,
    val maxSelectMargin: Double? = null,

    val skill: SkillConfig? = null,

    val teamSynergy: TeamSynergyConfig? = null,
    val prediction: PredictionConfig? = null,
    val personality: PersonalityConfig? = null,

    val actionPriorities: List<ActionPriority>? = null,
    val moveScoringRules: List<MoveScoringRule>? = null,
    val switchConditions: List<SwitchCondition>? = null,
    val itemUsageRules: List<ItemUsageRule>? = null,
    val gimmickRules: List<GimmickRule>? = null,

    val onTurnStart: ExpressionLike? = null,
    val onMoveSelect: ExpressionLike? = null,
    val onSwitchEvaluate: ExpressionLike? = null,
    val onBattleEnd: ExpressionLike? = null
) {
    companion object {
        val CODEC: Codec<BattleAIConfig> = RecordCodecBuilder.create { instance ->
            instance.group(
                ResourceLocation.CODEC.optionalFieldOf("id").forGetter { Optional.ofNullable(it.id) },
                ResourceLocation.CODEC.optionalFieldOf("parent").forGetter { Optional.ofNullable(it.parent) },

                // Base biases
                Codec.DOUBLE.optionalFieldOf("move_bias").forGetter { Optional.ofNullable(it.moveBias) },
                Codec.DOUBLE.optionalFieldOf("status_move_bias").forGetter { Optional.ofNullable(it.statusMoveBias) },
                Codec.DOUBLE.optionalFieldOf("switch_bias").forGetter { Optional.ofNullable(it.switchBias) },
                Codec.DOUBLE.optionalFieldOf("item_bias").forGetter { Optional.ofNullable(it.itemBias) },
                Codec.DOUBLE.optionalFieldOf("gimmick_bias").forGetter { Optional.ofNullable(it.gimmickBias) },
                Codec.DOUBLE.optionalFieldOf("max_select_margin").forGetter { Optional.ofNullable(it.maxSelectMargin) },

                // Skill sub-configuration
                SkillConfig.CODEC.optionalFieldOf("skill").forGetter { Optional.ofNullable(it.skill) },

                // Sub-configurations
                TeamSynergyConfig.CODEC.optionalFieldOf("team_synergy").forGetter { Optional.ofNullable(it.teamSynergy) },
                PredictionConfig.CODEC.optionalFieldOf("prediction").forGetter { Optional.ofNullable(it.prediction) },
                PersonalityConfig.CODEC.optionalFieldOf("personality").forGetter { Optional.ofNullable(it.personality) },

                // Rule lists
                ActionPriority.CODEC.listOf().optionalFieldOf("action_priorities").forGetter { Optional.ofNullable(it.actionPriorities) },
                MoveScoringRule.CODEC.listOf().optionalFieldOf("move_scoring_rules").forGetter { Optional.ofNullable(it.moveScoringRules) }
            ).apply(instance) { id, parent, moveBias, statusMoveBias, switchBias, itemBias, gimmickBias, maxSelectMargin,
                                 skill, teamSynergy, prediction, personality, actionPriorities, moveScoringRules ->
                BattleAIConfig(
                    id = id.orElse(null),
                    parent = parent.orElse(null),
                    moveBias = moveBias.orElse(null),
                    statusMoveBias = statusMoveBias.orElse(null),
                    switchBias = switchBias.orElse(null),
                    itemBias = itemBias.orElse(null),
                    gimmickBias = gimmickBias.orElse(null),
                    maxSelectMargin = maxSelectMargin.orElse(null),
                    skill = skill.orElse(null),
                    teamSynergy = teamSynergy.orElse(null),
                    prediction = prediction.orElse(null),
                    personality = personality.orElse(null),
                    actionPriorities = actionPriorities.orElse(null),
                    moveScoringRules = moveScoringRules.orElse(null)
                )
            }
        }

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
     * Child values override parent values; null child values inherit parent values.
     *
     * @param parentConfig The resolved parent configuration
     * @return A new config with all values resolved
     */
    fun resolveWithParent(parentConfig: BattleAIConfig): BattleAIConfig {
        return BattleAIConfig(
            id = this.id,
            parent = null, // Resolved, no longer needs parent reference
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
            actionPriorities = this.actionPriorities ?: parentConfig.actionPriorities,
            moveScoringRules = this.moveScoringRules ?: parentConfig.moveScoringRules,
            switchConditions = this.switchConditions ?: parentConfig.switchConditions,
            itemUsageRules = this.itemUsageRules ?: parentConfig.itemUsageRules,
            gimmickRules = this.gimmickRules ?: parentConfig.gimmickRules,
            onTurnStart = this.onTurnStart ?: parentConfig.onTurnStart,
            onMoveSelect = this.onMoveSelect ?: parentConfig.onMoveSelect,
            onSwitchEvaluate = this.onSwitchEvaluate ?: parentConfig.onSwitchEvaluate,
            onBattleEnd = this.onBattleEnd ?: parentConfig.onBattleEnd
        )
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
