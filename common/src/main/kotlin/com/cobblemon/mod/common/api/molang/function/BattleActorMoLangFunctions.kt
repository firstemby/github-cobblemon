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


object BattleActorMoLangFunctions : AbstractMoLangFunctionHolder<BattleActor>() {
    override fun BattleActor.moLangFunctions(): MutableMap<String, (MoParams) -> Any> {
        val actor = this
        val map = mutableMapOf<String, (MoParams) -> Any>(
            "is_npc" to { DoubleValue(actor.type == ActorType.NPC) },
            "is_player" to { DoubleValue(actor.type == ActorType.PLAYER) },
            "is_wild" to { DoubleValue(actor.type == ActorType.WILD) }
        )

        when (actor) {
            is NPCBattleActor -> {
                map["npc"] = { actor.entity.struct }
            }

            is PlayerBattleActor -> {
                map["player"] = { actor.entity?.asMoLangValue() ?: DoubleValue.ZERO }
            }

            is PokemonBattleActor -> {
                map["pokemon"] = { actor.entity?.asMoLangValue() ?: DoubleValue.ZERO }
            }
        }

        return map
    }
}
