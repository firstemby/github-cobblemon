package com.cobblemon.mod.common.battles.ai.competitive

import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.battles.InBattleMove

class NewDamageCalculator(battle: PokemonBattle?) {
    internal val delegate = DamageCalculator(battle)

    fun calculateDamage(
        move: InBattleMove,
        attacker: TrackedPokemon,
        defender: TrackedPokemon,
        weather: String? = null,
        criticalHit: Boolean = false,
        isSpreadMove: Boolean = false,
        targetCount: Int = 1
    ): Double {
        val base = delegate.calculateDamage(move, attacker, defender, weather, criticalHit, isSpreadMove, targetCount)
        if (base <= 0.0) {
            return 0.0
        }

        val moveData = Moves.getByName(move.id) ?: return base
        val moveTags = NewAiBattleUtils.moveTags(move.id)
        var adjusted = base

        if ("delay_1" in moveTags) {
            adjusted *= 0.92
        }
        if ("recharge_turn" in moveTags) {
            adjusted *= 0.87
        }
        if ("ohko" in moveTags) {
            adjusted *= 0.45
        }
        if ("context_weather_sensitive" in moveTags) {
            val normalizedWeather = NewAiBattleUtils.normalizeWeatherId(weather)
            if (normalizedWeather == null) {
                adjusted *= 0.95
            }
        }
        if (moveData.power <= 0 && moveTags.any { it.startsWith("stat_") }) {
            adjusted *= 0.1
        }

        return adjusted
    }
}
