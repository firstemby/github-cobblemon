/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battles.ai

import com.cobblemon.mod.common.api.battles.ai.config.PredictionConfig
import java.util.UUID

/**
 * Tracks opponent information revealed during battle for AI prediction.
 */
class AIBattleMemory(
    val battleId: UUID,
    private val config: PredictionConfig = PredictionConfig.DEFAULT
) {
    private val revealedMoves = mutableMapOf<UUID, MutableSet<String>>()
    private val revealedItems = mutableMapOf<UUID, String?>()
    private val revealedAbilities = mutableMapOf<UUID, String?>()
    private val moveUsageCounts = mutableMapOf<UUID, MutableMap<String, Int>>()
    private val lastUsedMoves = mutableMapOf<UUID, String>()
    private val switchEvents = mutableListOf<SwitchEvent>()
    private var currentTurn = 0

    private val statBoosts = mutableMapOf<UUID, MutableMap<String, Int>>()
    private val sideConditions = mutableMapOf<String, MutableSet<String>>()
    private val turnSwitchedIn = mutableMapOf<UUID, Int>()
    private val volatileStatuses = mutableMapOf<UUID, MutableSet<String>>()
    private val itemConsumed = mutableMapOf<UUID, Boolean>()
    private val gimmickStatus = mutableMapOf<UUID, GimmickStatus>()
    private val protectUsageCount = mutableMapOf<UUID, Int>()
    private var currentWeather: String? = null
    private var currentTerrain: String? = null
    private val statusConditions = mutableMapOf<UUID, String>()

    fun recordMoveUsed(pokemonId: UUID, moveName: String) {
        if (!config.enabled || !config.rememberRevealedMoves) return

        val normalizedMove = moveName.lowercase().replace(" ", "")
        revealedMoves.getOrPut(pokemonId) { mutableSetOf() }.add(normalizedMove)
        moveUsageCounts.getOrPut(pokemonId) { mutableMapOf() }
            .merge(normalizedMove, 1) { old, new -> old + new }
        lastUsedMoves[pokemonId] = normalizedMove
    }

    fun recordItemRevealed(pokemonId: UUID, itemName: String?) {
        if (!config.enabled || !config.rememberRevealedItems) return

        revealedItems[pokemonId] = itemName?.lowercase()?.replace(" ", "")
    }

    fun recordAbilityRevealed(pokemonId: UUID, abilityName: String) {
        if (!config.enabled || !config.rememberRevealedAbilities) return

        revealedAbilities[pokemonId] = abilityName.lowercase().replace(" ", "")
    }

    fun recordSwitch(pokemonOutId: UUID, pokemonInId: UUID, reason: SwitchReason = SwitchReason.VOLUNTARY) {
        if (!config.enabled) return

        switchEvents.add(SwitchEvent(
            turn = currentTurn,
            pokemonOutId = pokemonOutId,
            pokemonInId = pokemonInId,
            reason = reason
        ))
    }

    fun advanceTurn() {
        currentTurn++

        if (config.memoryDecay > 0) {
            // TODO: Memory decay could reduce confidence in old information
        }
    }

    fun getRevealedMoves(pokemonId: UUID): Set<String> {
        return revealedMoves[pokemonId]?.toSet() ?: emptySet()
    }

    fun getLastUsedMove(pokemonId: UUID): String? {
        return lastUsedMoves[pokemonId]
    }

    fun getRevealedItem(pokemonId: UUID): String? {
        return revealedItems[pokemonId]
    }

    fun getRevealedAbility(pokemonId: UUID): String? {
        return revealedAbilities[pokemonId]
    }

    fun getMoveUsageCount(pokemonId: UUID, moveName: String): Int {
        val normalizedMove = moveName.lowercase().replace(" ", "")
        return moveUsageCounts[pokemonId]?.get(normalizedMove) ?: 0
    }

    fun hasRevealedAbility(pokemonId: UUID, abilityName: String): Boolean {
        val normalizedAbility = abilityName.lowercase().replace(" ", "")
        return revealedAbilities[pokemonId] == normalizedAbility
    }

    fun getTotalSwitchCount(): Int = switchEvents.size

    fun getSwitchEvents(): List<SwitchEvent> = switchEvents.toList()

    fun clear() {
        revealedMoves.clear()
        revealedItems.clear()
        revealedAbilities.clear()
        moveUsageCounts.clear()
        lastUsedMoves.clear()
        switchEvents.clear()
        currentTurn = 0
        statBoosts.clear()
        sideConditions.clear()
        turnSwitchedIn.clear()
        volatileStatuses.clear()
        itemConsumed.clear()
        gimmickStatus.clear()
        protectUsageCount.clear()
        currentWeather = null
        currentTerrain = null
        statusConditions.clear()
    }

    fun recordStatChange(pokemonId: UUID, stat: String, stages: Int) {
        if (!config.enabled) return
        val normalizedStat = stat.lowercase()
        val boosts = statBoosts.getOrPut(pokemonId) { mutableMapOf() }
        val currentStages = boosts[normalizedStat] ?: 0
        boosts[normalizedStat] = (currentStages + stages).coerceIn(-6, 6)
    }

    fun clearStatBoosts(pokemonId: UUID) {
        statBoosts.remove(pokemonId)
    }

    fun getStatBoost(pokemonId: UUID, stat: String): Int {
        return statBoosts[pokemonId]?.get(stat.lowercase()) ?: 0
    }

    fun getAllStatBoosts(pokemonId: UUID): Map<String, Int> {
        return statBoosts[pokemonId]?.toMap() ?: emptyMap()
    }

    fun clearAllStatBoosts() {
        if (!config.enabled) return
        statBoosts.clear()
    }

    fun clearNegativeBoosts(pokemonId: UUID) {
        if (!config.enabled) return
        statBoosts[pokemonId]?.entries?.removeIf { it.value < 0 }
    }

    fun setStatBoost(pokemonId: UUID, stat: String, amount: Int) {
        if (!config.enabled) return
        val normalizedStat = stat.lowercase()
        val boosts = statBoosts.getOrPut(pokemonId) { mutableMapOf() }
        boosts[normalizedStat] = amount.coerceIn(-6, 6)
    }

    fun invertStatBoosts(pokemonId: UUID) {
        if (!config.enabled) return
        statBoosts[pokemonId]?.let { boosts ->
            boosts.keys.forEach { stat ->
                boosts[stat] = -(boosts[stat] ?: 0)
            }
        }
    }

    fun copyStatBoosts(targetId: UUID, sourceId: UUID) {
        if (!config.enabled) return
        val sourceBoosts = statBoosts[sourceId]?.toMutableMap() ?: mutableMapOf()
        statBoosts[targetId] = sourceBoosts
    }

    fun swapStatBoosts(pokemon1Id: UUID, pokemon2Id: UUID) {
        if (!config.enabled) return
        val boosts1 = statBoosts[pokemon1Id]?.toMutableMap()
        val boosts2 = statBoosts[pokemon2Id]?.toMutableMap()

        if (boosts2 != null) {
            statBoosts[pokemon1Id] = boosts2
        } else {
            statBoosts.remove(pokemon1Id)
        }

        if (boosts1 != null) {
            statBoosts[pokemon2Id] = boosts1
        } else {
            statBoosts.remove(pokemon2Id)
        }
    }

    fun recordSideConditionStart(sideId: String, condition: String) {
        if (!config.enabled) return
        sideConditions.getOrPut(sideId) { mutableSetOf() }.add(condition.lowercase())
    }

    fun recordSideConditionEnd(sideId: String, condition: String) {
        if (!config.enabled) return
        sideConditions[sideId]?.remove(condition.lowercase())
    }

    fun hasSideCondition(sideId: String, condition: String): Boolean {
        return sideConditions[sideId]?.contains(condition.lowercase()) ?: false
    }

    fun getSideConditions(sideId: String): Set<String> {
        return sideConditions[sideId]?.toSet() ?: emptySet()
    }

    fun recordPokemonSwitchedIn(pokemonId: UUID) {
        if (!config.enabled) return
        turnSwitchedIn[pokemonId] = currentTurn
        volatileStatuses.remove(pokemonId)
        protectUsageCount.remove(pokemonId)
    }

    fun getTurnsOnField(pokemonId: UUID): Int {
        val switchInTurn = turnSwitchedIn[pokemonId] ?: return 1
        return (currentTurn - switchInTurn + 1).coerceAtLeast(1)
    }

    fun recordVolatileStatus(pokemonId: UUID, status: String) {
        if (!config.enabled) return
        volatileStatuses.getOrPut(pokemonId) { mutableSetOf() }.add(status.lowercase())
    }

    fun removeVolatileStatus(pokemonId: UUID, status: String) {
        if (!config.enabled) return
        volatileStatuses[pokemonId]?.remove(status.lowercase())
    }

    fun hasVolatileStatus(pokemonId: UUID, status: String): Boolean {
        return volatileStatuses[pokemonId]?.contains(status.lowercase()) ?: false
    }

    fun isTrapped(pokemonId: UUID): Boolean {
        val statuses = volatileStatuses[pokemonId] ?: return false
        val trappingStatuses = setOf(
            "trapped", "partiallytrapped", "bind", "wrap", "firespin", "whirlpool",
            "sandtomb", "magmastorm", "infestation", "clamped", "shadowhold",
            "meanlook", "block", "spiderweb", "jawlock", "octolock", "noescape"
        )
        return statuses.any { it in trappingStatuses }
    }

    fun getVolatileStatuses(pokemonId: UUID): Set<String> {
        return volatileStatuses[pokemonId]?.toSet() ?: emptySet()
    }

    fun recordItemConsumed(pokemonId: UUID) {
        if (!config.enabled) return
        itemConsumed[pokemonId] = true
        revealedItems[pokemonId] = null
    }

    fun hasItemBeenConsumed(pokemonId: UUID): Boolean {
        return itemConsumed[pokemonId] ?: false
    }

    fun recordTerastallized(pokemonId: UUID, teraType: String) {
        if (!config.enabled) return
        val status = gimmickStatus.getOrPut(pokemonId) { GimmickStatus() }
        gimmickStatus[pokemonId] = status.copy(isTerastallized = true, teraType = teraType.lowercase())
    }

    fun recordMegaEvolved(pokemonId: UUID) {
        if (!config.enabled) return
        val status = gimmickStatus.getOrPut(pokemonId) { GimmickStatus() }
        gimmickStatus[pokemonId] = status.copy(isMegaEvolved = true)
    }

    fun recordDynamaxed(pokemonId: UUID) {
        if (!config.enabled) return
        val status = gimmickStatus.getOrPut(pokemonId) { GimmickStatus() }
        gimmickStatus[pokemonId] = status.copy(isDynamaxed = true)
    }

    fun recordDynamaxEnded(pokemonId: UUID) {
        if (!config.enabled) return
        gimmickStatus[pokemonId]?.let {
            gimmickStatus[pokemonId] = it.copy(isDynamaxed = false)
        }
    }

    fun getGimmickStatus(pokemonId: UUID): GimmickStatus {
        return gimmickStatus[pokemonId] ?: GimmickStatus()
    }

    fun isDynamaxed(pokemonId: UUID): Boolean {
        return gimmickStatus[pokemonId]?.isDynamaxed ?: false
    }

    fun isMegaEvolved(pokemonId: UUID): Boolean {
        return gimmickStatus[pokemonId]?.isMegaEvolved ?: false
    }

    fun isTerastallized(pokemonId: UUID): Boolean {
        return gimmickStatus[pokemonId]?.isTerastallized ?: false
    }

    fun getTeraType(pokemonId: UUID): String? {
        return gimmickStatus[pokemonId]?.teraType
    }

    fun recordProtectUsed(pokemonId: UUID) {
        if (!config.enabled) return
        protectUsageCount[pokemonId] = (protectUsageCount[pokemonId] ?: 0) + 1
    }

    fun resetProtectCount(pokemonId: UUID) {
        protectUsageCount[pokemonId] = 0
    }

    fun getProtectCount(pokemonId: UUID): Int {
        return protectUsageCount[pokemonId] ?: 0
    }

    fun getCurrentTurn(): Int = currentTurn

    fun recordWeatherStart(weather: String) {
        if (!config.enabled) return
        currentWeather = weather.lowercase().replace(" ", "")
    }

    fun recordWeatherEnd() {
        if (!config.enabled) return
        currentWeather = null
    }

    fun getCurrentWeather(): String? = currentWeather

    fun recordTerrainStart(terrain: String) {
        if (!config.enabled) return
        currentTerrain = terrain.lowercase().replace(" ", "")
    }

    fun recordTerrainEnd() {
        if (!config.enabled) return
        currentTerrain = null
    }

    fun getCurrentTerrain(): String? = currentTerrain

    fun recordStatusInflicted(pokemonId: UUID, status: String) {
        if (!config.enabled) return
        statusConditions[pokemonId] = status.lowercase().replace(" ", "")
    }

    fun recordStatusCured(pokemonId: UUID) {
        if (!config.enabled) return
        statusConditions.remove(pokemonId)
    }

    fun getStatusCondition(pokemonId: UUID): String? = statusConditions[pokemonId]

    fun hasStatusCondition(pokemonId: UUID): Boolean = pokemonId in statusConditions
}

/**
 * Tracks gimmick status for a Pokemon (Tera, Mega, Dynamax).
 */
data class GimmickStatus(
    val isTerastallized: Boolean = false,
    val teraType: String? = null,
    val isMegaEvolved: Boolean = false,
    val isDynamaxed: Boolean = false
)
