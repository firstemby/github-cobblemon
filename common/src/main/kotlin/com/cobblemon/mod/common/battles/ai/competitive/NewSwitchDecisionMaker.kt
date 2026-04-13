package com.cobblemon.mod.common.battles.ai.competitive

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

    fun shouldVoluntarilySwitch(
        myPokemon: TrackedPokemon,
        opponentPokemon: TrackedPokemon,
        battleState: BattleState,
        gamePlan: GamePlan,
        availableSwitches: List<BattlePokemon>
    ): Boolean {
        return delegate.shouldVoluntarilySwitch(myPokemon, opponentPokemon, battleState, gamePlan, availableSwitches)
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
}
