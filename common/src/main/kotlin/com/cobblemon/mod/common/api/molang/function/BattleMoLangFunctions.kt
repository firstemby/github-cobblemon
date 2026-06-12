/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.molang.function

import com.bedrockk.molang.runtime.MoParams
import com.bedrockk.molang.runtime.value.DoubleValue
import com.bedrockk.molang.runtime.value.StringValue
import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.util.asArrayValue
import java.util.*

object BattleMoLangFunctions : AbstractMoLangFunctionHolder<PokemonBattle>() {
    override fun PokemonBattle.moLangFunctions(): MutableMap<String, (MoParams) -> Any> {
        val battle = this
        return mutableMapOf(
            "environment" to { battle.runtime.environment },
            "battle_id" to { StringValue(battle.battleId.toString()) },
            "battle_type" to { StringValue(battle.format.toString()) },
            "is_pvn" to { DoubleValue(battle.isPvN) },
            "is_pvp" to { DoubleValue(battle.isPvP) },
            "is_pvw" to { DoubleValue(battle.isPvW) },
            "stop" to { battle.stop() },
            "actors" to { battle.actors.toList().asArrayValue { it.struct } },
            "get_actor" to getActor@{ params ->
                val uuid = UUID.fromString(params.getString(0))
                val actor = battle.actors.find { it.uuid == uuid } ?: return@getActor DoubleValue.ZERO
                actor.struct
            }
        )
    }
}
