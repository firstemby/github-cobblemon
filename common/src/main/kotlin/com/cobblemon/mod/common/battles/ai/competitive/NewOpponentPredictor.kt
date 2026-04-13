package com.cobblemon.mod.common.battles.ai.competitive

class NewOpponentPredictor {
    internal val delegate = OpponentPredictor()

    fun updateMoveHistory(pokemon: String, move: String, situation: String) {
        delegate.updateMoveHistory(pokemon, move, situation)
    }

    fun updateSwitchPattern(fromPokemon: String, toPokemon: String, reason: String) {
        delegate.updateSwitchPattern(fromPokemon, toPokemon, reason)
    }

    fun predictNextMove(pokemon: String, situation: String): Map<String, Double> {
        return delegate.predictNextMove(pokemon, situation)
    }

    fun getSwitchProbability(pokemon: String, situation: String): Double {
        return delegate.getSwitchProbability(pokemon, situation)
    }

    fun predictMoveHeuristically(opponent: TrackedPokemon, myPokemon: TrackedPokemon): Map<String, Double> {
        return delegate.predictMoveHeuristically(opponent, myPokemon)
    }

    fun predictSwitchTarget(fromPokemon: String, myActivePokemon: TrackedPokemon): Map<String, Double> {
        return delegate.predictSwitchTarget(fromPokemon, myActivePokemon)
    }

    fun categorizePlaystyle(pokemon: String): OpponentPlaystyle {
        return delegate.categorizePlaystyle(pokemon)
    }

    fun reset() {
        delegate.reset()
    }
}
