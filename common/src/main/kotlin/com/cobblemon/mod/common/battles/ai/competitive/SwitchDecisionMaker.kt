package com.cobblemon.mod.common.battles.ai.competitive

import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon
import com.cobblemon.mod.common.pokemon.Pokemon

class SwitchDecisionMaker(
    private val damageCalculator: DamageCalculator,
    private val opponentPredictor: OpponentPredictor,
    private val strategyAnalyzer: StrategyAnalyzer
) {
    fun shouldVoluntarilySwitch(
        myPokemon: TrackedPokemon,
        opponentPokemon: TrackedPokemon,
        battleState: BattleState,
        gamePlan: GamePlan,
        availableSwitches: List<BattlePokemon>
    ): Boolean {
        if (availableSwitches.isEmpty()) return false
        val currentMatchup = evaluateMatchup(myPokemon, opponentPokemon, battleState)
        val bestSwitchScore = availableSwitches.maxOfOrNull { switchOption ->
            evaluateSwitchOption(switchOption.effectedPokemon, opponentPokemon, battleState, gamePlan)
        } ?: 0.0

        val switchThreshold = 40.0
        if (bestSwitchScore > currentMatchup.overallScore + switchThreshold) {
            return true
        }

        return checkSwitchConditions(
            myPokemon,
            opponentPokemon,
            battleState,
            gamePlan,
            currentMatchup
        )
    }

    fun findBestSwitch(
        availableSwitches: List<BattlePokemon>,
        opponentPokemon: TrackedPokemon,
        battleState: BattleState,
        gamePlan: GamePlan
    ): BattlePokemon? {
        if (availableSwitches.isEmpty()) return null
        return availableSwitches.maxByOrNull { switchOption ->
            evaluateSwitchOption(switchOption.effectedPokemon, opponentPokemon, battleState, gamePlan)
        }
    }

    fun evaluateSwitchOption(
        candidate: Pokemon,
        opponent: TrackedPokemon,
        battleState: BattleState,
        gamePlan: GamePlan
    ): Double {
        var score = 0.0
        val candidateTracker = createTrackerPokemon(candidate)
        val matchup = evaluateMatchup(candidateTracker, opponent, battleState)
        score += matchup.overallScore

        val hpFactor = candidateTracker.currentHpPercent
        if (hpFactor < 0.5) {
            score -= (1.0 - hpFactor) * 40.0
        }
        if (gamePlan.keyPokemon.contains(candidate.species.name)) {
            score += 20.0
        }

        val mySpeed = getEffectiveStat(candidate, Stats.SPEED)
        val oppSpeed = opponent.pokemon?.let { getEffectiveStat(it, Stats.SPEED) } ?: 0.0
        if (mySpeed > oppSpeed) score += 25.0

        val hazardDamage = calculateEntryHazardDamage(candidate, battleState.npcHazards)
        score -= hazardDamage * 0.5

        val role = strategyAnalyzer.identifyPokemonRole(candidate)
        score += evaluateRoleSynergy(role, gamePlan, battleState)
        return score
    }

    private fun evaluateMatchup(
        myPokemon: TrackedPokemon,
        opponent: TrackedPokemon,
        battleState: BattleState
    ): MatchupEvaluation {
        val ourBestEffectiveness = calculateBestMoveEffectiveness(myPokemon, opponent)
        val offensiveAdvantage = (ourBestEffectiveness - 1.0) * 40.0

        val theirBestEffectiveness = calculateBestMoveEffectiveness(opponent, myPokemon)
        val defensiveAdvantage = -(theirBestEffectiveness - 1.0) * 35.0

        val mySpeed = myPokemon.pokemon?.let { getEffectiveStat(it, Stats.SPEED) } ?: 0.0
        val oppSpeed = opponent.pokemon?.let { getEffectiveStat(it, Stats.SPEED) } ?: 0.0
        val speedAdvantage = if (mySpeed > oppSpeed) 20.0 else -15.0

        val overallScore = offensiveAdvantage + defensiveAdvantage + speedAdvantage
        val recommendation = when {
            overallScore > 60 && myPokemon.currentHpPercent > 0.7 -> MatchupRecommendation.STAY_IN_AND_SETUP
            overallScore > 30 -> MatchupRecommendation.STAY_IN_AND_ATTACK
            overallScore > 0 -> MatchupRecommendation.CONSIDER_SWITCHING
            overallScore > -30 -> MatchupRecommendation.PIVOT_OUT
            else -> MatchupRecommendation.SWITCH_OUT
        }
        return MatchupEvaluation(
            offensiveAdvantage = offensiveAdvantage,
            defensiveAdvantage = defensiveAdvantage,
            speedAdvantage = speedAdvantage,
            overallScore = overallScore,
            recommendation = recommendation
        )
    }

    private fun checkSwitchConditions(
        myPokemon: TrackedPokemon,
        opponent: TrackedPokemon,
        battleState: BattleState,
        gamePlan: GamePlan,
        matchup: MatchupEvaluation
    ): Boolean {
        val theirBestEffectiveness = calculateBestMoveEffectiveness(opponent, myPokemon)
        if (theirBestEffectiveness >= 2.0 && myPokemon.currentHpPercent > 0.3) {
            return true
        }
        if (myPokemon.currentHpPercent < 0.3 && matchup.overallScore < 30) {
            return true
        }
        if (opponent.boosts.values.any { it >= 2 } && matchup.overallScore < 20) {
            return true
        }
        if (myPokemon.species in gamePlan.keyPokemon &&
            myPokemon.currentHpPercent < 0.5 &&
            theirBestEffectiveness > 1.0
        ) {
            return true
        }
        return false
    }

    private fun calculateBestMoveEffectiveness(
        attacker: TrackedPokemon,
        defender: TrackedPokemon
    ): Double {
        val moves = attacker.pokemon?.moveSet?.getMoves() ?: return 1.0
        return moves.maxOfOrNull { move ->
            TypeEffectiveness.calculateTypeEffectiveness(move.type, defender.currentTypes ?: listOf())
        } ?: 1.0
    }

    private fun calculateEntryHazardDamage(pokemon: Pokemon, hazards: List<String>): Double {
        var damage = 0.0
        val maxHP = getEffectiveStat(pokemon, Stats.HP)
        for (hazard in hazards) {
            when (hazard.lowercase()) {
                "stealthrock" -> {
                    val rockEffectiveness = pokemon.types.fold(1.0) { acc, type ->
                        acc * TypeEffectiveness.getDamageMultiplier(com.cobblemon.mod.common.api.types.ElementalTypes.ROCK, type)
                    }
                    damage += maxHP * rockEffectiveness * 0.125
                }
                "spikes" -> damage += maxHP * 0.125
                "toxicspikes" -> {
                    if (!pokemon.types.any { it.name.lowercase() in listOf("poison", "steel") }) {
                        damage += maxHP * 0.15
                    }
                }
                "stickyweb" -> damage += 10.0
            }
        }
        return damage
    }

    private fun evaluateRoleSynergy(
        role: PokemonRole,
        gamePlan: GamePlan,
        battleState: BattleState
    ): Double {
        return when (gamePlan.winCondition) {
            WinCondition.SETUP_SWEEP -> when (role) {
                PokemonRole.SETUP_SWEEPER -> 50.0
                PokemonRole.SUPPORT -> if (battleState.gamePhase == GamePhase.EARLY_GAME) 30.0 else 10.0
                else -> 0.0
            }
            WinCondition.AGGRESSIVE_SWEEP -> when (role) {
                PokemonRole.SWEEPER, PokemonRole.WALLBREAKER -> 40.0
                PokemonRole.REVENGE_KILLER -> 30.0
                else -> 0.0
            }
            WinCondition.DEFENSIVE_STALL -> when (role) {
                PokemonRole.TANK, PokemonRole.SUPPORT -> 45.0
                else -> -10.0
            }
            WinCondition.REVENGE_KILLING -> when (role) {
                PokemonRole.REVENGE_KILLER -> 60.0
                PokemonRole.PIVOT -> 25.0
                else -> 0.0
            }
            WinCondition.RAIN_OFFENSE, WinCondition.SUN_OFFENSE, WinCondition.SAND_OFFENSE -> when (role) {
                PokemonRole.SWEEPER -> 35.0
                else -> 0.0
            }
            else -> 0.0
        }
    }

    private fun createTrackerPokemon(pokemon: Pokemon): TrackedPokemon {
        return TrackedPokemon(
            pokemon = pokemon,
            originalPokemon = pokemon,
            species = pokemon.species.name,
            currentHp = pokemon.currentHealth,
            maxHp = pokemon.maxHealth,
            currentHpPercent = pokemon.currentHealth.toDouble() / pokemon.maxHealth,
            currentTypes = pokemon.types.map { it.name.lowercase() }.toMutableList(),
            currentAbility = pokemon.ability.name,
            moves = pokemon.moveSet.getMoves(),
            predictedRole = strategyAnalyzer.identifyPokemonRole(pokemon),
            cachedAttack = getEffectiveStat(pokemon, Stats.ATTACK),
            cachedDefense = getEffectiveStat(pokemon, Stats.DEFENCE),
            cachedSpAttack = getEffectiveStat(pokemon, Stats.SPECIAL_ATTACK),
            cachedSpDefense = getEffectiveStat(pokemon, Stats.SPECIAL_DEFENCE),
            cachedSpeed = getEffectiveStat(pokemon, Stats.SPEED)
        )
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
}
