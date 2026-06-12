/*
 * Copyright (C) 2023 Cobblemon Contributors
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
 * Format: |-waiting|SOURCE|TARGET
 * The SOURCE Pokémon has used a move and is waiting for the TARGET Pokémon (e.g. Fire Pledge).
 * @author Crater
 * @since April 17th, 2026
 */
class WaitingInstruction(val message: BattleMessage): InterpreterInstruction {
    override fun invoke(battle: PokemonBattle) {
        battle.dispatchWaiting(1.5F) {
            val pokemon = message.battlePokemon(0, battle) ?: return@dispatchWaiting
            val pokemonName = pokemon.getName()
            val targetPokemon = message.battlePokemon(1, battle) ?: return@dispatchWaiting
            val targetPokemonName = targetPokemon.getName()
            val lang = battleLang("waiting", pokemonName, targetPokemonName).yellow()
            battle.broadcastChatMessage(lang)
            battle.minorBattleActions[pokemon.uuid] = message
        }
    }
}
