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
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.battles.instruction.FormeChangeEvent
import com.cobblemon.mod.common.battles.ShowdownInterpreter
import com.cobblemon.mod.common.battles.dispatch.InterpreterInstruction
import com.cobblemon.mod.common.pokemon.transformation.form.TemporaryForm
import com.cobblemon.mod.common.util.battleLang

/**
 * Format: |-formechange|POKEMON|SPECIES|HP STATUS or |detailschange|POKEMON|DETAILS|HP STATUS
 *
 * POKEMON has temporarily (-formechange) or permanently (detailschange) changed form to SPECIES/DETAILS.
 * @author Segfault Guy
 * @since December 9th, 2024
 */
class FormeChangeInstruction(val message: BattleMessage): InterpreterInstruction {

    override fun invoke(battle: PokemonBattle) {
        val battlePokemon = message.battlePokemon(0, battle) ?: return
        val details = message.argumentAt(1)?.split(',')?.get(0)?.lowercase() ?: return
        val speciesName = details.substringBefore('-')
        val formId = details.substringAfter('-').ifBlank { speciesName }
        val typeKey = if (message.id == "detailschange") "permanent" else "temporary"

        ShowdownInterpreter.broadcastOptionalAbility(battle, message.effect(), battlePokemon)

        battle.dispatchWaiting {
            battle.minorBattleActions[battlePokemon.uuid] = message

            val currentForm = battlePokemon.effectedPokemon.form
            val form = battlePokemon.effectedPokemon.species.getFormByShowdownId(formId)
            if (form is TemporaryForm)
                form.forceStart(battlePokemon.effectedPokemon)
            else if (currentForm is TemporaryForm)
                battlePokemon.effectedPokemon.formProxy.server().revert()
                // TODO cant play animations like this

            CobblemonEvents.FORME_CHANGE.post(FormeChangeEvent(battle, battlePokemon, formId))
            val pokemonName = battlePokemon.getName()
            val lang = when(formId) {
                "busted", "hero", "complete" -> return@dispatchWaiting
                "school", "wishiwashi", "meteor", "minior" -> battleLang("formechange.$formId", pokemonName)
                speciesName -> battleLang("formechange.default.temporary.end", pokemonName)
                else -> battleLang("formechange.default.$typeKey", pokemonName, formId)
            }
            battle.broadcastChatMessage(lang)
        }
    }
}