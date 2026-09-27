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
        val functions = hashMapOf<String, (MoParams) -> Any>()

        functions["environment"] = { battle.runtime.environment }
        functions["battle_id"] = { StringValue(battle.battleId.toString()) }
        functions["battle_type"] = { StringValue(battle.format.toString()) }
        functions["is_pvn"] = { DoubleValue(battle.isPvN) }
        functions["is_pvp"] = { DoubleValue(battle.isPvP) }
        functions["is_pvw"] = { DoubleValue(battle.isPvW) }
        functions["stop"] = { battle.stop() }
        functions["actors"] = { battle.actors.toList().asArrayValue { it.struct } }
        functions["get_actor"] = getActor@{ params ->
            val uuid = UUID.fromString(params.getString(0))
            val actor = battle.actors.find { it.uuid == uuid } ?: return@getActor DoubleValue.ZERO
            actor.struct
        }

        return functions
    }
}
