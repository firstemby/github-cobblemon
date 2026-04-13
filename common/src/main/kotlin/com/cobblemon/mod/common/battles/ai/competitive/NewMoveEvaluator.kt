package com.cobblemon.mod.common.battles.ai.competitive

import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.battles.InBattleMove
import kotlin.math.max

class NewMoveEvaluator(
    private val damageCalculator: NewDamageCalculator,
    private val opponentPredictor: NewOpponentPredictor,
    private val isDoublesMode: Boolean = false
) {
    private val delegate = MoveEvaluator(damageCalculator.delegate, opponentPredictor.delegate, isDoublesMode)
    private val priorityRegex = Regex("^priority_(-?\\d+)$")
    private val healRegex = Regex("^heal_(\\d+)$")
    private val chanceRegex = Regex("_(\\d+)$")
    private val statRegex = Regex("^stat_(atk|def|spatk|spdef|speed|accuracy|evasion)_(-?\\d+)(?:_(\\d+))?$")
    private val recoilRegex = Regex("^recoil_(\\d+)$")

    data class MoveEvaluation(
        val move: InBattleMove,
        val rawDamage: Double,
        val expectedDamage: Double,
        val strategicValue: Double,
        val riskFactor: Double,
        val priority: Int,
        val turnAdvantage: Double,
        val gamePlanAlignment: Double
    ) {
        val totalScore: Double = (expectedDamage + strategicValue + gamePlanAlignment + turnAdvantage) / riskFactor
    }

    data class DebugDetails(
        val hasStab: Boolean,
        val effectiveness: Double,
        val accuracy: Double,
        val priority: Int,
        val isStatusMove: Boolean,
        val isSetup: Boolean,
        val setsHazards: Boolean,
        val removesHazards: Boolean,
        val setsWeather: Boolean,
        val isRecovery: Boolean,
        val isPivot: Boolean,
        val isRisky: Boolean
    )

    fun evaluateMove(
        move: InBattleMove,
        attacker: TrackedPokemon,
        defender: TrackedPokemon,
        battleState: BattleState,
        gamePlan: GamePlan
    ): MoveEvaluation {
        val base = delegate.evaluateMove(move, attacker, defender, battleState, gamePlan)
        val moveTags = NewAiBattleUtils.moveTags(move.id)
        val attackerAbility = attacker.currentAbility?.lowercase() ?: attacker.pokemon?.ability?.name?.lowercase()
        val attackerAbilityTags = NewAiBattleUtils.abilityTags(attackerAbility)
        val defenderAbilityTags = NewAiBattleUtils.abilityTags(defender.currentAbility?.lowercase() ?: defender.pokemon?.ability?.name?.lowercase())

        val utilityBonus = tagUtilityBonus(
            moveTags = moveTags,
            attacker = attacker,
            defender = defender,
            battleState = battleState,
            attackerAbilityTags = attackerAbilityTags,
            defenderAbilityTags = defenderAbilityTags
        )
        val riskPenalty = tagRiskPenalty(moveTags, attacker, battleState)
        val expectedDamage = applyTagExpectedDamageAdjustments(base.expectedDamage, moveTags, attackerAbility)
        val gamePlanBonus = tagGamePlanBonus(moveTags, gamePlan, battleState)

        return MoveEvaluation(
            move = base.move,
            rawDamage = base.rawDamage,
            expectedDamage = expectedDamage,
            strategicValue = base.strategicValue + utilityBonus,
            riskFactor = max(0.1, base.riskFactor + riskPenalty),
            priority = max(base.priority, parsePriority(moveTags, base.priority)),
            turnAdvantage = base.turnAdvantage,
            gamePlanAlignment = base.gamePlanAlignment + gamePlanBonus
        )
    }

    fun evaluateMoveForDoubles(
        move: InBattleMove,
        attacker: TrackedPokemon,
        primaryTarget: TrackedPokemon,
        allOpponents: List<TrackedPokemon>,
        partner: TrackedPokemon?,
        battleState: BattleState,
        gamePlan: GamePlan
    ): MoveEvaluation {
        val base = delegate.evaluateMoveForDoubles(
            move,
            attacker,
            primaryTarget,
            allOpponents,
            partner,
            battleState,
            gamePlan
        )
        val moveTags = NewAiBattleUtils.moveTags(move.id)
        val attackerAbility = attacker.currentAbility?.lowercase() ?: attacker.pokemon?.ability?.name?.lowercase()
        val attackerAbilityTags = NewAiBattleUtils.abilityTags(attackerAbility)

        var strategic = base.strategicValue
        var risk = base.riskFactor
        if ("target_opponents" in moveTags || "target_all" in moveTags) {
            strategic += allOpponents.count { it.currentHp > 0 } * 10.0
        }
        if ("target_all" in moveTags && partner != null && partner.currentHpPercent < 0.4) {
            risk += 0.35
        }
        if ("pivot" in moveTags) {
            strategic += 12.0
        }
        if ("move_tag_damage_synergy" in attackerAbilityTags && moveTags.any { it in TAG_SYNERGY_KEYS }) {
            strategic += 14.0
        }

        return MoveEvaluation(
            move = base.move,
            rawDamage = base.rawDamage,
            expectedDamage = applyTagExpectedDamageAdjustments(base.expectedDamage, moveTags, attackerAbility),
            strategicValue = strategic,
            riskFactor = max(0.1, risk + tagRiskPenalty(moveTags, attacker, battleState)),
            priority = max(base.priority, parsePriority(moveTags, base.priority)),
            turnAdvantage = base.turnAdvantage,
            gamePlanAlignment = base.gamePlanAlignment + tagGamePlanBonus(moveTags, gamePlan, battleState)
        )
    }

    fun buildDebugDetails(
        move: InBattleMove,
        attacker: TrackedPokemon,
        defender: TrackedPokemon,
        battleState: BattleState,
        gamePlan: GamePlan
    ): DebugDetails {
        val details = delegate.buildDebugDetails(move, attacker, defender, battleState, gamePlan)
        return DebugDetails(
            hasStab = details.hasStab,
            effectiveness = details.effectiveness,
            accuracy = details.accuracy,
            priority = details.priority,
            isStatusMove = details.isStatusMove,
            isSetup = details.isSetup,
            setsHazards = details.setsHazards,
            removesHazards = details.removesHazards,
            setsWeather = details.setsWeather,
            isRecovery = details.isRecovery,
            isPivot = details.isPivot,
            isRisky = details.isRisky
        )
    }

    private fun applyTagExpectedDamageAdjustments(
        expectedDamage: Double,
        moveTags: Set<String>,
        attackerAbilityId: String?
    ): Double {
        var adjusted = expectedDamage
        if (adjusted <= 0.0) {
            return adjusted
        }
        if ("move_tag_damage_synergy" in NewAiBattleUtils.abilityTags(attackerAbilityId) &&
            moveTags.any { it in TAG_SYNERGY_KEYS }
        ) {
            adjusted *= 1.1
        }
        if ("delay_1" in moveTags) adjusted *= 0.9
        if ("recharge_turn" in moveTags) adjusted *= 0.85
        return adjusted
    }

    private fun tagUtilityBonus(
        moveTags: Set<String>,
        attacker: TrackedPokemon,
        defender: TrackedPokemon,
        battleState: BattleState,
        attackerAbilityTags: Set<String>,
        defenderAbilityTags: Set<String>
    ): Double {
        var bonus = 0.0

        if (moveTags.any { it.startsWith("heal_") } || "heal_variable" in moveTags) {
            val heal = parseHealPercent(moveTags).coerceAtLeast(25)
            bonus += (1.0 - attacker.currentHpPercent).coerceAtLeast(0.0) * heal
            if (attacker.currentHpPercent <= 0.35) bonus += 24.0
            if (attacker.currentHpPercent >= 0.9) bonus -= 10.0
        }

        if ("target_self" in moveTags) {
            val setupGain = moveTags.mapNotNull { statRegex.find(it)?.groupValues?.getOrNull(2)?.toIntOrNull() }
                .filter { it > 0 }
                .sum()
            bonus += setupGain * 12.0
            if (battleState.gamePhase == GamePhase.EARLY_GAME || battleState.gamePhase == GamePhase.MID_GAME) {
                bonus += setupGain * 8.0
            }
        }

        val statusChance = parseStatusChance(moveTags)
        if (statusChance > 0 && defender.pokemon?.status == null && "status_move_immunity" !in defenderAbilityTags) {
            bonus += statusChance * 0.35
        }

        if ("hazard_set" in moveTags && battleState.playerHazards.isEmpty()) {
            bonus += 40.0
        }
        if (("hazard_clear" in moveTags || "removes_hazards" in moveTags) && battleState.npcHazards.isNotEmpty()) {
            bonus += 35.0 + battleState.npcHazards.size * 8.0
        }

        val priority = parsePriority(moveTags, 0)
        if (priority > 0 && attacker.currentHpPercent < 0.5) {
            bonus += 10.0 + (priority * 6.0)
        }

        if ("protect_like" in moveTags || "invincible" in moveTags) {
            if (attacker.currentHpPercent < 0.45) bonus += 20.0
            if (battleState.gamePhase == GamePhase.ENDGAME) bonus += 8.0
        }

        if ("pivot" in moveTags) {
            bonus += if (attacker.currentHpPercent < 0.4) 20.0 else 8.0
        }

        if ("sets_weather" in moveTags && attackerAbilityTags.any { it == "weather_interaction" || it.startsWith("conditional_weather_") }) {
            bonus += 22.0
        }
        if ("sets_terrain" in moveTags && attackerAbilityTags.any { it == "terrain_interaction" || it.startsWith("type_") }) {
            bonus += 12.0
        }
        if ("move_tag_damage_synergy" in attackerAbilityTags && moveTags.any { it in TAG_SYNERGY_KEYS }) {
            bonus += 14.0
        }
        if ("secondary_chance_boost" in attackerAbilityTags && moveTags.any { it.startsWith("secondary_chance_") }) {
            bonus += 10.0
        }
        if ("status_move_immunity" in attackerAbilityTags && "category_status" in moveTags) {
            bonus += 6.0
        }

        return bonus
    }

    private fun tagGamePlanBonus(moveTags: Set<String>, gamePlan: GamePlan, battleState: BattleState): Double {
        var bonus = 0.0
        when (gamePlan.winCondition) {
            WinCondition.HAZARD_CONTROL -> {
                if ("hazard_set" in moveTags) bonus += 40.0
                if ("hazard_clear" in moveTags || "removes_hazards" in moveTags) bonus += 35.0
            }
            WinCondition.RAIN_OFFENSE, WinCondition.SUN_OFFENSE, WinCondition.SAND_OFFENSE, WinCondition.WEATHER_CONTROL -> {
                if ("sets_weather" in moveTags) bonus += 32.0
                if ("context_weather_sensitive" in moveTags && battleState.weather != null) bonus += 14.0
            }
            WinCondition.SETUP_SWEEP -> {
                if ("target_self" in moveTags && moveTags.any { it.startsWith("stat_") && !it.contains("_-") }) bonus += 28.0
            }
            WinCondition.DEFENSIVE_STALL -> {
                if (moveTags.any { it.startsWith("heal_") } || "heal_variable" in moveTags) bonus += 20.0
                if (moveTags.any { it.startsWith("toxic_") || it.startsWith("poison_") }) bonus += 16.0
            }
            else -> {}
        }
        return bonus
    }

    private fun tagRiskPenalty(moveTags: Set<String>, attacker: TrackedPokemon, battleState: BattleState): Double {
        var penalty = 0.0
        val hpRiskFactor = (1.0 - attacker.currentHpPercent).coerceAtLeast(0.1)

        if ("self_ko" in moveTags) penalty += 1.6
        if ("crash_risk" in moveTags) penalty += 0.35
        if ("recharge_turn" in moveTags) penalty += 0.45
        if ("delay_1" in moveTags && attacker.currentHpPercent < 0.55) penalty += 0.2

        moveTags.forEach {
            val recoil = recoilRegex.find(it)?.groupValues?.getOrNull(1)?.toIntOrNull()
            if (recoil != null) {
                penalty += (recoil / 100.0) * (0.8 + hpRiskFactor)
            }
        }

        val minAccuracy = moveTags
            .filter { it.startsWith("accuracy_") && it != "accuracy_true" }
            .mapNotNull { it.removePrefix("accuracy_").toIntOrNull() }
            .minOrNull()
        if (minAccuracy != null && minAccuracy < 90) {
            penalty += ((90 - minAccuracy) / 100.0)
        }
        if (battleState.gamePhase == GamePhase.ENDGAME && "self_ko" in moveTags) {
            penalty += 0.8
        }

        return penalty
    }

    private fun parsePriority(tags: Set<String>, fallback: Int): Int {
        return tags.mapNotNull { priorityRegex.find(it)?.groupValues?.getOrNull(1)?.toIntOrNull() }.firstOrNull() ?: fallback
    }

    private fun parseHealPercent(tags: Set<String>): Int {
        return tags.mapNotNull { healRegex.find(it)?.groupValues?.getOrNull(1)?.toIntOrNull() }.maxOrNull() ?: 0
    }

    private fun parseStatusChance(tags: Set<String>): Int {
        return tags.filter {
            it.startsWith("burn_") ||
                it.startsWith("paralysis_") ||
                it.startsWith("sleep_") ||
                it.startsWith("poison_") ||
                it.startsWith("toxic_") ||
                it.startsWith("freeze_") ||
                it.startsWith("confusion_")
        }.mapNotNull { chanceRegex.find(it)?.groupValues?.getOrNull(1)?.toIntOrNull() }.maxOrNull() ?: 0
    }

    companion object {
        private val TAG_SYNERGY_KEYS = setOf("biting", "punching", "sound", "bullet", "wind", "pulse", "dance")
    }
}
