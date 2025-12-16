/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.battles.ai.config

import com.cobblemon.mod.common.api.molang.ExpressionLike

/**
 * A rule that determines when and how to use items from a battle bag.
 *
 * @property id Unique identifier for this rule.
 * @property condition Molang condition that must be true for this rule to apply.
 * @property itemFilter Filter for which items this rule applies to.
 * @property priority Priority of this rule (higher = evaluated first).
 * @property biasMultiplier Multiplier applied to item score when this rule matches.
 * @property scoreBonus Flat score added when this rule matches.
 * @property targetCondition Condition for selecting which Pokémon to use the item on.
 *
 * @author Amo Aster
 * @since December 2025
 */
data class ItemUsageRule(
    val id: String? = null,
    val condition: ExpressionLike,
    val itemFilter: ItemFilter? = null,
    val priority: Int = 0,
    val biasMultiplier: Double = 1.0,
    val scoreBonus: Double = 0.0,
    val targetCondition: ExpressionLike? = null
)
