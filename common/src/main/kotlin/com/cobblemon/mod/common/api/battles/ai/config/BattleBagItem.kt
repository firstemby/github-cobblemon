/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.battles.ai.config

/**
 * Represents an item available in a battle bag.
 *
 * @property itemId The resource location of the item (e.g., "cobblemon:potion").
 * @property quantity How many of this item are available per battle.
 * @property category The category this item belongs to for rule filtering.
 * @property baseScore Base score for using this item (higher = more likely to use).
 *
 * @author Amo Aster
 * @since December 2025
 */
data class BattleBagItem(
    val itemId: String,
    val quantity: Int = 1,
    val category: BagItemCategory = BagItemCategory.OTHER,
    val baseScore: Double = 1.0
) {
    /**
     * Checks if this item matches the given filter.
     *
     * @param filter The filter to check against, or null to match all items.
     * @return True if this item matches the filter criteria.
     */
    fun matches(filter: ItemFilter?): Boolean {
        if (filter == null) return true

        if (filter.categories != null && category !in filter.categories) {
            return false
        }

        if (filter.items != null && itemId !in filter.items) {
            return false
        }

        return true
    }
}
