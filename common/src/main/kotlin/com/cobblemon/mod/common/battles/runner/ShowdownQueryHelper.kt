/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battles.runner

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import java.util.UUID

/**
 * Helper class for parsing JSON responses from Showdown query methods.
 * Provides typed data classes and parsing utilities for AI decision-making.
 *
 * @author AmoAster
 */
object ShowdownQueryHelper {

    private val gson = Gson()

    // ============================================================================
    // Response Data Classes
    // ============================================================================

    /**
     * Response from getEffectiveStat
     */
    data class EffectiveStatResponse(
        val stat: Int? = null,
        val error: String? = null
    ) {
        val isSuccess: Boolean get() = error == null && stat != null
    }

    /**
     * Response from getActionSpeed
     */
    data class ActionSpeedResponse(
        val speed: Int? = null,
        val trickRoom: Boolean = false,
        val error: String? = null
    ) {
        val isSuccess: Boolean get() = error == null && speed != null
    }

    /**
     * Response from getTypeEffectiveness
     */
    data class TypeEffectivenessResponse(
        val effectiveness: Double? = null,
        val immune: Boolean = false,
        val typeMod: Int? = null,
        val error: String? = null
    ) {
        val isSuccess: Boolean get() = error == null && effectiveness != null

        val isSuperEffective: Boolean get() = (effectiveness ?: 1.0) > 1.0
        val isNotVeryEffective: Boolean get() = (effectiveness ?: 1.0) < 1.0 && !immune
        val isNeutral: Boolean get() = (effectiveness ?: 1.0) == 1.0
    }

    /**
     * Response from estimateDamage
     */
    data class DamageEstimateResponse(
        val damage: Int? = null,
        val damagePercent: Double? = null,
        val minDamage: Int? = null,
        val maxDamage: Int? = null,
        val minPercent: Double? = null,
        val maxPercent: Double? = null,
        val defenderHP: Int? = null,
        val defenderMaxHP: Int? = null,
        val immune: Boolean = false,
        val failed: Boolean = false,
        val effectiveness: Double? = null,
        val isOHKO: Boolean = false,
        @SerializedName("is2HKO")
        val is2HKO: Boolean = false,
        val error: String? = null
    ) {
        val isSuccess: Boolean get() = error == null && (damage != null || immune || failed)

        /**
         * Calculate how many hits to KO based on average damage
         */
        fun hitsToKO(): Int {
            if (immune || failed || damage == null || damage <= 0 || defenderHP == null) return Int.MAX_VALUE
            return kotlin.math.ceil(defenderHP.toDouble() / damage).toInt()
        }

        /**
         * Get guaranteed KO count (using minimum damage)
         */
        fun guaranteedHitsToKO(): Int {
            if (immune || failed || minDamage == null || minDamage <= 0 || defenderHP == null) return Int.MAX_VALUE
            return kotlin.math.ceil(defenderHP.toDouble() / minDamage).toInt()
        }
    }

    /**
     * Response from canUseMove
     */
    data class CanUseMoveResponse(
        val canUse: Boolean = false,
        val reason: String? = null,
        val disabledBy: String? = null,
        val pp: Int? = null,
        val maxpp: Int? = null,
        val error: String? = null
    ) {
        val isSuccess: Boolean get() = error == null
    }

    /**
     * Active Pokemon data from getBattleState
     */
    data class ActivePokemonData(
        val uuid: String? = null,
        val species: String? = null,
        val hp: Int? = null,
        val maxhp: Int? = null,
        val status: String? = null,
        val side: String? = null,
        val position: Int? = null,
        val types: List<String> = emptyList(),
        val ability: String? = null,
        val item: String? = null,
        val boosts: Map<String, Int> = emptyMap(),
        val volatiles: List<String> = emptyList(),
        val stats: Map<String, Int> = emptyMap(),
        val actionSpeed: Int? = null
    ) {
        val hpPercent: Double get() = if (maxhp != null && maxhp > 0) hp?.toDouble()?.div(maxhp) ?: 0.0 else 0.0

        fun hasVolatile(volatile: String): Boolean = volatiles.contains(volatile)

        fun getBoost(stat: String): Int = boosts[stat] ?: 0

        fun getStat(stat: String): Int = stats[stat] ?: 0
    }

    /**
     * Side condition data from getBattleState
     */
    data class SideConditionData(
        val duration: Int? = null,
        val layers: Int? = null
    )

    /**
     * Response from getBattleState
     */
    data class BattleStateResponse(
        val turn: Int? = null,
        val weather: String? = null,
        val weatherDuration: Int? = null,
        val terrain: String? = null,
        val terrainDuration: Int? = null,
        val pseudoWeathers: Map<String, PseudoWeatherData> = emptyMap(),
        val sideConditions: Map<String, Map<String, SideConditionData>> = emptyMap(),
        val activePokemon: List<ActivePokemonData> = emptyList(),
        val error: String? = null
    ) {
        val isSuccess: Boolean get() = error == null

        fun hasTrickRoom(): Boolean = pseudoWeathers.containsKey("trickroom")

        fun hasWeather(weatherName: String): Boolean = weather?.equals(weatherName, ignoreCase = true) == true

        fun hasTerrain(terrainName: String): Boolean = terrain?.equals(terrainName, ignoreCase = true) == true

        fun getSideConditions(sideId: String): Map<String, SideConditionData> = sideConditions[sideId] ?: emptyMap()

        fun hasSideCondition(sideId: String, condition: String): Boolean =
            getSideConditions(sideId).containsKey(condition)

        fun getActivePokemonBySide(sideId: String): List<ActivePokemonData> =
            activePokemon.filter { it.side == sideId }

        fun getActivePokemonByUUID(uuid: String): ActivePokemonData? =
            activePokemon.find { it.uuid == uuid }
    }

    /**
     * Pseudo-weather data (Trick Room, etc.)
     */
    data class PseudoWeatherData(
        val duration: Int? = null
    )

    // ============================================================================
    // Parsing Methods
    // ============================================================================

    /**
     * Parse getEffectiveStat response
     */
    fun parseEffectiveStat(json: String): EffectiveStatResponse {
        return try {
            gson.fromJson(json, EffectiveStatResponse::class.java)
        } catch (e: Exception) {
            EffectiveStatResponse(error = "Parse error: ${e.message}")
        }
    }

    /**
     * Parse getActionSpeed response
     */
    fun parseActionSpeed(json: String): ActionSpeedResponse {
        return try {
            gson.fromJson(json, ActionSpeedResponse::class.java)
        } catch (e: Exception) {
            ActionSpeedResponse(error = "Parse error: ${e.message}")
        }
    }

    /**
     * Parse getTypeEffectiveness response
     */
    fun parseTypeEffectiveness(json: String): TypeEffectivenessResponse {
        return try {
            gson.fromJson(json, TypeEffectivenessResponse::class.java)
        } catch (e: Exception) {
            TypeEffectivenessResponse(error = "Parse error: ${e.message}")
        }
    }

    /**
     * Parse estimateDamage response
     */
    fun parseDamageEstimate(json: String): DamageEstimateResponse {
        return try {
            gson.fromJson(json, DamageEstimateResponse::class.java)
        } catch (e: Exception) {
            DamageEstimateResponse(error = "Parse error: ${e.message}")
        }
    }

    /**
     * Parse canUseMove response
     */
    fun parseCanUseMove(json: String): CanUseMoveResponse {
        return try {
            gson.fromJson(json, CanUseMoveResponse::class.java)
        } catch (e: Exception) {
            CanUseMoveResponse(error = "Parse error: ${e.message}")
        }
    }

    /**
     * Parse getBattleState response
     */
    fun parseBattleState(json: String): BattleStateResponse {
        return try {
            gson.fromJson(json, BattleStateResponse::class.java)
        } catch (e: Exception) {
            BattleStateResponse(error = "Parse error: ${e.message}")
        }
    }

    // ============================================================================
    // Convenience Methods (Query + Parse in one call)
    // ============================================================================

    /**
     * Get effective stat with typed response
     */
    fun getEffectiveStat(battleId: UUID, pokemonUUID: UUID, statName: String): EffectiveStatResponse {
        val json = ShowdownService.service.getEffectiveStat(battleId, pokemonUUID, statName)
        return parseEffectiveStat(json)
    }

    /**
     * Get action speed with typed response
     */
    fun getActionSpeed(battleId: UUID, pokemonUUID: UUID): ActionSpeedResponse {
        val json = ShowdownService.service.getActionSpeed(battleId, pokemonUUID)
        return parseActionSpeed(json)
    }

    /**
     * Get type effectiveness with typed response
     */
    fun getTypeEffectiveness(battleId: UUID, attackerUUID: UUID, defenderUUID: UUID, moveId: String): TypeEffectivenessResponse {
        val json = ShowdownService.service.getTypeEffectiveness(battleId, attackerUUID, defenderUUID, moveId)
        return parseTypeEffectiveness(json)
    }

    /**
     * Estimate damage with typed response
     */
    fun estimateDamage(battleId: UUID, attackerUUID: UUID, defenderUUID: UUID, moveId: String): DamageEstimateResponse {
        val json = ShowdownService.service.estimateDamage(battleId, attackerUUID, defenderUUID, moveId)
        return parseDamageEstimate(json)
    }

    /**
     * Get battle state with typed response
     */
    fun getBattleState(battleId: UUID, sideId: String): BattleStateResponse {
        val json = ShowdownService.service.getBattleState(battleId, sideId)
        return parseBattleState(json)
    }

    /**
     * Check if move can be used with typed response
     */
    fun canUseMove(battleId: UUID, pokemonUUID: UUID, moveId: String): CanUseMoveResponse {
        val json = ShowdownService.service.canUseMove(battleId, pokemonUUID, moveId)
        return parseCanUseMove(json)
    }

    // ============================================================================
    // Utility Methods
    // ============================================================================

    /**
     * Check if a JSON response contains an error
     */
    fun hasError(json: String): Boolean {
        return try {
            val obj = JsonParser.parseString(json).asJsonObject
            obj.has("error")
        } catch (e: Exception) {
            true
        }
    }

    /**
     * Extract error message from JSON response
     */
    fun getError(json: String): String? {
        return try {
            val obj = JsonParser.parseString(json).asJsonObject
            if (obj.has("error")) obj.get("error").asString else null
        } catch (e: Exception) {
            "Failed to parse response"
        }
    }
}
