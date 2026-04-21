/*
 * Copyright (C) 2026 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battles.interpreter.instructions

import com.cobblemon.mod.common.api.battles.interpreter.BattleMessage
import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.api.text.yellow
import com.cobblemon.mod.common.battles.dispatch.InterpreterInstruction
import com.cobblemon.mod.common.util.battleLang

/**
 * Format: |-combine
 * Announces that a move has been combined with another.
 * Currently only used for Pledge moves, but exists for compliance with Showdown sim protocol.
 * @author Crater
 * @since April 17th, 2026
 */
class CombineInstruction(val message: BattleMessage) : InterpreterInstruction {
    override fun invoke(battle: PokemonBattle) {
        battle.dispatchWaiting(1.5F) {
            battle.broadcastChatMessage(battleLang("combine").yellow())
        }
    }
}
