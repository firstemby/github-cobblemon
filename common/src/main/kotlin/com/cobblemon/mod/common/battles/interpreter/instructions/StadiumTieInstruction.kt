/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battles.interpreter.instructions

import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.battles.ShowdownInterpreter
import com.cobblemon.mod.common.battles.dispatch.InterpreterInstruction
import net.minecraft.network.chat.Component

object StadiumTieInstruction : InterpreterInstruction {
    override fun invoke(battle: PokemonBattle) {
        battle.dispatchGo {
            battle.broadcastChatMessage(Component.translatable("cobblemon.stadium.battle.draw"))
            battle.winners = emptyList()
            battle.losers = emptyList()
            battle.end()
            ShowdownInterpreter.lastCauser.remove(battle.battleId)
        }
    }
}
