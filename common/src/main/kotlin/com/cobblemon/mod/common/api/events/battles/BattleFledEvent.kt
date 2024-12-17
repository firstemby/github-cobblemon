/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.events.battles

import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor

/**
 * Event fired when a [PokemonBattle] is fled by a [PlayerBattleActor].
 *
 * @author Segfault Guy
 * @since March 25th 2023
 */
data class BattleFledEvent (
    override val battle: PokemonBattle,
    val player: PlayerBattleActor
) : BattleEvent