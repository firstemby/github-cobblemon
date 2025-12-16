/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.battles.ai.config

import com.cobblemon.mod.common.util.asExpressionLike

/**
 * Configuration for a battle bag that NPCs can use during battles.
 * Defines what items are available and how/when the AI should use them.
 *
 * @property id Unique identifier for this bag configuration.
 * @property enabled Whether the AI is allowed to use items at all.
 * @property maxItemsPerBattle Maximum number of items that can be used per battle.
 * @property items Available items in this bag with their quantities.
 * @property usageRules Rules that determine when to use items.
 *
 * @author Amo Aster
 * @since December 2025
 */
data class BattleBagConfig(
    val id: String = "default",
    val enabled: Boolean = true,
    val maxItemsPerBattle: Int = 4,
    val items: List<BattleBagItem> = emptyList(),
    val usageRules: List<ItemUsageRule> = emptyList()
) {
    companion object {
        val EMPTY = BattleBagConfig(enabled = false)

        val DEFAULT = BattleBagConfig(
            id = "default",
            enabled = true,
            maxItemsPerBattle = 4,
            items = listOf(
                BattleBagItem(
                    itemId = "cobblemon:potion",
                    quantity = 2,
                    category = BagItemCategory.HEALING
                ),
                BattleBagItem(
                    itemId = "cobblemon:super_potion",
                    quantity = 2,
                    category = BagItemCategory.HEALING
                )
            ),
            usageRules = listOf(
                ItemUsageRule(
                    id = "heal_low_hp",
                    condition = "q.pokemon.current_hp_percent < 0.3".asExpressionLike(),
                    itemFilter = ItemFilter(categories = listOf(BagItemCategory.HEALING)),
                    priority = 10,
                    biasMultiplier = 2.0
                )
            )
        )
    }
}
