/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battles.runner

import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.battles.runner.graal.GraalShowdownService
import com.cobblemon.mod.common.battles.runner.socket.SocketShowdownService
import com.google.gson.JsonArray
import java.util.UUID

/**
 * Mediator service for communicating between the Cobblemon Minecraft mod and Cobblemon showdown service.
 *
 * All outgoing calls to showdown should be done through this layer to decouple the mod from a specific implementation.
 * To get an instance of the service, use the singleton provided via `ShowdownService.get()`
 *
 * @since February 27th, 2023
 * @author landonjw
 */
interface ShowdownService {
    fun openConnection()
    fun closeConnection()
    fun startBattle(battle: PokemonBattle, messages: Array<String>)
    fun endBattle(battle: PokemonBattle)
    fun send(battleId: UUID, messages: Array<String>)
    fun getRegistryData(type: String): JsonArray
    fun sendRegistryData(data: Map<String, String>, type: String)
    fun sendRegistryEntry(data: String, type: String)
    fun resetRegistryData(type: String)
    fun resetAllRegistries()
    fun indicateSpeciesInitialized() {}

    // ============================================================================
    // AI Query Methods
    // These methods query Showdown for battle state information used by the AI.
    // They expose Showdown's internal calculations without hardcoding mechanics.
    // Author: Amo Aster
    // ============================================================================

    /**
     * Get the effective stat for a Pokemon after all modifiers (abilities, items, weather, etc.)
     * @param battleId The battle UUID
     * @param pokemonUUID The Pokemon's UUID
     * @param statName The stat to query: "atk", "def", "spa", "spd", "spe"
     * @return JSON string with { stat: number } or { error: string }
     */
    fun getEffectiveStat(battleId: UUID, pokemonUUID: UUID, statName: String): String {
        return """{"error": "Not implemented"}"""
    }

    /**
     * Get the action speed for a Pokemon (includes Trick Room, all speed modifiers)
     * @param battleId The battle UUID
     * @param pokemonUUID The Pokemon's UUID
     * @return JSON string with { speed: number, trickRoom: boolean } or { error: string }
     */
    fun getActionSpeed(battleId: UUID, pokemonUUID: UUID): String {
        return """{"error": "Not implemented"}"""
    }

    /**
     * Get type effectiveness of a move against a target Pokemon (accounts for abilities)
     * @param battleId The battle UUID
     * @param attackerUUID The attacking Pokemon's UUID
     * @param defenderUUID The defending Pokemon's UUID
     * @param moveId The move ID
     * @return JSON string with { effectiveness: number, immune: boolean } or { error: string }
     */
    fun getTypeEffectiveness(battleId: UUID, attackerUUID: UUID, defenderUUID: UUID, moveId: String): String {
        return """{"error": "Not implemented"}"""
    }

    /**
     * Estimate damage for a move (deterministic, uses Showdown's calculation)
     * @param battleId The battle UUID
     * @param attackerUUID The attacking Pokemon's UUID
     * @param defenderUUID The defending Pokemon's UUID
     * @param moveId The move ID
     * @return JSON string with damage info or { error: string }
     */
    fun estimateDamage(battleId: UUID, attackerUUID: UUID, defenderUUID: UUID, moveId: String): String {
        return """{"error": "Not implemented"}"""
    }

    /**
     * Get comprehensive battle state snapshot for AI decision-making
     * @param battleId The battle UUID
     * @param sideId The side requesting info ("p1" or "p2")
     * @return JSON string with battle state or { error: string }
     */
    fun getBattleState(battleId: UUID, sideId: String): String {
        return """{"error": "Not implemented"}"""
    }

    /**
     * Check if a Pokemon can use a specific move
     * @param battleId The battle UUID
     * @param pokemonUUID The Pokemon's UUID
     * @param moveId The move ID
     * @return JSON string with { canUse: boolean, reason?: string } or { error: string }
     */
    fun canUseMove(battleId: UUID, pokemonUUID: UUID, moveId: String): String {
        return """{"error": "Not implemented"}"""
    }

    companion object {
        val service: ShowdownService by lazy { GraalShowdownService() }
    }
}