/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.battles.ai.config.effects

/**
 * Restriction effects for held items.
 *
 * @property locksMove Whether the item locks the user to one move (Choice items).
 * @property blocksStatusMoves Whether the item prevents using status moves (Assault Vest).
 * @property allowedMoveTypes List of move types that are allowed (null = all allowed).
 *
 * @author Amo Aster
 * @since December 2025
 */
data class RestrictionEffect(
    val locksMove: Boolean = false,
    val blocksStatusMoves: Boolean = false,
    val allowedMoveTypes: List<String>? = null
) {
    companion object {
        val NONE = RestrictionEffect()
    }
}
