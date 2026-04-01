package com.cobblemon.mod.common.battles.ai.competitive

import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.battles.ai.strongBattleAI.BattleAITags
import com.cobblemon.mod.common.pokemon.Pokemon
import kotlin.math.max

class StrategyAnalyzer {
    fun analyzeTeamComposition(team: List<Pokemon>): GamePlan {
        val roles = team.map { identifyPokemonRole(it) }
        val sweepers = roles.count { it == PokemonRole.SWEEPER }
        val setupSweepers = roles.count { it == PokemonRole.SETUP_SWEEPER }
        val wallbreakers = roles.count { it == PokemonRole.WALLBREAKER }
        val supports = roles.count { it in listOf(PokemonRole.SUPPORT, PokemonRole.TANK) }
        val revengeKillers = roles.count { it == PokemonRole.REVENGE_KILLER }

        val weatherStrategy = identifyWeatherStrategy(team)
        if (weatherStrategy != null) {
            return createWeatherGamePlan(weatherStrategy, team)
        }

        val hazardStrategy = identifyHazardStrategy(team)
        if (hazardStrategy) {
            return createHazardGamePlan(team)
        }

        val winCondition = when {
            setupSweepers >= 2 && supports >= 2 -> WinCondition.SETUP_SWEEP
            sweepers + wallbreakers >= 4 -> WinCondition.AGGRESSIVE_SWEEP
            supports >= 3 -> WinCondition.DEFENSIVE_STALL
            revengeKillers >= 2 -> WinCondition.REVENGE_KILLING
            else -> WinCondition.BALANCED
        }
        return createGamePlan(winCondition, team, roles)
    }

    private fun identifyWeatherStrategy(team: List<Pokemon>): WinCondition? {
        val rainSetters = team.count { hasWeatherSettingMove(it, "rain") || hasWeatherSettingAbility(it, "rain") }
        val rainAbusers = team.count { hasWeatherAbility(it, "rain") || hasWeatherMoves(it, "rain") }
        val sunSetters = team.count { hasWeatherSettingMove(it, "sun") || hasWeatherSettingAbility(it, "sun") }
        val sunAbusers = team.count { hasWeatherAbility(it, "sun") || hasWeatherMoves(it, "sun") }
        val sandSetters = team.count { hasWeatherSettingMove(it, "sand") || hasWeatherSettingAbility(it, "sand") }
        val sandAbusers = team.count { hasWeatherAbility(it, "sand") }
        return when {
            rainSetters >= 1 && rainAbusers >= 2 -> WinCondition.RAIN_OFFENSE
            sunSetters >= 1 && sunAbusers >= 2 -> WinCondition.SUN_OFFENSE
            sandSetters >= 1 && sandAbusers >= 2 -> WinCondition.SAND_OFFENSE
            rainSetters >= 1 || sunSetters >= 1 || sandSetters >= 1 -> WinCondition.WEATHER_CONTROL
            else -> null
        }
    }

    private fun identifyHazardStrategy(team: List<Pokemon>): Boolean {
        val hazardSetters = team.count { pokemon ->
            pokemon.moveSet.getMoves().any { move ->
                val id = move.name.lowercase()
                id in MoveData.entryHazards || "hazard_set" in BattleAITags.getMoveTags(id)
            }
        }
        val hazardRemovers = team.count { pokemon ->
            pokemon.moveSet.getMoves().any { it.name.lowercase() in MoveData.antiHazardsMoves }
        }
        return hazardSetters >= 2 && hazardRemovers >= 1
    }

    private fun createWeatherGamePlan(winCondition: WinCondition, team: List<Pokemon>): GamePlan {
        val weatherType = when (winCondition) {
            WinCondition.RAIN_OFFENSE -> "rain"
            WinCondition.SUN_OFFENSE -> "sun"
            WinCondition.SAND_OFFENSE -> "sand"
            else -> "any"
        }
        val setters = team.filter { hasWeatherSettingMove(it, weatherType) || hasWeatherSettingAbility(it, weatherType) }
        val abusers = team.filter { hasWeatherAbility(it, weatherType) || hasWeatherMoves(it, weatherType) }
        val keyPokemon = (setters + abusers).distinct().map { it.species.name }
        val priorities = when (winCondition) {
            WinCondition.RAIN_OFFENSE -> listOf(
                "set_rain_immediately",
                "preserve_rain_setter",
                "sweep_with_rain_abusers",
                "use_perfect_accuracy_moves",
                "prevent_weather_changes"
            )
            WinCondition.SUN_OFFENSE -> listOf(
                "set_sun_immediately",
                "preserve_sun_setter",
                "sweep_with_chlorophyll",
                "use_solar_beam",
                "prevent_weather_changes"
            )
            WinCondition.SAND_OFFENSE -> listOf(
                "set_sandstorm",
                "abuse_sand_rush",
                "chip_damage_from_sand",
                "prevent_weather_changes"
            )
            else -> listOf("maintain_weather", "use_weather_abusers")
        }

        return GamePlan(
            winCondition = winCondition,
            keyPokemon = keyPokemon,
            priorities = priorities,
            riskyMoves = MoveData.highRiskMoves,
            aggressionLevel = 0.8,
            setupPriority = 0.9
        )
    }

    private fun createHazardGamePlan(team: List<Pokemon>): GamePlan {
        val hazardSetters = team.filter { pokemon ->
            pokemon.moveSet.getMoves().any { it.name.lowercase() in MoveData.entryHazards }
        }.map { it.species.name }

        return GamePlan(
            winCondition = WinCondition.HAZARD_CONTROL,
            keyPokemon = hazardSetters,
            priorities = listOf(
                "set_stealth_rock_early",
                "stack_hazards_if_safe",
                "remove_opponent_hazards",
                "force_switches",
                "preserve_hazard_setters"
            ),
            riskyMoves = listOf("explosion"),
            aggressionLevel = 0.6,
            setupPriority = 0.7
        )
    }

    private fun createGamePlan(winCondition: WinCondition, team: List<Pokemon>, roles: List<PokemonRole>): GamePlan {
        val keyPokemon = identifyKeyPokemon(winCondition, team, roles)
        val priorities = when (winCondition) {
            WinCondition.SETUP_SWEEP -> listOf(
                "create_setup_opportunity",
                "execute_setup_moves",
                "sweep_after_setup",
                "preserve_setup_sweeper",
                "remove_priority_threats"
            )
            WinCondition.AGGRESSIVE_SWEEP -> listOf(
                "maintain_offensive_pressure",
                "use_coverage_moves",
                "preserve_sweepers",
                "use_priority_to_finish",
                "momentum_over_setup"
            )
            WinCondition.DEFENSIVE_STALL -> listOf(
                "spread_status_conditions",
                "use_recovery_moves",
                "set_up_hazards",
                "stall_out_threats",
                "remove_opponent_hazards"
            )
            WinCondition.REVENGE_KILLING -> listOf(
                "preserve_revenge_killer",
                "use_priority_strategically",
                "chip_into_priority_range",
                "scout_with_protect",
                "maintain_momentum"
            )
            WinCondition.HAZARD_CONTROL -> listOf(
                "set_stealth_rock_early",
                "stack_hazards_safely",
                "remove_opponent_hazards",
                "force_switches"
            )
            else -> listOf(
                "maintain_balance",
                "adapt_to_opponent",
                "resource_management",
                "take_calculated_risks"
            )
        }

        val riskyMoves = when (winCondition) {
            WinCondition.SETUP_SWEEP -> MoveData.selfKoMoves
            WinCondition.REVENGE_KILLING -> MoveData.highRiskMoves
            WinCondition.DEFENSIVE_STALL -> MoveData.highRiskMoves
            else -> MoveData.selfKoMoves
        }

        val aggressionLevel = when (winCondition) {
            WinCondition.AGGRESSIVE_SWEEP -> 0.9
            WinCondition.SETUP_SWEEP -> 0.7
            WinCondition.REVENGE_KILLING -> 0.75
            WinCondition.DEFENSIVE_STALL -> 0.3
            else -> 0.6
        }

        val setupPriority = when (winCondition) {
            WinCondition.SETUP_SWEEP -> 0.95
            WinCondition.DEFENSIVE_STALL -> 0.6
            else -> 0.5
        }

        return GamePlan(
            winCondition = winCondition,
            keyPokemon = keyPokemon,
            priorities = priorities,
            riskyMoves = riskyMoves,
            aggressionLevel = aggressionLevel,
            setupPriority = setupPriority
        )
    }

    private fun identifyKeyPokemon(
        winCondition: WinCondition,
        team: List<Pokemon>,
        roles: List<PokemonRole>
    ): List<String> {
        return when (winCondition) {
            WinCondition.SETUP_SWEEP -> team.filterIndexed { index, _ -> roles[index] == PokemonRole.SETUP_SWEEPER }.map { it.species.name }
            WinCondition.AGGRESSIVE_SWEEP -> team.filterIndexed { index, _ ->
                roles[index] in listOf(PokemonRole.SWEEPER, PokemonRole.WALLBREAKER)
            }.map { it.species.name }
            WinCondition.REVENGE_KILLING -> team.filterIndexed { index, _ -> roles[index] == PokemonRole.REVENGE_KILLER }.map { it.species.name }
            WinCondition.DEFENSIVE_STALL -> team.filterIndexed { index, _ ->
                roles[index] in listOf(PokemonRole.TANK, PokemonRole.SUPPORT)
            }.map { it.species.name }
            else -> team.take(2).map { it.species.name }
        }
    }

    fun identifyPokemonRole(pokemon: Pokemon): PokemonRole {
        val attack = pokemon.species.baseStats[Stats.ATTACK] ?: 0
        val spAttack = pokemon.species.baseStats[Stats.SPECIAL_ATTACK] ?: 0
        val defense = pokemon.species.baseStats[Stats.DEFENCE] ?: 0
        val spDefense = pokemon.species.baseStats[Stats.SPECIAL_DEFENCE] ?: 0
        val speed = pokemon.species.baseStats[Stats.SPEED] ?: 0
        val hp = pokemon.species.baseStats[Stats.HP] ?: 0

        val maxAttack = max(attack, spAttack)
        val avgDefense = (defense + spDefense) / 2.0
        val bulk = hp + avgDefense

        val hasSetupMoves = pokemon.moveSet.getMoves().any { it.name.lowercase() in MoveData.boostFromMoves.keys }
        val hasPriorityMoves = pokemon.moveSet.getMoves().any { it.name.lowercase() in MoveData.priorityMoves }
        val hasPivotMoves = pokemon.moveSet.getMoves().any { it.name.lowercase() in MoveData.pivotMoves }
        val hasRecoveryMoves = pokemon.moveSet.getMoves().any { it.name.lowercase() in MoveData.selfRecoveryMoves }

        return when {
            hasPivotMoves -> PokemonRole.PIVOT
            hasSetupMoves && speed >= 90 && maxAttack >= 90 -> PokemonRole.SETUP_SWEEPER
            speed >= 110 && hasPriorityMoves -> PokemonRole.REVENGE_KILLER
            speed >= 100 && maxAttack >= 100 -> PokemonRole.SWEEPER
            maxAttack >= 120 -> PokemonRole.WALLBREAKER
            bulk >= 180 && maxAttack < 80 -> PokemonRole.TANK
            hasRecoveryMoves || hasSetupMoves -> PokemonRole.SUPPORT
            else -> PokemonRole.BALANCED
        }
    }

    private fun hasWeatherSettingMove(pokemon: Pokemon, weather: String): Boolean {
        val moves = pokemon.moveSet.getMoves().map { it.name.lowercase() }
        return moves.any { moveId -> canonicalWeather(MoveData.weatherMoves[moveId]) == weather }
    }

    private fun hasWeatherSettingAbility(pokemon: Pokemon, weather: String): Boolean {
        val ability = pokemon.ability.name.lowercase()
        val abilityTags = BattleAITags.getAbilityTags(ability)
        if ("weather_setter" !in abilityTags) {
            return false
        }
        val conditional = abilityTags.firstOrNull { it.startsWith("conditional_weather_") }?.removePrefix("conditional_weather_")
        return conditional == null || canonicalWeather(conditional) == weather
    }

    private fun hasWeatherAbility(pokemon: Pokemon, weather: String): Boolean {
        val ability = pokemon.ability.name.lowercase()
        val abilityTags = BattleAITags.getAbilityTags(ability)
        if (abilityTags.any { it == "weather_interaction" || it == "self_chip_weather" }) {
            val conditional = abilityTags.firstOrNull { it.startsWith("conditional_weather_") }?.removePrefix("conditional_weather_")
            if (conditional == null) {
                return true
            }
            return canonicalWeather(conditional) == weather
        }
        return canonicalWeather(MoveData.weatherAbilities[ability]) == weather
    }

    private fun hasWeatherMoves(pokemon: Pokemon, weather: String): Boolean {
        val moves = pokemon.moveSet.getMoves().map { it.name.lowercase() }
        return moves.any { moveId -> canonicalWeather(MoveData.weatherDependentMoves[moveId]) == weather }
    }

    private fun canonicalWeather(raw: String?): String? {
        return when (raw?.lowercase()) {
            null -> null
            "rain", "raindance", "raining", "heavyrain" -> "rain"
            "sun", "sunny", "sunnyday", "harshsunlight", "desolateland" -> "sun"
            "sand", "sandstorm" -> "sand"
            "hail" -> "hail"
            "snow", "snowscape" -> "snow"
            else -> raw.lowercase()
        }
    }
}

enum class WinCondition {
    AGGRESSIVE_SWEEP,
    SETUP_SWEEP,
    DEFENSIVE_STALL,
    HAZARD_CONTROL,
    REVENGE_KILLING,
    BALANCED,
    RAIN_OFFENSE,
    SUN_OFFENSE,
    SAND_OFFENSE,
    WEATHER_CONTROL
}

data class GamePlan(
    val winCondition: WinCondition,
    val keyPokemon: List<String>,
    val priorities: List<String>,
    val riskyMoves: List<String>,
    val aggressionLevel: Double = 0.6,
    val setupPriority: Double = 0.5
)
