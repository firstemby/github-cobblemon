/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.battles.ai.config

import com.cobblemon.mod.common.api.molang.ExpressionLike

/** A rule that determines when and how to use items from a battle bag. */
data class ItemUsageRule(
    val id: String? = null,
    val condition: ExpressionLike,
    val itemFilter: ItemFilter? = null,
    val priority: Int = 0,
    val biasMultiplier: Double = 1.0,
    val scoreBonus: Double = 0.0,
    val targetCondition: ExpressionLike? = null
)
