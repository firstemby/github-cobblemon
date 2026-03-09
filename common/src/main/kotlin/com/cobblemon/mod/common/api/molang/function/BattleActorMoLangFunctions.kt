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
import com.cobblemon.mod.common.api.battles.model.actor.ActorType
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMoLangValue
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import com.cobblemon.mod.common.battles.actor.PokemonBattleActor
import com.cobblemon.mod.common.entity.npc.NPCBattleActor

object BattleActorMoLangFunctions {
    val holder: List<(BattleActor) -> HashMap<String, (MoParams) -> Any>> = mutableListOf(
        { battleActor ->
            val map = hashMapOf<String, (MoParams) -> Any>()
            map["is_npc"] = { DoubleValue(battleActor.type == ActorType.NPC) }
            map["is_player"] = { DoubleValue(battleActor.type == ActorType.PLAYER) }
            map["is_wild"] = { DoubleValue(battleActor.type == ActorType.WILD) }
            when (battleActor) {
                is NPCBattleActor -> {
                    map["npc"] = { battleActor.entity.struct }
                }

                is PlayerBattleActor -> {
                    map["player"] = { battleActor.entity?.asMoLangValue() ?: DoubleValue.ZERO }
                }

                is PokemonBattleActor -> {
                    map["pokemon"] = { battleActor.entity?.asMoLangValue() ?: DoubleValue.ZERO }
                }
            }
            return@mutableListOf map
        }
    )
}
