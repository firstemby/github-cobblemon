package com.cobblemon.mod.common.battles.ai.competitive

import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.battles.InBattleMove
import com.cobblemon.mod.common.battles.MoveTarget
import com.cobblemon.mod.common.pokemon.Pokemon
import kotlin.math.abs
import kotlin.math.max


class MoveEvaluator(
    private val damageCalculator: DamageCalculator,
    private val opponentPredictor: OpponentPredictor,
    private val isDoublesMode: Boolean = false
) {


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


    fun evaluateMove(
        move: InBattleMove,
        attacker: TrackedPokemon,
        defender: TrackedPokemon,
        battleState: BattleState,
        gamePlan: GamePlan
    ): MoveEvaluation {
        val moveData = Moves.getByName(move.id)
            ?: return MoveEvaluation(move, 0.0, 0.0, 0.0, 1.0, 0, 0.0, 0.0)


        val rawDamage = damageCalculator.calculateDamage(move, attacker, defender, battleState.weather)
        val accuracy = getEffectiveAccuracy(move, attacker, defender, battleState.weather)
        val expectedDamage = rawDamage * accuracy


        val strategicValue = calculateStrategicValue(move, attacker, defender, battleState, gamePlan)
        val riskFactor = calculateRiskFactor(move, attacker, defender, battleState, gamePlan)
        val turnAdvantage = calculateTurnAdvantage(move, attacker, defender, battleState)
        val gamePlanAlignment = calculateGamePlanAlignment(move, attacker, defender, battleState, gamePlan)

        return MoveEvaluation(
            move = move,
            rawDamage = rawDamage,
            expectedDamage = expectedDamage,
            strategicValue = strategicValue,
            riskFactor = riskFactor,
            priority = moveData.priority,
            turnAdvantage = turnAdvantage,
            gamePlanAlignment = gamePlanAlignment
        )
    }


    private fun getEffectiveAccuracy(
        move: InBattleMove,
        attacker: TrackedPokemon,
        defender: TrackedPokemon,
        weather: String?
    ): Double {
        val moveData = Moves.getByName(move.id) ?: return 0.0
        var accuracy = moveData.accuracy / 100.0


        when (weather?.lowercase()) {
            "rain" -> {
                if (move.id.lowercase() in listOf("thunder", "hurricane")) accuracy = 1.0
            }

            "hail", "snow" -> {
                if (move.id.lowercase() == "blizzard") accuracy = 1.0
            }

            "sun" -> {
                if (move.id.lowercase() in listOf("thunder", "hurricane")) accuracy *= 0.5
            }
        }


        when (attacker.currentAbility?.lowercase()) {
            "compoundeyes" -> accuracy *= 1.3
            "noguard" -> accuracy = 1.0
        }

        when (defender.currentAbility?.lowercase()) {
            "noguard" -> accuracy = 1.0
            "sandveil" -> if (weather == "sandstorm") accuracy *= 0.8
            "snowcloak" -> if (weather in listOf("hail", "snow")) accuracy *= 0.8
        }


        val accuracyBoost = attacker.boosts.getOrDefault(Stats.ACCURACY, 0)
        val evasionBoost = defender.boosts.getOrDefault(Stats.EVASION, 0)
        val netBoost = accuracyBoost - evasionBoost

        if (netBoost != 0) {
            accuracy *= if (netBoost > 0) {
                (3.0 + netBoost) / 3.0
            } else {
                3.0 / (3.0 - netBoost)
            }
        }

        return accuracy.coerceIn(0.0, 1.0)
    }


    private fun calculateStrategicValue(
        move: InBattleMove,
        attacker: TrackedPokemon,
        defender: TrackedPokemon,
        battleState: BattleState,
        gamePlan: GamePlan
    ): Double {
        var value = 0.0


        if (move.id.lowercase() in MoveData.weatherMoves.keys) {
            value += evaluateWeatherSetting(move, gamePlan, battleState)
        }


        if (move.id.lowercase() in MoveData.boostFromMoves.keys) {
            value += evaluateSetupMove(move, attacker, defender, battleState, gamePlan)
        }


        val moveId = move.id.lowercase()
        val isStatusMove = MoveData.statusMoves.keys.any { it?.name?.lowercase() == moveId }
        if (isStatusMove) {
            value += evaluateStatusMove(move, attacker, defender, battleState, gamePlan)
        }


        if (move.id.lowercase() in MoveData.entryHazards) {
            value += evaluateHazardSetting(battleState, gamePlan)
        }


        if (move.id.lowercase() in MoveData.antiHazardsMoves) {
            value += evaluateHazardRemoval(battleState, gamePlan)
        }


        val moveData = Moves.getByName(move.id)
        if (moveData != null && moveData.priority > 0) {
            value += evaluatePriorityMove(move, attacker, defender, battleState)
        }


        if (move.id.lowercase() in MoveData.selfRecoveryMoves) {
            value += evaluateRecoveryMove(attacker, battleState, gamePlan)
        }


        if (move.id.lowercase() in MoveData.pivotMoves) {
            value += evaluatePivotMove(move, attacker, defender, battleState)
        }


        if (battleState.gamePhase == GamePhase.ENDGAME) {
            value += evaluateEndgameMove(move, battleState)
        }

        return value
    }


    private fun calculateGamePlanAlignment(
        move: InBattleMove,
        attacker: TrackedPokemon,
        defender: TrackedPokemon,
        battleState: BattleState,
        gamePlan: GamePlan
    ): Double {
        val moveData = Moves.getByName(move.id) ?: return 0.0
        var alignment = 0.0


        if (move.id.lowercase() in gamePlan.riskyMoves) {
            alignment -= 100.0
        }


        alignment += when (gamePlan.winCondition) {
            WinCondition.RAIN_OFFENSE -> {
                var score = 0.0
                if (move.id.lowercase() == "raindance") score += 200.0
                if (battleState.weather == "rain") {
                    if (moveData.elementalType.name == "water") score += 50.0
                    if (move.id.lowercase() in listOf("thunder", "hurricane")) score += 100.0
                }
                score
            }

            WinCondition.SUN_OFFENSE -> {
                var score = 0.0
                if (move.id.lowercase() == "sunnyday") score += 200.0
                if (battleState.weather in listOf("sun", "harshsunlight")) {
                    if (moveData.elementalType.name == "fire") score += 50.0
                    if (move.id.lowercase() == "solarbeam") score += 100.0
                }
                score
            }

            WinCondition.SETUP_SWEEP -> {
                var score = 0.0
                if (move.id.lowercase() in MoveData.boostFromMoves.keys &&
                    attacker.species in gamePlan.keyPokemon
                ) {
                    score += 150.0
                }
                if (attacker.boosts.values.any { it >= 2 } && moveData.power > 80) {
                    score += 100.0
                }
                score
            }

            WinCondition.AGGRESSIVE_SWEEP -> {
                var score = 0.0
                if (moveData.power >= 90) score += 60.0
                if (moveData.priority > 0 && defender.currentHpPercent < 0.5) score += 80.0
                score
            }

            WinCondition.DEFENSIVE_STALL -> {
                var score = 0.0
                if (move.id.lowercase() in MoveData.statusMoves.values.map { it }) score += 70.0
                if (move.id.lowercase() in MoveData.selfRecoveryMoves) score += 60.0
                if (moveData.power > 80) score -= 30.0
                score
            }

            WinCondition.HAZARD_CONTROL -> {
                var score = 0.0
                if (move.id.lowercase() == "stealthrock" && "stealthrock" !in battleState.playerHazards) {
                    score += 250.0
                }
                if (move.id.lowercase() in MoveData.entryHazards) score += 100.0
                score
            }

            WinCondition.REVENGE_KILLING -> {
                var score = 0.0
                if (moveData.priority > 0) score += 120.0
                if (defender.currentHp > 50 && moveData.power >= 40 && moveData.power <= 80) score += 40.0
                score
            }

            else -> 0.0
        }


        if (moveData.power > 0) {
            alignment += moveData.power * gamePlan.aggressionLevel * 0.3
        } else {
            alignment += (1.0 - gamePlan.aggressionLevel) * 30.0
        }

        return alignment
    }


    private fun calculateRiskFactor(
        move: InBattleMove,
        attacker: TrackedPokemon,
        defender: TrackedPokemon,
        battleState: BattleState,
        gamePlan: GamePlan
    ): Double {
        val moveData = Moves.getByName(move.id) ?: return 1.0
        var risk = 1.0


        val accuracy = getEffectiveAccuracy(move, attacker, defender, battleState.weather)
        risk += (1.0 - accuracy) * 0.5


        if (move.id.lowercase() in MoveData.recoilMoves) {
            risk += if (attacker.currentHpPercent < 0.5) 0.5 else 0.2
        }


        if (move.id.lowercase() in listOf("explosion", "selfdestruct", "memento", "healingwish")) {

            if (attacker.species in gamePlan.keyPokemon) {
                risk += 2.0
            } else if (attacker.currentHpPercent < 0.3) {
                risk += 0.2
            } else {
                risk += 0.8
            }
        }


        if (move.pp <= 1) {
            risk += 0.3
        }


        val mySpeed = attacker.pokemon?.let { getEffectiveStat(it, Stats.SPEED) } ?: 0.0
        val oppSpeed = defender.pokemon?.let { getEffectiveStat(it, Stats.SPEED) } ?: 0.0
        if (abs(mySpeed - oppSpeed) < 5) {
            risk += 0.1
        }


        val teamAdvantage = calculateTeamAdvantage(battleState)
        if (teamAdvantage > 0) {

            risk *= 0.8
        } else if (teamAdvantage < -1) {

            risk *= 1.2
        }

        return max(0.1, risk)
    }


    private fun calculateTurnAdvantage(
        move: InBattleMove,
        attacker: TrackedPokemon,
        defender: TrackedPokemon,
        battleState: BattleState
    ): Double {
        val moveData = Moves.getByName(move.id) ?: return 0.0
        var advantage = 0.0
        val mySpeed = attacker.pokemon?.let { getEffectiveStat(it, Stats.SPEED) } ?: 0.0
        val oppSpeed = defender.pokemon?.let { getEffectiveStat(it, Stats.SPEED) } ?: 0.0
        if (mySpeed > oppSpeed) advantage += 15.0
        if (moveData.priority > 0) advantage += moveData.priority * 25.0
        if (battleState.gamePhase == GamePhase.ENDGAME && moveData.power > 90) {
            advantage += 20.0
        }
        return advantage
    }


    private fun evaluateWeatherSetting(move: InBattleMove, gamePlan: GamePlan, battleState: BattleState): Double {
        val moveName = move.id.lowercase()
        val weatherType = MoveData.weatherMoves[moveName] ?: return 0.0

        return when (gamePlan.winCondition) {
            WinCondition.RAIN_OFFENSE -> if (weatherType == "rain") 300.0 else 0.0
            WinCondition.SUN_OFFENSE -> if (weatherType == "sun") 300.0 else 0.0
            WinCondition.SAND_OFFENSE -> if (weatherType == "sand") 300.0 else 0.0
            WinCondition.WEATHER_CONTROL -> 150.0
            else -> 30.0
        }
    }

    private fun evaluateSetupMove(
        move: InBattleMove,
        attacker: TrackedPokemon,
        defender: TrackedPokemon,
        battleState: BattleState,
        gamePlan: GamePlan
    ): Double {
        val boosts = MoveData.boostFromMoves[move.id.lowercase()] ?: return 0.0
        var value = 0.0
        val totalBoosts = boosts.values.sum()
        value += totalBoosts * 40.0 * gamePlan.setupPriority
        val relevantBoosts = boosts.keys.mapNotNull { stat -> attacker.boosts[stat] }
        if (relevantBoosts.any { it >= 3 }) value *= 0.2
        val predictedMoves = opponentPredictor.predictMoveHeuristically(defender, attacker)
        val dangerLevel = predictedMoves.count { (moveName, _) ->
            (Moves.getByName(moveName)?.power ?: 0.0) > 80
        }
        if (dangerLevel > 0 && attacker.currentHpPercent < 0.7) {
            value *= 0.4
        }

        return value
    }

    private fun evaluateStatusMove(
        move: InBattleMove,
        attacker: TrackedPokemon,
        defender: TrackedPokemon,
        battleState: BattleState,
        gamePlan: GamePlan
    ): Double {

        if (defender.pokemon?.status != null) return 0.0

        return when (move.id.lowercase()) {
            "thunderwave", "stunspore", "glare" -> {

                if (defender.predictedRole == PokemonRole.SWEEPER) 90.0 else 50.0
            }

            "willowisp" -> {

                if (defender.predictedRole in listOf(PokemonRole.WALLBREAKER, PokemonRole.SWEEPER)) {
                    80.0
                } else 40.0
            }

            "toxic" -> {

                if (gamePlan.winCondition == WinCondition.DEFENSIVE_STALL) 100.0 else 60.0
            }

            "spore", "sleeppowder" -> 120.0
            else -> 30.0
        }
    }

    private fun evaluateHazardSetting(battleState: BattleState, gamePlan: GamePlan): Double {
        val remainingOpponents = battleState.playerTeam.count { it.currentHealth > 0 }
        if (remainingOpponents < 2) return 15.0

        var value = remainingOpponents * 20.0
        if (gamePlan.winCondition == WinCondition.HAZARD_CONTROL) value *= 1.8

        return value
    }

    private fun evaluateHazardRemoval(battleState: BattleState, gamePlan: GamePlan): Double {
        val myHazards = battleState.npcHazards.size
        if (myHazards == 0) return 5.0

        var value = myHazards * 40.0
        val remainingTeam = battleState.npcTeam.count { it.currentHealth > 0 }
        if (remainingTeam >= 3) value *= 1.3

        return value
    }

    private fun evaluatePriorityMove(
        move: InBattleMove,
        attacker: TrackedPokemon,
        defender: TrackedPokemon,
        battleState: BattleState
    ): Double {
        val moveData = Moves.getByName(move.id) ?: return 0.0
        var value = moveData.priority * 20.0


        if (defender.currentHpPercent < 0.5) {
            val damage = damageCalculator.calculateDamage(move, attacker, defender, battleState.weather)
            if (damage >= defender.currentHp) value += 150.0
        }


        val mySpeed = attacker.pokemon?.let { getEffectiveStat(it, Stats.SPEED) } ?: 0.0
        val oppSpeed = defender.pokemon?.let { getEffectiveStat(it, Stats.SPEED) } ?: 0.0
        if (abs(mySpeed - oppSpeed) < 10) value += 50.0

        return value
    }

    private fun evaluateRecoveryMove(
        attacker: TrackedPokemon,
        battleState: BattleState,
        gamePlan: GamePlan
    ): Double {
        if (attacker.currentHpPercent > 0.8) return 5.0

        var value = (1.0 - attacker.currentHpPercent) * 140.0


        if (gamePlan.winCondition == WinCondition.DEFENSIVE_STALL) value *= 1.5

        return value
    }

    private fun evaluatePivotMove(
        move: InBattleMove,
        attacker: TrackedPokemon,
        defender: TrackedPokemon,
        battleState: BattleState
    ): Double {
        var value = 40.0


        val damage = damageCalculator.calculateDamage(move, attacker, defender, battleState.weather)
        value += damage * 0.3

        return value
    }

    private fun evaluateEndgameMove(move: InBattleMove, battleState: BattleState): Double {
        val moveData = Moves.getByName(move.id) ?: return 0.0
        var bonus = 0.0


        if (moveData.power > 0 && moveData.accuracy >= 90) {
            bonus += 35.0
        }


        if (moveData.accuracy < 80) {
            bonus -= 30.0
        }


        if (moveData.priority > 0) {
            bonus += 50.0
        }

        return bonus
    }

    private fun calculateTeamAdvantage(battleState: BattleState): Int {
        val npcAlive = battleState.npcTeam.count { it.currentHealth > 0 }
        val playerAlive = battleState.playerTeam.count { it.currentHealth > 0 }
        return npcAlive - playerAlive
    }

    private fun getEffectiveStat(pokemon: Pokemon, stat: Stats): Double {
        val base = pokemon.species.baseStats[stat] ?: 0
        val iv = pokemon.ivs[stat] ?: 31
        val ev = pokemon.evs[stat] ?: 0
        val level = pokemon.level

        return if (stat == Stats.HP) {
            ((2.0 * base + iv + ev / 4.0) * level / 100.0 + level + 10.0)
        } else {
            ((2.0 * base + iv + ev / 4.0) * level / 100.0 + 5.0)
        }
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
        val moveData = Moves.getByName(move.id)
            ?: return MoveEvaluation(move, 0.0, 0.0, 0.0, 1.0, 0, 0.0, 0.0)

        if (isSpreadMove(moveData.target)) {
            return evaluateSpreadMove(move, attacker, allOpponents, partner, battleState, gamePlan)
        }

        val baseEvaluation = evaluateMove(move, attacker, primaryTarget, battleState, gamePlan)

        var targetingBonus = 0.0
        if (primaryTarget.threatLevel > 5) {
            targetingBonus += 20.0
        }

        return MoveEvaluation(
            move = baseEvaluation.move,
            rawDamage = baseEvaluation.rawDamage,
            expectedDamage = baseEvaluation.expectedDamage,
            strategicValue = baseEvaluation.strategicValue + targetingBonus,
            riskFactor = baseEvaluation.riskFactor,
            priority = baseEvaluation.priority,
            turnAdvantage = baseEvaluation.turnAdvantage,
            gamePlanAlignment = baseEvaluation.gamePlanAlignment
        )
    }

    private fun evaluateSpreadMove(
        move: InBattleMove,
        attacker: TrackedPokemon,
        allOpponents: List<TrackedPokemon>,
        partner: TrackedPokemon?,
        battleState: BattleState,
        gamePlan: GamePlan
    ): MoveEvaluation {
        val moveData = Moves.getByName(move.id)
            ?: return MoveEvaluation(move, 0.0, 0.0, 0.0, 1.0, 0, 0.0, 0.0)

        var totalDamage = 0.0
        var totalStrategicValue = 0.0
        val activeOpponents = allOpponents.filter { it.currentHp > 0 }
        val targetCount = activeOpponents.size + (if (hitsPartner(moveData.target) && partner != null) 1 else 0)


        for (opponent in activeOpponents) {
            val damage = damageCalculator.calculateDamage(
                move, attacker, opponent, battleState.weather,
                isSpreadMove = true,
                targetCount = targetCount
            )
            totalDamage += damage


            if (damage >= opponent.currentHp) {
                totalStrategicValue += 150.0
            }
        }

        val multiTargetBonus = activeOpponents.size * 40.0
        totalStrategicValue += multiTargetBonus

        var partnerPenalty = 0.0
        if (hitsPartner(moveData.target) && partner != null && partner.currentHp > 0) {
            val partnerDamage = damageCalculator.calculateDamage(
                move, attacker, partner, battleState.weather,
                isSpreadMove = true,
                targetCount = targetCount
            )

            partnerPenalty = when {
                partnerDamage >= partner.currentHp -> -999999.0
                partner.species in gamePlan.keyPokemon -> -partnerDamage * 5.0
                partner.currentHpPercent < 0.3 -> -partnerDamage * 4.0
                else -> -partnerDamage * 2.0
            }

            totalStrategicValue += partnerPenalty
        }

        val accuracy =
            getEffectiveAccuracy(move, attacker, activeOpponents.firstOrNull() ?: TrackedPokemon(), battleState.weather)
        val expectedDamage = totalDamage * accuracy

        val riskFactor = if (partnerPenalty < -1000) {
            999.0
        } else if (partnerPenalty < 0) {
            max(1.5, 1.0 + abs(partnerPenalty) / 100.0)
        } else {
            1.0
        }

        val gamePlanAlignment = if (activeOpponents.isNotEmpty()) {
            calculateGamePlanAlignment(move, attacker, activeOpponents.first(), battleState, gamePlan)
        } else {
            0.0
        }

        return MoveEvaluation(
            move = move,
            rawDamage = totalDamage,
            expectedDamage = expectedDamage,
            strategicValue = totalStrategicValue,
            riskFactor = riskFactor,
            priority = moveData.priority,
            turnAdvantage = 0.0,
            gamePlanAlignment = gamePlanAlignment
        )
    }


    private fun isSpreadMove(target: MoveTarget): Boolean {
        return target in listOf(
            MoveTarget.allAdjacent,
            MoveTarget.allAdjacentFoes,
            MoveTarget.all
        )
    }


    private fun hitsPartner(target: MoveTarget): Boolean {
        return target == MoveTarget.allAdjacent
    }
}
