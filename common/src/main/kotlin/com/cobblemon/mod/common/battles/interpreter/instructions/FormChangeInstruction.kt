/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battles.interpreter.instructions

import com.bedrockk.molang.runtime.MoLangRuntime
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.battles.interpreter.BattleMessage
import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.battles.instruction.FormeChangeEvent
import com.cobblemon.mod.common.api.moves.animations.ActionEffectContext
import com.cobblemon.mod.common.api.moves.animations.ActionEffects
import com.cobblemon.mod.common.api.moves.animations.UsersProvider
import com.cobblemon.mod.common.api.pokemon.status.Statuses
import com.cobblemon.mod.common.battles.ShowdownInterpreter
import com.cobblemon.mod.common.battles.dispatch.ActionEffectInstruction
import com.cobblemon.mod.common.battles.dispatch.GO
import com.cobblemon.mod.common.battles.dispatch.InterpreterInstruction
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon
import com.cobblemon.mod.common.net.messages.client.battle.BattleTransformPokemonPacket
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.transformation.form.TemporaryForm
import com.cobblemon.mod.common.util.battleLang
import com.cobblemon.mod.common.util.cobblemonResource
import java.util.concurrent.CompletableFuture

/**
 * Format: |-formechange|POKEMON|SPECIES|HP STATUS or |detailschange|POKEMON|DETAILS|HP STATUS
 *
 * POKEMON has temporarily (-formechange) or permanently (detailschange) changed form to SPECIES/DETAILS.
 * @author Segfault Guy
 * @since December 9th, 2024
 */
class FormChangeInstruction(val message: BattleMessage): ActionEffectInstruction {
    override var future: CompletableFuture<*> = CompletableFuture.completedFuture(Unit)
    override var holds = mutableSetOf<String>()
    override val id = cobblemonResource("formchange")

    lateinit var battlePokemon: BattlePokemon
    lateinit var speciesName: String
    lateinit var formId: String
    lateinit var typeKey: String
    lateinit var pnx: String
    lateinit var form: FormData

    override fun preActionEffect(battle: PokemonBattle) {
        val details = message.argumentAt(1)?.split(',')?.get(0)?.lowercase() ?: return
        battlePokemon = message.battlePokemon(0, battle) ?: return
        speciesName = details.substringBefore('-')
        formId = details.substringAfter('-').ifBlank { speciesName }
        typeKey = if (message.id == "detailschange") "permanent" else "temporary"
        pnx = message.pnxAndUuid(0)?.first ?: return

        ShowdownInterpreter.broadcastOptionalAbility(battle, message.effect(), battlePokemon)
        battle.dispatchGo {
            battle.minorBattleActions[battlePokemon.uuid] = message
            form = battlePokemon.effectedPokemon.species.getFormByShowdownId(formId)
            CobblemonEvents.FORME_CHANGE.post(FormeChangeEvent(battle, battlePokemon, formId))
        }
    }

    override fun runActionEffect(battle: PokemonBattle, runtime: MoLangRuntime) {
        battle.dispatch {
            if (form is TemporaryForm) {
                val actionEffect = (form as TemporaryForm).actionEffect
                val providers = mutableListOf<Any>(battle)
                val pokemon = message.battlePokemon(0, battle) ?: return@dispatch GO
                pokemon.effectedPokemon.entity?.let { UsersProvider(it) }?.let(providers::add)
                val context = ActionEffectContext(
                    actionEffect = actionEffect,
                    runtime = runtime,
                    providers = providers,
                    level = battle.players.firstOrNull()?.level()
                )
                (form as TemporaryForm).forceStart(battlePokemon.effectedPokemon.entity!!, context)
            }
            else {
                battlePokemon.effectedPokemon.formProxy.server().revert()
            }
            return@dispatch GO
        }
    }

    override fun postActionEffect(battle: PokemonBattle) {
        battle.dispatchWaiting {
            CobblemonEvents.FORME_CHANGE.post(FormeChangeEvent(battle, battlePokemon, formId))
            val pokemonName = battlePokemon.getName()
            val lang = when (formId) {
                "busted", "hero", "complete" -> return@dispatchWaiting
                "school", "wishiwashi", "meteor", "minior" -> battleLang("formechange.$formId", pokemonName)
                speciesName -> battleLang("formechange.default.temporary.end", pokemonName)
                else -> battleLang("formechange.default.$typeKey", pokemonName, formId)
            }
            battle.sendSidedUpdate(
                source = battlePokemon.actor,
                allyPacket = BattleTransformPokemonPacket(pnx, battlePokemon, true),
                opponentPacket = BattleTransformPokemonPacket(pnx, battlePokemon, false)
            )
            battle.broadcastChatMessage(lang)
        }
    }
}