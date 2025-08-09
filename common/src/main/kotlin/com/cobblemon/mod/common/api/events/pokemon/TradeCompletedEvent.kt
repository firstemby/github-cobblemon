/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.events.pokemon

import com.bedrockk.molang.runtime.value.DoubleValue
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMoLangValue
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.trade.TradeParticipant
import com.cobblemon.mod.common.util.getPlayer

class TradeCompletedEvent(val tradeParticipant1 : TradeParticipant, val tradeParticipant1Pokemon : Pokemon, val tradeParticipant2 : TradeParticipant, val tradeParticipant2Pokemon : Pokemon) {
    val context = mutableMapOf(
        "trade_participant_1" to (tradeParticipant1.uuid.getPlayer()?.asMoLangValue() ?: DoubleValue.ZERO),
        "trade_participant_2" to (tradeParticipant2.uuid.getPlayer()?.asMoLangValue() ?: DoubleValue.ZERO),
        "trade_participant_1_pokemon" to tradeParticipant1Pokemon.struct,
        "trade_participant_2_pokemon" to tradeParticipant2Pokemon.struct
    )
}