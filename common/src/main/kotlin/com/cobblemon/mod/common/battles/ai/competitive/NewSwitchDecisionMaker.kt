package com.cobblemon.mod.common.battles.ai.competitive

import com.cobblemon.mod.common.api.moves.Move
import com.cobblemon.mod.common.api.pokemon.stats.Stat
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon
import com.cobblemon.mod.common.pokemon.Pokemon

class NewSwitchDecisionMaker(
    private val damageCalculator: NewDamageCalculator,
    private val opponentPredictor: NewOpponentPredictor,
    private val strategyAnalyzer: NewStrategyAnalyzer
) {
    private val delegate = SwitchDecisionMaker(
        damageCalculator.delegate,
        opponentPredictor.delegate,
        strategyAnalyzer.delegate
    )

    data class SwitchDecisionDiagnostics(
        val shouldSwitch: Boolean,
        val reason: String,
        val currentScore: Double,
        val bestScore: Double,
        val scoreGain: Double,
        val severeDisadvantage: Boolean,
        val strongCounterFound: Boolean,
        val currentThreat: Double,
        val bestThreat: Double,
        val currentPressure: Double,
        val bestPressure: Double,
        val bestSwitchName: String?
    )

    private var lastDiagnostics = SwitchDecisionDiagnostics(
        shouldSwitch = false,
        reason = "No decision computed yet.",
        currentScore = 0.0,
        bestScore = 0.0,
        scoreGain = 0.0,
        severeDisadvantage = false,
        strongCounterFound = false,
        currentThreat = 1.0,
        bestThreat = 1.0,
        currentPressure = 1.0,
        bestPressure = 1.0,
        bestSwitchName = null
    )

    fun shouldVoluntarilySwitch(
        myPokemon: TrackedPokemon,
        opponentPokemon: TrackedPokemon,
        battleState: BattleState,
        gamePlan: GamePlan,
        availableSwitches: List<BattlePokemon>
    ): Boolean {
        if (availableSwitches.isEmpty()) {
            lastDiagnostics = lastDiagnostics.copy(
                shouldSwitch = false,
                reason = "No legal bench options available."
            )
            return false
        }

        val currentPokemon = myPokemon.pokemon
        if (currentPokemon == null) {
            lastDiagnostics = lastDiagnostics.copy(
                shouldSwitch = false,
                reason = "Current battler data missing."
            )
            return false
        }

        val scored = availableSwitches.map { option ->
            option to evaluateSwitchOption(option.effectedPokemon, opponentPokemon, battleState, gamePlan)
        }.sortedByDescending { it.second }

        val bestPair = scored.first()
        val bestSwitch = bestPair.first.effectedPokemon
        val bestScore = bestPair.second
        val currentScore = evaluateSwitchOption(currentPokemon, opponentPokemon, battleState, gamePlan)
        val scoreGain = bestScore - currentScore

        val currentThreat = defensiveThreatMultiplier(opponentPokemon, currentPokemon)
        val bestThreat = defensiveThreatMultiplier(opponentPokemon, bestSwitch)
        val currentPressure = offensivePressureMultiplier(currentPokemon, opponentPokemon)
        val bestPressure = offensivePressureMultiplier(bestSwitch, opponentPokemon)

        val mySpeed = effectiveStat(currentPokemon, Stats.SPEED)
        val oppSpeed = opponentPokemon.pokemon?.let { effectiveStat(it, Stats.SPEED) } ?: opponentPokemon.cachedSpeed
        val speedDisadvantage = oppSpeed > mySpeed * 1.08
        val lowHealth = myPokemon.currentHpPercent <= 0.55
        val opponentBoosted = opponentPokemon.boosts.values.any { it >= 2 }
        val currentPositionPoor = currentScore <= 12.0

        val severeDisadvantage =
            (currentThreat >= 2.0 && (speedDisadvantage || lowHealth || opponentBoosted)) ||
                (currentThreat >= 1.5 && lowHealth) ||
                (opponentBoosted && currentPositionPoor) ||
                (myPokemon.currentHpPercent <= 0.35 && currentPositionPoor)

        val threatImprovement = currentThreat - bestThreat
        val pressureImprovement = bestPressure - currentPressure
        val strongCounterFound =
            scoreGain >= 45.0 &&
                bestScore >= 70.0 &&
                currentScore <= 25.0 &&
                (
                    threatImprovement >= 0.5 ||
                        pressureImprovement >= 0.75 ||
                        effectiveStat(bestSwitch, Stats.SPEED) > oppSpeed * 1.15
                    )

        val shouldSwitch =
            (severeDisadvantage && scoreGain >= 18.0) ||
                strongCounterFound

        val reason = when {
            shouldSwitch && severeDisadvantage && strongCounterFound ->
                "Switching: severe matchup risk and a strong counter exists."
            shouldSwitch && severeDisadvantage ->
                "Switching: severe disadvantage in current matchup."
            shouldSwitch && strongCounterFound ->
                "Switching: found a clearly better counter from bench."
            else ->
                "Staying in: disadvantage not severe enough and no clear counter upgrade."
        }

        lastDiagnostics = SwitchDecisionDiagnostics(
            shouldSwitch = shouldSwitch,
            reason = reason,
            currentScore = currentScore,
            bestScore = bestScore,
            scoreGain = scoreGain,
            severeDisadvantage = severeDisadvantage,
            strongCounterFound = strongCounterFound,
            currentThreat = currentThreat,
            bestThreat = bestThreat,
            currentPressure = currentPressure,
            bestPressure = bestPressure,
            bestSwitchName = bestSwitch.species.name
        )
        return shouldSwitch
    }

    fun findBestSwitch(
        availableSwitches: List<BattlePokemon>,
        opponentPokemon: TrackedPokemon,
        battleState: BattleState,
        gamePlan: GamePlan
    ): BattlePokemon? {
        return delegate.findBestSwitch(availableSwitches, opponentPokemon, battleState, gamePlan)
    }

    fun evaluateSwitchOption(
        candidate: Pokemon,
        opponent: TrackedPokemon,
        battleState: BattleState,
        gamePlan: GamePlan
    ): Double {
        return delegate.evaluateSwitchOption(candidate, opponent, battleState, gamePlan)
    }

    fun latestSwitchRationale(): String {
        val d = lastDiagnostics
        return buildString {
            append(d.reason)
            append(" ")
            append(
                "currentScore=${"%.1f".format(d.currentScore)}, " +
                    "bestScore=${"%.1f".format(d.bestScore)}, " +
                    "gain=${"%.1f".format(d.scoreGain)}"
            )
            append(" | ")
            append(
                "currentThreat=${"%.2f".format(d.currentThreat)}, " +
                    "bestThreat=${"%.2f".format(d.bestThreat)}, " +
                    "currentPressure=${"%.2f".format(d.currentPressure)}, " +
                    "bestPressure=${"%.2f".format(d.bestPressure)}"
            )
            append(" | ")
            append(
                "severeDisadvantage=${d.severeDisadvantage}, " +
                    "strongCounterFound=${d.strongCounterFound}, " +
                    "bestSwitch=${d.bestSwitchName ?: "none"}"
            )
        }
    }

    fun latestDiagnosticsSnapshot(): SwitchDecisionDiagnostics = lastDiagnostics

    private fun offensivePressureMultiplier(attacker: Pokemon, defender: TrackedPokemon): Double {
        val defenderTypes = defender.currentTypes ?: defender.pokemon?.types?.map { it.name.lowercase() } ?: listOf()
        return bestMoveEffectiveness(attacker.moveSet.getMoves(), defenderTypes)
    }

    private fun defensiveThreatMultiplier(opponent: TrackedPokemon, candidate: Pokemon): Double {
        val candidateTypes = candidate.types.map { it.name.lowercase() }
        val opponentMoves = opponent.moves.ifEmpty { opponent.pokemon?.moveSet?.getMoves() ?: listOf() }
        return bestMoveEffectiveness(opponentMoves, candidateTypes)
    }

    private fun bestMoveEffectiveness(moves: List<Move>, defenderTypes: List<String>): Double {
        if (moves.isEmpty() || defenderTypes.isEmpty()) return 1.0
        return moves.maxOfOrNull { move ->
            TypeEffectiveness.calculateTypeEffectiveness(move.type, defenderTypes)
        } ?: 1.0
    }

    private fun effectiveStat(pokemon: Pokemon, stat: Stat): Double {
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
}
