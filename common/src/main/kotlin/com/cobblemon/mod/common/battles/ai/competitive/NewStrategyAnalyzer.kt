package com.cobblemon.mod.common.battles.ai.competitive

import com.cobblemon.mod.common.pokemon.Pokemon

class NewStrategyAnalyzer {
    internal val delegate = StrategyAnalyzer()

    fun analyzeTeamComposition(team: List<Pokemon>): GamePlan {
        return delegate.analyzeTeamComposition(team)
    }

    fun identifyPokemonRole(pokemon: Pokemon): PokemonRole {
        return delegate.identifyPokemonRole(pokemon)
    }
}
