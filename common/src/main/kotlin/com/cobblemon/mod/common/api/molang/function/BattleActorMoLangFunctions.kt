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
        val functions = hashMapOf<String, (MoParams) -> Any>()

        functions["is_npc"] = { DoubleValue(actor.type == ActorType.NPC) }
        functions["is_player"] = { DoubleValue(actor.type == ActorType.PLAYER) }
        functions["is_wild"] = { DoubleValue(actor.type == ActorType.WILD) }

        when (actor) {
            is NPCBattleActor -> {
                functions["npc"] = { actor.entity.struct }
            }

            is PlayerBattleActor -> {
                functions["player"] = { actor.entity?.asMoLangValue() ?: DoubleValue.ZERO }
            }

            is PokemonBattleActor -> {
                functions["pokemon"] = { actor.entity?.asMoLangValue() ?: DoubleValue.ZERO }
            }
        }

        return functions
    }
}
