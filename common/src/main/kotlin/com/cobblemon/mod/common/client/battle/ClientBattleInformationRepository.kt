/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.battle

import com.cobblemon.mod.common.net.messages.client.battle.BattleInformationDTO
import com.cobblemon.mod.common.net.messages.client.battle.BattlePokemonDTO
import java.time.Instant
import java.util.UUID

object ClientBattleInformationRepository {
    val actors = mutableMapOf<UUID, MutableList<BattlePokemonDTO>>()
    val battles = mutableMapOf<UUID, BattleInformationDTO>()
    val actorActualSides = mutableMapOf<UUID, Int>()
    var mustChooseBy: Instant? = null
}