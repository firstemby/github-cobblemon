/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battles.interpreter.instructions

import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.battles.dispatch.InterpreterInstruction

/**
 * Tails every `update` output from Showdown. Used for picking up any forced switches that need to be done.
 * Marks every actor that must switch before prompting any of them, since AI actors can respond synchronously.
 *
 * @author Hiroku
 * @since April 13th, 2026
 */
object PostUpdateInstruction : InterpreterInstruction {
    override fun invoke(battle: PokemonBattle) {
        battle.dispatchGo {
            val actors = battle.actors
            actors.forEach { actor ->
                if (actor.request?.forceSwitch?.any { it } == true) {
                    actor.mustChoose = true
                }
            }
            actors.forEach { it.postUpdate() }
        }
    }
}
