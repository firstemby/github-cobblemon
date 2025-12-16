/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.battles.ai.config

/**
 * Runtime state for tracking battle bag item usage during a battle.
 *
 * @author Amo Aster
 * @since December 2025
 */
class BattleBagState(private val config: BattleBagConfig) {
    private val itemsUsed = mutableMapOf<String, Int>()
    private var totalItemsUsed = 0

    fun canUseItem(itemId: String): Boolean {
        if (!config.enabled) return false
        if (totalItemsUsed >= config.maxItemsPerBattle) return false

        val bagItem = config.items.find { it.itemId == itemId } ?: return false
        val used = itemsUsed[itemId] ?: 0
        return used < bagItem.quantity
    }

    fun useItem(itemId: String) {
        itemsUsed[itemId] = (itemsUsed[itemId] ?: 0) + 1
        totalItemsUsed++
    }

    fun getRemainingQuantity(itemId: String): Int {
        val bagItem = config.items.find { it.itemId == itemId } ?: return 0
        val used = itemsUsed[itemId] ?: 0
        return (bagItem.quantity - used).coerceAtLeast(0)
    }

    fun getTotalRemainingUses(): Int {
        return (config.maxItemsPerBattle - totalItemsUsed).coerceAtLeast(0)
    }

    fun getAvailableItems(): List<Pair<BattleBagItem, Int>> {
        return config.items.mapNotNull { item ->
            val remaining = getRemainingQuantity(item.itemId)
            if (remaining > 0) item to remaining else null
        }
    }

    /**
     * Gets available items that match the given filter.
     */
    fun getAvailableItems(filter: ItemFilter?): List<Pair<BattleBagItem, Int>> {
        return getAvailableItems().filter { (item, _) -> item.matches(filter) }
    }
}
