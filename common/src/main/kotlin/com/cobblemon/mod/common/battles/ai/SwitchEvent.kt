/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battles.ai

import java.util.UUID

/**
 * Represents a switch event for pattern analysis in battle AI.
 *
 * @property turn The turn number when the switch occurred
 * @property pokemonOutId The UUID of the Pokemon being switched out
 * @property pokemonInId The UUID of the Pokemon being switched in
 * @property reason The reason for the switch
 *
 * @author Amo Aster
 * @since December 2025
 */
data class SwitchEvent(
    val turn: Int,
    val pokemonOutId: UUID,
    val pokemonInId: UUID,
    val reason: SwitchReason
)
