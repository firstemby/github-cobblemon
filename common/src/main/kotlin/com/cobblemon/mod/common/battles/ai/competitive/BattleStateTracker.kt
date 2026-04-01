package com.cobblemon.mod.common.battles.ai.competitive

import com.cobblemon.mod.common.api.moves.Move
import com.cobblemon.mod.common.api.pokemon.stats.Stat
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.battles.interpreter.ContextManager
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon
import com.cobblemon.mod.common.pokemon.Pokemon

data class BattleState(
    val turn: Int,
    val playerTeam: List<Pokemon>,
    val npcTeam: List<Pokemon>,
    val playerActive: Pokemon,
    val npcActive: Pokemon,
    val weather: String?,
    val terrain: String?,
    val playerHazards: List<String>,
    val npcHazards: List<String>,
    val playerBoosts: Map<Stat, Int>,
    val npcBoosts: Map<Stat, Int>,
    val battleContext: ContextManager,
    val gamePhase: GamePhase = determineGamePhase(turn, npcTeam, playerTeam),
    val isDoubles: Boolean = false,
    val playerActiveSlots: List<Pokemon> = listOf(playerActive),
    val npcActiveSlots: List<Pokemon> = listOf(npcActive),
    val mySlotIndex: Int = 0,
    val partnerSlotIndex: Int? = null
) {
    companion object {
        private fun determineGamePhase(turn: Int, npcTeam: List<Pokemon>, playerTeam: List<Pokemon>): GamePhase {
            val npcAlive = npcTeam.count { it.currentHealth > 0 }
            val playerAlive = playerTeam.count { it.currentHealth > 0 }
            return when {
                turn <= 3 -> GamePhase.EARLY_GAME
                npcAlive <= 2 || playerAlive <= 2 -> GamePhase.ENDGAME
                turn >= 15 -> GamePhase.LATE_GAME
                else -> GamePhase.MID_GAME
            }
        }
    }
}

enum class GamePhase {
    EARLY_GAME,
    MID_GAME,
    LATE_GAME,
    ENDGAME
}

data class TrackedPokemon(
    var pokemon: Pokemon? = null,
    var originalPokemon: Pokemon? = null,
    var species: String? = null,
    var currentHp: Int = 0,
    var currentHpPercent: Double = 0.0,
    var maxHp: Int = 0,
    var previousHp: Int = 0,
    var lastHpChange: Int = 0,
    var boosts: Map<Stat, Int> = mapOf(),
    var atkBoost: Int = 0,
    var defBoost: Int = 0,
    var spaBoost: Int = 0,
    var spdBoost: Int = 0,
    var speBoost: Int = 0,
    var currentAbility: String? = null,
    var currentTypes: MutableList<String>? = null,
    var stats: Map<Stat, Int> = mapOf(),
    var moves: List<Move> = listOf(),
    var revealedMoves: MutableSet<String> = mutableSetOf(),
    var cachedAttack: Double = 0.0,
    var cachedDefense: Double = 0.0,
    var cachedSpAttack: Double = 0.0,
    var cachedSpDefense: Double = 0.0,
    var cachedSpeed: Double = 0.0,
    var sideConditions: Map<String, Any> = mapOf(),
    var firstTurn: Int = 0,
    var protectCount: Int = 0,
    var lastMoveDamage: Int = 0,
    var statusTurns: Int = 0,
    var switchInTurn: Int = 0,
    var predictedRole: PokemonRole = PokemonRole.UNKNOWN,
    var threatLevel: Int = 0
)

enum class PokemonRole {
    UNKNOWN,
    SWEEPER,
    SETUP_SWEEPER,
    WALLBREAKER,
    REVENGE_KILLER,
    TANK,
    SUPPORT,
    PIVOT,
    BALANCED
}

data class TeamTracker(
    var party: MutableList<TrackedPokemon> = mutableListOf(),
    var uuid: String? = null,
    var activePokemon: TrackedPokemon = TrackedPokemon(),
    var activeSlots: MutableList<TrackedPokemon> = mutableListOf(),
    var nRemainingMons: Int = 0,
    var hazardDamage: Int = 0,
    var teamSynergy: Double = 0.0,
    var momentum: Double = 0.0
) {
    fun getActiveAt(slotIndex: Int): TrackedPokemon? = activeSlots.getOrNull(slotIndex)

    fun getAllActive(): List<TrackedPokemon> = activeSlots.filter { it.currentHp > 0 }

    fun syncLegacyActivePokemon() {
        if (activeSlots.isNotEmpty()) {
            activePokemon = activeSlots[0]
        }
    }
}

class EnhancedActiveTracker {
    var p1Active: TeamTracker = TeamTracker()
    var p2Active: TeamTracker = TeamTracker()

    fun updatePokemonSlot(team: TeamTracker, slotIndex: Int, battlePokemon: BattlePokemon) {
        while (team.activeSlots.size <= slotIndex) {
            team.activeSlots.add(TrackedPokemon())
        }
        updatePokemon(team.activeSlots[slotIndex], battlePokemon)
        team.syncLegacyActivePokemon()
    }

    fun getTrackedPokemonByPNX(pnx: String, team: TeamTracker): TrackedPokemon? {
        if (pnx.length < 3) return null
        val slotIndex = pnx[2] - 'a'
        return team.getActiveAt(slotIndex)
    }

    fun updatePokemon(tracker: TrackedPokemon, battlePokemon: BattlePokemon) {
        val pokemon = battlePokemon.effectedPokemon
        tracker.pokemon = pokemon
        tracker.originalPokemon = battlePokemon.originalPokemon
        tracker.species = pokemon.species.name
        tracker.currentHp = pokemon.currentHealth
        tracker.maxHp = pokemon.maxHealth
        tracker.currentHpPercent = pokemon.currentHealth.toDouble() / pokemon.maxHealth
        tracker.currentTypes = pokemon.types.map { it.name.lowercase() }.toMutableList()
        tracker.currentAbility = pokemon.ability.name
        tracker.moves = pokemon.moveSet.getMoves()
        tracker.boosts = battlePokemon.statChanges
        tracker.atkBoost = battlePokemon.statChanges[Stats.ATTACK] ?: 0
        tracker.defBoost = battlePokemon.statChanges[Stats.DEFENCE] ?: 0
        tracker.spaBoost = battlePokemon.statChanges[Stats.SPECIAL_ATTACK] ?: 0
        tracker.spdBoost = battlePokemon.statChanges[Stats.SPECIAL_DEFENCE] ?: 0
        tracker.speBoost = battlePokemon.statChanges[Stats.SPEED] ?: 0
        cacheStats(tracker)
    }

    private fun cacheStats(tracker: TrackedPokemon) {
        val pokemon = tracker.pokemon ?: return
        tracker.cachedAttack = calculateStat(pokemon, Stats.ATTACK)
        tracker.cachedDefense = calculateStat(pokemon, Stats.DEFENCE)
        tracker.cachedSpAttack = calculateStat(pokemon, Stats.SPECIAL_ATTACK)
        tracker.cachedSpDefense = calculateStat(pokemon, Stats.SPECIAL_DEFENCE)
        tracker.cachedSpeed = calculateStat(pokemon, Stats.SPEED)
    }

    private fun calculateStat(pokemon: Pokemon, stat: Stat): Double {
        val base = pokemon.species.baseStats[stat] ?: 0
        val iv = pokemon.ivs[stat] ?: 31
        val ev = pokemon.evs[stat] ?: 0
        val level = pokemon.level
        return ((2.0 * base + iv + ev / 4.0) * level / 100.0 + 5.0)
    }

    fun updateParty(team: TeamTracker, pokemon: TrackedPokemon) {
        val existingIndex = team.party.indexOfFirst { it.pokemon?.uuid == pokemon.pokemon?.uuid }
        if (existingIndex == -1) {
            team.party.add(pokemon)
        } else {
            team.party[existingIndex] = pokemon
        }
    }
}

data class FearThreat(
    val pokemon: Pokemon,
    val hasEndeavor: Boolean,
    val hasPriorityMove: Boolean,
    val hasFocusItem: Boolean,
    val threatLevel: Int
)

enum class BattleSituation {
    DESPERATE,
    WINNING,
    EARLY_GAME,
    ENDGAME,
    OPPONENT_SETUP,
    MY_SETUP,
    NEUTRAL,
    HAZARD_ADVANTAGE,
    WEATHER_CONTROL
}

data class MatchupEvaluation(
    val offensiveAdvantage: Double,
    val defensiveAdvantage: Double,
    val speedAdvantage: Double,
    val overallScore: Double,
    val recommendation: MatchupRecommendation
)

enum class MatchupRecommendation {
    STAY_IN_AND_ATTACK,
    STAY_IN_AND_SETUP,
    STAY_IN_AND_STATUS,
    CONSIDER_SWITCHING,
    SWITCH_OUT,
    PIVOT_OUT
}
