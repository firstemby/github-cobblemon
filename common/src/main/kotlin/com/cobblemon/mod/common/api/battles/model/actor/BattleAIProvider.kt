/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.battles.model.actor

/**
 * Provides methods for allowing non-player controlled battle actors to decide how they will move in a Pokemon battle
 */
abstract class BattleAIProvider {
    abstract fun getMoveForNextTurn()
}