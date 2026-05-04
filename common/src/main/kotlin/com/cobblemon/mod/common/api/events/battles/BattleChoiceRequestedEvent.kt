/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.events.battles

import com.cobblemon.mod.common.net.messages.client.battle.BattleMakeChoicePacket
import net.minecraft.server.level.ServerPlayer

data class BattleChoiceRequestedEvent (
    val player: ServerPlayer,
    val packet: BattleMakeChoicePacket
)