/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.battles.ai.config

data class BattleBagItem(
    val itemId: String,
    val quantity: Int = 1,
    val category: BagItemCategory = BagItemCategory.OTHER,
    val baseScore: Double = 1.0
) {
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
