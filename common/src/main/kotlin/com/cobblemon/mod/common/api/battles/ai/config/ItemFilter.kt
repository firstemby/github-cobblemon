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
 * Filter for selecting which items a usage rule applies to.
 *
 * @property categories Filter by item categories. If set, item must have one of these categories.
 * @property items Filter by specific item IDs. If set, item must be in this list.
 * @property condition Custom Molang condition for additional filtering.
 *
 * @author Amo Aster
 * @since December 2025
 */
data class ItemFilter(
    val categories: List<BagItemCategory>? = null,
    val items: List<String>? = null,
    val condition: ExpressionLike? = null
)
