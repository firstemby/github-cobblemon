/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battles.ai.debug

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.util.UUID

/**
 * Captures the complete decision-making process of the ConfigurableBattleAI
 * for visualization in the debug webpage.
 *
 * @author Amo Aster
 * @since December 2025
 */
data class AIDecisionLog(
    val timestamp: Long = System.currentTimeMillis(),
    val battleId: String,
    val turn: Int,
    val configId: String,
    val activePokemon: PokemonSnapshot,
    val opponents: List<PokemonSnapshot>,
    val allies: List<PokemonSnapshot>,
    val personality: PersonalitySnapshot,
    val battleState: BattleStateSnapshot,
    val moveEvaluations: MutableList<MoveEvaluation> = mutableListOf(),
    val switchEvaluations: MutableList<SwitchEvaluation> = mutableListOf(),
    val itemEvaluations: MutableList<ItemEvaluation> = mutableListOf(),
    val thinkingSteps: MutableList<ThinkingStep> = mutableListOf(),
    var finalDecision: FinalDecision? = null
) {
    companion object {
        val GSON: Gson = GsonBuilder().setPrettyPrinting().create()
    }

    fun toJson(): String = GSON.toJson(this)
}

/**
 * Snapshot of a Pokemon's current state.
 */
data class PokemonSnapshot(
    val uuid: String,
    val species: String,
    val form: String?,
    val nickname: String?,
    val level: Int,
    val currentHp: Int,
    val maxHp: Int,
    val hpPercent: Double,
    val status: String?,
    val types: List<String>,
    val ability: String?,
    val heldItem: String?,
    val moves: List<MoveSnapshot>,
    val stats: StatsSnapshot?,
    val boosts: Map<String, Int>? = null,
    val isActive: Boolean = false,
    val side: String? = null
)

/**
 * Snapshot of a move's current state.
 */
data class MoveSnapshot(
    val id: String,
    val displayName: String,
    val type: String,
    val category: String,
    val power: Int,
    val accuracy: Int,
    val pp: Int,
    val maxPp: Int,
    val priority: Int,
    val canBeUsed: Boolean
)

/**
 * Stats snapshot for a Pokemon.
 */
data class StatsSnapshot(
    val hp: Int,
    val atk: Int,
    val def: Int,
    val spa: Int,
    val spd: Int,
    val spe: Int
)

/**
 * Personality traits snapshot.
 */
data class PersonalitySnapshot(
    val aggression: Double,
    val riskTolerance: Double,
    val setupPreference: Double,
    val switchiness: Double,
    val itemConservatism: Double
)

/**
 * Current battle state snapshot.
 */
data class BattleStateSnapshot(
    val weather: String?,
    val terrain: String?,
    val trickRoom: Boolean,
    val sideConditions: Map<String, List<String>>,
    val availableSwitches: Int,
    val canUseItems: Boolean
)

/**
 * Evaluation of a single move option.
 */
data class MoveEvaluation(
    val moveId: String,
    val moveName: String,
    val moveType: String,
    val moveCategory: String,
    val targetUuid: String?,
    val targetName: String?,
    val baseScore: Double,
    val scoringSteps: MutableList<ScoringStep> = mutableListOf(),
    var finalScore: Double,
    val damageCalc: DamageCalcResult? = null,
    val categories: List<String> = emptyList()
)

/**
 * Result of damage calculation for a move.
 */
data class DamageCalcResult(
    val basePower: Int,
    val effectiveness: Double,
    val effectivenessLabel: String,
    val stab: Boolean,
    val minDamage: Int,
    val maxDamage: Int,
    val avgDamage: Int,
    val minPercent: Double,
    val maxPercent: Double,
    val avgPercent: Double,
    val isOHKO: Boolean,
    val is2HKO: Boolean
)

/**
 * Evaluation of a switch option.
 */
data class SwitchEvaluation(
    val pokemonUuid: String,
    val pokemonName: String,
    val species: String,
    val hpPercent: Double,
    val types: List<String>,
    val baseScore: Double,
    val matchupScore: Double,
    val scoringSteps: MutableList<ScoringStep> = mutableListOf(),
    var finalScore: Double
)

/**
 * Evaluation of an item use option.
 */
data class ItemEvaluation(
    val itemId: String,
    val itemName: String,
    val category: String,
    val targetUuid: String,
    val targetName: String,
    val baseScore: Double,
    val scoringSteps: MutableList<ScoringStep> = mutableListOf(),
    var finalScore: Double
)

/**
 * A single step in the scoring process.
 */
data class ScoringStep(
    val type: ScoringStepType,
    val description: String,
    val modifier: String,
    val scoreBefore: Double,
    val scoreAfter: Double
)

enum class ScoringStepType {
    BASE_POWER,
    DAMAGE_CALC,
    TYPE_EFFECTIVENESS,
    STAB,
    STATUS_BIAS,
    MOVE_RULE,
    SWITCH_CONDITION,
    ITEM_RULE,
    ACTION_PRIORITY,
    PERSONALITY,
    BIAS_MULTIPLIER,
    HP_BONUS,
    MATCHUP_BONUS,
    CATEGORY_BONUS,
    URGENCY
}

/**
 * A step in the AI's thinking process for display.
 */
data class ThinkingStep(
    val phase: ThinkingPhase,
    val title: String,
    val details: String,
    val icon: String? = null
)

enum class ThinkingPhase {
    CONTEXT_ANALYSIS,
    MOVE_SCORING,
    SWITCH_SCORING,
    ITEM_SCORING,
    PRIORITY_APPLICATION,
    PERSONALITY_APPLICATION,
    FINAL_SELECTION
}

/**
 * The final decision made by the AI.
 */
data class FinalDecision(
    val type: DecisionType,
    val actionId: String,
    val actionName: String,
    val targetUuid: String?,
    val targetName: String?,
    val score: Double,
    val reasoning: String,
    val alternativesConsidered: Int,
    val marginFromBest: Double
)

enum class DecisionType {
    MOVE,
    SWITCH,
    ITEM,
    PASS,
    RANDOM
}
