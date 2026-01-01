package com.cobblemon.mod.common.battles.ai.competitive

import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.pokemon.Pokemon
import kotlin.math.min

 
class OpponentPredictor {
    private val moveHistory = mutableMapOf<String, MutableList<String>>()
    private val switchHistory = mutableMapOf<String, MutableList<String>>()
    private val situationResponses = mutableMapOf<String, MutableMap<String, Int>>()
    private val aggressionLevel = mutableMapOf<String, Double>()
    private val setupTendency = mutableMapOf<String, Double>()
    private val switchFrequency = mutableMapOf<String, Double>()

     
    fun updateMoveHistory(pokemon: String, move: String, situation: String) {
        moveHistory.getOrPut(pokemon) { mutableListOf() }.add(move)
        situationResponses.getOrPut(situation) { mutableMapOf() }.merge(move, 1, Int::plus)
        val moveData = Moves.getByName(move)
        if (moveData != null) {
            val currentAggression = aggressionLevel[pokemon] ?: 0.5
            val newAggression = if (moveData.power > 0) {
 
                currentAggression * 0.9 + 0.8 * 0.1
            } else {
 
                currentAggression * 0.9 + 0.2 * 0.1
            }
            aggressionLevel[pokemon] = newAggression
            if (move in MoveData.boostFromMoves.keys) {
                val currentSetup = setupTendency[pokemon] ?: 0.3
                setupTendency[pokemon] = min(1.0, currentSetup + 0.15)
            }
        }
    }

    fun updateSwitchPattern(fromPokemon: String, toPokemon: String, reason: String) {
        switchHistory.getOrPut(fromPokemon) { mutableListOf() }.add(toPokemon)
        situationResponses.getOrPut("switch_$reason") { mutableMapOf() }.merge(toPokemon, 1, Int::plus)
        val totalActions = (moveHistory[fromPokemon]?.size ?: 0) + (switchHistory[fromPokemon]?.size ?: 0)
        if (totalActions > 0) {
            switchFrequency[fromPokemon] = (switchHistory[fromPokemon]?.size ?: 0).toDouble() / totalActions
        }
    }

    fun predictNextMove(pokemon: String, situation: String): Map<String, Double> {
        val responses = situationResponses[situation] ?: return emptyMap()
        val total = responses.values.sum().toDouble()

        return if (total > 0) {
            responses.mapValues { it.value / total }
        } else {
            emptyMap()
        }
    }

    fun getSwitchProbability(pokemon: String, situation: String): Double {
        val switches = switchHistory[pokemon]?.size ?: 0
        val totalActions = (moveHistory[pokemon]?.size ?: 0) + switches

 
        if (totalActions < 5) return 0.2 

        return min(0.8, switches.toDouble() / totalActions + 0.1)
    }

     
    private fun getAggressionLevel(pokemon: String): Double = aggressionLevel[pokemon] ?: 0.5
    private fun getSetupTendency(pokemon: String): Double = setupTendency[pokemon] ?: 0.3
    private fun getSwitchFrequency(pokemon: String): Double = switchFrequency[pokemon] ?: 0.2

    fun predictMoveHeuristically(
        opponent: TrackedPokemon,
        myPokemon: TrackedPokemon
    ): Map<String, Double> {
        val predictions = mutableMapOf<String, Double>()
        val aggression = getAggressionLevel(opponent.species ?: "unknown")
        for (moveName in opponent.revealedMoves) {
            val moveData = Moves.getByName(moveName) ?: continue
            var probability = 0.25

            probability *= if (moveData.power > 0) {
                aggression + 0.3
            } else {
                (1.0 - aggression) + 0.3
            }

            val myTypes = myPokemon.currentTypes ?: listOf()
            val effectiveness = TypeEffectiveness.calculateTypeEffectiveness(moveData.elementalType, myTypes)
            probability *= (0.5 + effectiveness)

            predictions[moveName] = probability
        }

        val total = predictions.values.sum()
        return if (total > 0) {
            predictions.mapValues { it.value / total }
        } else {
            emptyMap()
        }
    }

    fun predictSwitchTarget(
        fromPokemon: String,
        myActivePokemon: TrackedPokemon
    ): Map<String, Double> {
        val pastSwitches = switchHistory[fromPokemon] ?: return emptyMap()

 
        val switchCounts = mutableMapOf<String, Int>()
        for (target in pastSwitches) {
            switchCounts.merge(target, 1, Int::plus)
        }

        val total = switchCounts.values.sum().toDouble()
        return if (total > 0) {
            switchCounts.mapValues { it.value / total }
        } else {
            emptyMap()
        }
    }

     
    fun categorizePlaystyle(pokemon: String): OpponentPlaystyle {
        val aggression = getAggressionLevel(pokemon)
        val setupTend = getSetupTendency(pokemon)
        val switchFreq = getSwitchFrequency(pokemon)

        return when {
            setupTend > 0.5 -> OpponentPlaystyle.SETUP_ORIENTED
            aggression > 0.7 && switchFreq < 0.3 -> OpponentPlaystyle.HYPER_OFFENSIVE
            switchFreq > 0.5 -> OpponentPlaystyle.MOMENTUM_BASED
            aggression < 0.4 -> OpponentPlaystyle.DEFENSIVE_STALL
            else -> OpponentPlaystyle.BALANCED
        }
    }

     
    fun reset() {
        moveHistory.clear()
        switchHistory.clear()
        situationResponses.clear()
        aggressionLevel.clear()
        setupTendency.clear()
        switchFrequency.clear()
    }
}

 
enum class OpponentPlaystyle {
    HYPER_OFFENSIVE, 
    SETUP_ORIENTED, 
    MOMENTUM_BASED, 
    DEFENSIVE_STALL, 
    BALANCED 
}
