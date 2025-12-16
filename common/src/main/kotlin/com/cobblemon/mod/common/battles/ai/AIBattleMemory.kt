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
 * Tracks information about opponents revealed during battle for AI prediction.
 * This memory persists throughout a single battle and is used by the AI to make
 * more informed decisions based on what the opponent has revealed.
 *
 * @property battleId The unique ID of the battle this memory is associated with
 * @property config The prediction configuration controlling what is remembered
 *
 * @author Amo Aster
 * @since December 2025
 */
class AIBattleMemory(
    val battleId: UUID,
    private val config: PredictionConfig = PredictionConfig.DEFAULT
) {
    /** Maps Pokemon UUID to their revealed moves */
    private val revealedMoves = mutableMapOf<UUID, MutableSet<String>>()

    /** Maps Pokemon UUID to their revealed item (null if unknown or consumed) */
    private val revealedItems = mutableMapOf<UUID, String?>()

    /** Maps Pokemon UUID to their revealed ability */
    private val revealedAbilities = mutableMapOf<UUID, String?>()

    /** Maps Pokemon UUID to move usage counts */
    private val moveUsageCounts = mutableMapOf<UUID, MutableMap<String, Int>>()

    /** Maps Pokemon UUID to their last used move (for prediction) */
    private val lastUsedMoves = mutableMapOf<UUID, String>()

    /** Tracks switch patterns - when and why switches occurred */
    private val switchEvents = mutableListOf<SwitchEvent>()

    /** Turn counter for memory decay */
    private var currentTurn = 0

    // ===== NEW TRACKING FIELDS =====

    /** Maps Pokemon UUID to their current stat boosts (stat name -> stages) */
    private val statBoosts = mutableMapOf<UUID, MutableMap<String, Int>>()

    /** Maps side identifier ("side1" or "side2") to active side conditions */
    private val sideConditions = mutableMapOf<String, MutableSet<String>>()

    /** Maps Pokemon UUID to the turn they switched in */
    private val turnSwitchedIn = mutableMapOf<UUID, Int>()

    /** Maps Pokemon UUID to their volatile statuses (trapped, confused, etc.) */
    private val volatileStatuses = mutableMapOf<UUID, MutableSet<String>>()

    /** Maps Pokemon UUID to whether their item has been consumed */
    private val itemConsumed = mutableMapOf<UUID, Boolean>()

    /** Maps Pokemon UUID to their gimmick status */
    private val gimmickStatus = mutableMapOf<UUID, GimmickStatus>()

    /** Tracks which Pokemon have used Protect-like moves recently (for stale detection) */
    private val protectUsageCount = mutableMapOf<UUID, Int>()

    /**
     * Records that a Pokemon used a specific move.
     *
     * @param pokemonId The UUID of the Pokemon
     * @param moveName The name of the move used
     */
    fun recordMoveUsed(pokemonId: UUID, moveName: String) {
        if (!config.enabled || !config.rememberRevealedMoves) return

        val normalizedMove = moveName.lowercase().replace(" ", "")
        revealedMoves.getOrPut(pokemonId) { mutableSetOf() }.add(normalizedMove)
        moveUsageCounts.getOrPut(pokemonId) { mutableMapOf() }
            .merge(normalizedMove, 1) { old, new -> old + new }
        lastUsedMoves[pokemonId] = normalizedMove
    }

    /**
     * Records that a Pokemon's item was revealed.
     *
     * @param pokemonId The UUID of the Pokemon
     * @param itemName The name of the item, or null if consumed/none
     */
    fun recordItemRevealed(pokemonId: UUID, itemName: String?) {
        if (!config.enabled || !config.rememberRevealedItems) return

        revealedItems[pokemonId] = itemName?.lowercase()?.replace(" ", "")
    }

    /**
     * Records that a Pokemon's ability was revealed.
     *
     * @param pokemonId The UUID of the Pokemon
     * @param abilityName The name of the ability
     */
    fun recordAbilityRevealed(pokemonId: UUID, abilityName: String) {
        if (!config.enabled || !config.rememberRevealedAbilities) return

        revealedAbilities[pokemonId] = abilityName.lowercase().replace(" ", "")
    }

    /**
     * Records a switch event for pattern analysis.
     *
     * @param pokemonOutId The UUID of the Pokemon being switched out
     * @param pokemonInId The UUID of the Pokemon being switched in
     * @param reason Optional reason for the switch
     */
    fun recordSwitch(pokemonOutId: UUID, pokemonInId: UUID, reason: SwitchReason = SwitchReason.VOLUNTARY) {
        if (!config.enabled) return

        switchEvents.add(SwitchEvent(
            turn = currentTurn,
            pokemonOutId = pokemonOutId,
            pokemonInId = pokemonInId,
            reason = reason
        ))
    }

    /**
     * Advances to the next turn, applying memory decay if configured.
     */
    fun advanceTurn() {
        currentTurn++

        // Apply memory decay if configured
        if (config.memoryDecay > 0) {
            // For now, we don't actually remove memories, but this could
            // be used to reduce confidence in old information
        }
    }

    /**
     * Gets all moves revealed by a specific Pokemon.
     *
     * @param pokemonId The UUID of the Pokemon
     * @return Set of revealed move names
     */
    fun getRevealedMoves(pokemonId: UUID): Set<String> {
        return revealedMoves[pokemonId]?.toSet() ?: emptySet()
    }

    /**
     * Gets the last move used by a specific Pokemon.
     *
     * @param pokemonId The UUID of the Pokemon
     * @return The last move used, or null if unknown
     */
    fun getLastUsedMove(pokemonId: UUID): String? {
        return lastUsedMoves[pokemonId]
    }

    /**
     * Gets the revealed item for a Pokemon.
     *
     * @param pokemonId The UUID of the Pokemon
     * @return The item name, or null if unknown
     */
    fun getRevealedItem(pokemonId: UUID): String? {
        return revealedItems[pokemonId]
    }

    /**
     * Gets the revealed ability for a Pokemon.
     *
     * @param pokemonId The UUID of the Pokemon
     * @return The ability name, or null if unknown
     */
    fun getRevealedAbility(pokemonId: UUID): String? {
        return revealedAbilities[pokemonId]
    }

    /**
     * Gets how many times a Pokemon has used a specific move.
     *
     * @param pokemonId The UUID of the Pokemon
     * @param moveName The name of the move
     * @return The usage count
     */
    fun getMoveUsageCount(pokemonId: UUID, moveName: String): Int {
        val normalizedMove = moveName.lowercase().replace(" ", "")
        return moveUsageCounts[pokemonId]?.get(normalizedMove) ?: 0
    }

    /**
     * Checks if a Pokemon has been observed to have a specific ability.
     *
     * @param pokemonId The UUID of the Pokemon
     * @param abilityName The ability to check for
     * @return True if this ability has been revealed
     */
    fun hasRevealedAbility(pokemonId: UUID, abilityName: String): Boolean {
        val normalizedAbility = abilityName.lowercase().replace(" ", "")
        return revealedAbilities[pokemonId] == normalizedAbility
    }

    /**
     * Gets the number of times the opponent has switched this battle.
     *
     * @return Total switch count
     */
    fun getTotalSwitchCount(): Int = switchEvents.size

    /**
     * Gets switch events for analysis.
     *
     * @return List of switch events
     */
    fun getSwitchEvents(): List<SwitchEvent> = switchEvents.toList()

    /**
     * Clears all memory (useful for testing or reset scenarios).
     */
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
    }

    // ===== STAT BOOST METHODS =====

    /**
     * Records a stat boost or drop for a Pokemon.
     *
     * @param pokemonId The UUID of the Pokemon
     * @param stat The stat being changed (atk, def, spa, spd, spe, accuracy, evasion)
     * @param stages The number of stages changed (positive for boost, negative for drop)
     */
    fun recordStatChange(pokemonId: UUID, stat: String, stages: Int) {
        if (!config.enabled) return
        val normalizedStat = stat.lowercase()
        val boosts = statBoosts.getOrPut(pokemonId) { mutableMapOf() }
        val currentStages = boosts[normalizedStat] ?: 0
        // Clamp to -6 to +6
        boosts[normalizedStat] = (currentStages + stages).coerceIn(-6, 6)
    }

    /**
     * Clears all stat boosts for a Pokemon (e.g., when they switch out or Haze is used).
     *
     * @param pokemonId The UUID of the Pokemon
     */
    fun clearStatBoosts(pokemonId: UUID) {
        statBoosts.remove(pokemonId)
    }

    /**
     * Gets the current stat boost stages for a Pokemon.
     *
     * @param pokemonId The UUID of the Pokemon
     * @param stat The stat to check
     * @return The number of stages (-6 to +6), or 0 if unknown
     */
    fun getStatBoost(pokemonId: UUID, stat: String): Int {
        return statBoosts[pokemonId]?.get(stat.lowercase()) ?: 0
    }

    /**
     * Gets all stat boosts for a Pokemon.
     *
     * @param pokemonId The UUID of the Pokemon
     * @return Map of stat name to stages
     */
    fun getAllStatBoosts(pokemonId: UUID): Map<String, Int> {
        return statBoosts[pokemonId]?.toMap() ?: emptyMap()
    }

    /**
     * Clears all stat boosts for all Pokemon (e.g., Haze).
     */
    fun clearAllStatBoosts() {
        if (!config.enabled) return
        statBoosts.clear()
    }

    /**
     * Clears only negative stat boosts for a Pokemon.
     *
     * @param pokemonId The UUID of the Pokemon
     */
    fun clearNegativeBoosts(pokemonId: UUID) {
        if (!config.enabled) return
        statBoosts[pokemonId]?.entries?.removeIf { it.value < 0 }
    }

    /**
     * Sets a specific stat boost to an exact value (for SetBoost instruction).
     *
     * @param pokemonId The UUID of the Pokemon
     * @param stat The stat being set
     * @param amount The exact stage value to set (-6 to +6)
     */
    fun setStatBoost(pokemonId: UUID, stat: String, amount: Int) {
        if (!config.enabled) return
        val normalizedStat = stat.lowercase()
        val boosts = statBoosts.getOrPut(pokemonId) { mutableMapOf() }
        boosts[normalizedStat] = amount.coerceIn(-6, 6)
    }

    /**
     * Inverts all stat boosts for a Pokemon (positive becomes negative and vice versa).
     *
     * @param pokemonId The UUID of the Pokemon
     */
    fun invertStatBoosts(pokemonId: UUID) {
        if (!config.enabled) return
        statBoosts[pokemonId]?.let { boosts ->
            boosts.keys.forEach { stat ->
                boosts[stat] = -(boosts[stat] ?: 0)
            }
        }
    }

    /**
     * Copies stat boosts from one Pokemon to another.
     *
     * @param targetId The UUID of the Pokemon receiving the boosts
     * @param sourceId The UUID of the Pokemon whose boosts are being copied
     */
    fun copyStatBoosts(targetId: UUID, sourceId: UUID) {
        if (!config.enabled) return
        val sourceBoosts = statBoosts[sourceId]?.toMutableMap() ?: mutableMapOf()
        statBoosts[targetId] = sourceBoosts
    }

    /**
     * Swaps stat boosts between two Pokemon.
     *
     * @param pokemon1Id The UUID of the first Pokemon
     * @param pokemon2Id The UUID of the second Pokemon
     */
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

    // ===== SIDE CONDITION METHODS =====

    /**
     * Records a side condition starting (Tailwind, Stealth Rock, etc.).
     *
     * @param sideId The side identifier ("side1" or "side2")
     * @param condition The condition name
     */
    fun recordSideConditionStart(sideId: String, condition: String) {
        if (!config.enabled) return
        sideConditions.getOrPut(sideId) { mutableSetOf() }.add(condition.lowercase())
    }

    /**
     * Records a side condition ending.
     *
     * @param sideId The side identifier ("side1" or "side2")
     * @param condition The condition name
     */
    fun recordSideConditionEnd(sideId: String, condition: String) {
        if (!config.enabled) return
        sideConditions[sideId]?.remove(condition.lowercase())
    }

    /**
     * Checks if a side has a specific condition.
     *
     * @param sideId The side identifier
     * @param condition The condition to check for
     * @return True if the condition is active
     */
    fun hasSideCondition(sideId: String, condition: String): Boolean {
        return sideConditions[sideId]?.contains(condition.lowercase()) ?: false
    }

    /**
     * Gets all active conditions for a side.
     *
     * @param sideId The side identifier
     * @return Set of active condition names
     */
    fun getSideConditions(sideId: String): Set<String> {
        return sideConditions[sideId]?.toSet() ?: emptySet()
    }

    // ===== TURNS ON FIELD METHODS =====

    /**
     * Records when a Pokemon switches in.
     *
     * @param pokemonId The UUID of the Pokemon
     */
    fun recordPokemonSwitchedIn(pokemonId: UUID) {
        if (!config.enabled) return
        turnSwitchedIn[pokemonId] = currentTurn
        // Clear volatile statuses when switching in
        volatileStatuses.remove(pokemonId)
        // Reset protect count on switch
        protectUsageCount.remove(pokemonId)
    }

    /**
     * Gets how many turns a Pokemon has been on the field.
     *
     * @param pokemonId The UUID of the Pokemon
     * @return Number of turns on field (1 = just switched in this turn)
     */
    fun getTurnsOnField(pokemonId: UUID): Int {
        val switchInTurn = turnSwitchedIn[pokemonId] ?: return 1
        return (currentTurn - switchInTurn + 1).coerceAtLeast(1)
    }

    // ===== VOLATILE STATUS METHODS =====

    /**
     * Records a volatile status being applied to a Pokemon.
     *
     * @param pokemonId The UUID of the Pokemon
     * @param status The volatile status (trapped, confused, taunt, etc.)
     */
    fun recordVolatileStatus(pokemonId: UUID, status: String) {
        if (!config.enabled) return
        volatileStatuses.getOrPut(pokemonId) { mutableSetOf() }.add(status.lowercase())
    }

    /**
     * Records a volatile status ending for a Pokemon.
     *
     * @param pokemonId The UUID of the Pokemon
     * @param status The volatile status
     */
    fun removeVolatileStatus(pokemonId: UUID, status: String) {
        if (!config.enabled) return
        volatileStatuses[pokemonId]?.remove(status.lowercase())
    }

    /**
     * Checks if a Pokemon has a specific volatile status.
     *
     * @param pokemonId The UUID of the Pokemon
     * @param status The status to check
     * @return True if the Pokemon has this volatile status
     */
    fun hasVolatileStatus(pokemonId: UUID, status: String): Boolean {
        return volatileStatuses[pokemonId]?.contains(status.lowercase()) ?: false
    }

    /**
     * Checks if a Pokemon is trapped (by various means).
     *
     * @param pokemonId The UUID of the Pokemon
     * @return True if the Pokemon cannot switch
     */
    fun isTrapped(pokemonId: UUID): Boolean {
        val statuses = volatileStatuses[pokemonId] ?: return false
        val trappingStatuses = setOf(
            "trapped", "partiallytrapped", "bind", "wrap", "firespin", "whirlpool",
            "sandtomb", "magmastorm", "infestation", "clamped", "shadowhold",
            "meanlook", "block", "spiderweb", "jawlock", "octolock", "noescape"
        )
        return statuses.any { it in trappingStatuses }
    }

    /**
     * Gets all volatile statuses for a Pokemon.
     *
     * @param pokemonId The UUID of the Pokemon
     * @return Set of volatile status names
     */
    fun getVolatileStatuses(pokemonId: UUID): Set<String> {
        return volatileStatuses[pokemonId]?.toSet() ?: emptySet()
    }

    // ===== ITEM CONSUMPTION METHODS =====

    /**
     * Records that a Pokemon's item was consumed.
     *
     * @param pokemonId The UUID of the Pokemon
     */
    fun recordItemConsumed(pokemonId: UUID) {
        if (!config.enabled) return
        itemConsumed[pokemonId] = true
        // Also clear the revealed item since it's gone
        revealedItems[pokemonId] = null
    }

    /**
     * Checks if a Pokemon's item has been consumed.
     *
     * @param pokemonId The UUID of the Pokemon
     * @return True if the item was consumed
     */
    fun hasItemBeenConsumed(pokemonId: UUID): Boolean {
        return itemConsumed[pokemonId] ?: false
    }

    // ===== GIMMICK METHODS =====

    /**
     * Records that a Pokemon has terastallized.
     *
     * @param pokemonId The UUID of the Pokemon
     * @param teraType The type they terastallized into
     */
    fun recordTerastallized(pokemonId: UUID, teraType: String) {
        if (!config.enabled) return
        val status = gimmickStatus.getOrPut(pokemonId) { GimmickStatus() }
        gimmickStatus[pokemonId] = status.copy(isTerastallized = true, teraType = teraType.lowercase())
    }

    /**
     * Records that a Pokemon has mega evolved.
     *
     * @param pokemonId The UUID of the Pokemon
     */
    fun recordMegaEvolved(pokemonId: UUID) {
        if (!config.enabled) return
        val status = gimmickStatus.getOrPut(pokemonId) { GimmickStatus() }
        gimmickStatus[pokemonId] = status.copy(isMegaEvolved = true)
    }

    /**
     * Records that a Pokemon has dynamaxed.
     *
     * @param pokemonId The UUID of the Pokemon
     */
    fun recordDynamaxed(pokemonId: UUID) {
        if (!config.enabled) return
        val status = gimmickStatus.getOrPut(pokemonId) { GimmickStatus() }
        gimmickStatus[pokemonId] = status.copy(isDynamaxed = true)
    }

    /**
     * Records that a Pokemon's dynamax has ended.
     *
     * @param pokemonId The UUID of the Pokemon
     */
    fun recordDynamaxEnded(pokemonId: UUID) {
        if (!config.enabled) return
        gimmickStatus[pokemonId]?.let {
            gimmickStatus[pokemonId] = it.copy(isDynamaxed = false)
        }
    }

    /**
     * Gets the gimmick status for a Pokemon.
     *
     * @param pokemonId The UUID of the Pokemon
     * @return The gimmick status, or a default empty status
     */
    fun getGimmickStatus(pokemonId: UUID): GimmickStatus {
        return gimmickStatus[pokemonId] ?: GimmickStatus()
    }

    /**
     * Checks if a Pokemon is dynamaxed.
     *
     * @param pokemonId The UUID of the Pokemon
     * @return True if dynamaxed
     */
    fun isDynamaxed(pokemonId: UUID): Boolean {
        return gimmickStatus[pokemonId]?.isDynamaxed ?: false
    }

    /**
     * Checks if a Pokemon is mega evolved.
     *
     * @param pokemonId The UUID of the Pokemon
     * @return True if mega evolved
     */
    fun isMegaEvolved(pokemonId: UUID): Boolean {
        return gimmickStatus[pokemonId]?.isMegaEvolved ?: false
    }

    /**
     * Checks if a Pokemon is terastallized.
     *
     * @param pokemonId The UUID of the Pokemon
     * @return True if terastallized
     */
    fun isTerastallized(pokemonId: UUID): Boolean {
        return gimmickStatus[pokemonId]?.isTerastallized ?: false
    }

    /**
     * Gets the tera type of a terastallized Pokemon.
     *
     * @param pokemonId The UUID of the Pokemon
     * @return The tera type, or null if not terastallized
     */
    fun getTeraType(pokemonId: UUID): String? {
        return gimmickStatus[pokemonId]?.teraType
    }

    // ===== PROTECT TRACKING =====

    /**
     * Records a Protect-like move being used (for stale detection).
     *
     * @param pokemonId The UUID of the Pokemon
     */
    fun recordProtectUsed(pokemonId: UUID) {
        if (!config.enabled) return
        protectUsageCount[pokemonId] = (protectUsageCount[pokemonId] ?: 0) + 1
    }

    /**
     * Resets protect count for a Pokemon (call at start of each turn).
     */
    fun resetProtectCount(pokemonId: UUID) {
        protectUsageCount[pokemonId] = 0
    }

    /**
     * Gets consecutive protect usage count.
     *
     * @param pokemonId The UUID of the Pokemon
     * @return Number of times protect was used consecutively
     */
    fun getProtectCount(pokemonId: UUID): Int {
        return protectUsageCount[pokemonId] ?: 0
    }

    /**
     * Gets the current turn number.
     *
     * @return The current turn
     */
    fun getCurrentTurn(): Int = currentTurn
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
