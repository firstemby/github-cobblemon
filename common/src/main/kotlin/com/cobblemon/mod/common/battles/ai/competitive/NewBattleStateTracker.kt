package com.cobblemon.mod.common.battles.ai.competitive

import com.cobblemon.mod.common.battles.pokemon.BattlePokemon

typealias NewBattleState = BattleState
typealias NewTrackedPokemon = TrackedPokemon
typealias NewTeamTracker = TeamTracker
typealias NewGamePhase = GamePhase
typealias NewPokemonRole = PokemonRole
typealias NewBattleSituation = BattleSituation
typealias NewMatchupEvaluation = MatchupEvaluation
typealias NewMatchupRecommendation = MatchupRecommendation

class NewEnhancedActiveTracker {
    private val delegate = EnhancedActiveTracker()

    var p1Active: TeamTracker
        get() = delegate.p1Active
        set(value) {
            delegate.p1Active = value
        }

    var p2Active: TeamTracker
        get() = delegate.p2Active
        set(value) {
            delegate.p2Active = value
        }

    fun updatePokemonSlot(team: TeamTracker, slotIndex: Int, battlePokemon: BattlePokemon) {
        delegate.updatePokemonSlot(team, slotIndex, battlePokemon)
    }

    fun getTrackedPokemonByPNX(pnx: String, team: TeamTracker): TrackedPokemon? {
        return delegate.getTrackedPokemonByPNX(pnx, team)
    }

    fun updatePokemon(tracker: TrackedPokemon, battlePokemon: BattlePokemon) {
        delegate.updatePokemon(tracker, battlePokemon)
    }

    fun updateParty(team: TeamTracker, pokemon: TrackedPokemon) {
        delegate.updateParty(team, pokemon)
    }
}
