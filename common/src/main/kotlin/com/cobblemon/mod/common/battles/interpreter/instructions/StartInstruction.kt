/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battles.interpreter.instructions

import com.bedrockk.molang.runtime.MoLangRuntime
import com.cobblemon.mod.common.api.battles.interpreter.BattleContext
import com.cobblemon.mod.common.api.battles.interpreter.BattleMessage
import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.api.moves.animations.ActionEffectContext
import com.cobblemon.mod.common.api.moves.animations.ActionEffects
import com.cobblemon.mod.common.api.moves.animations.UsersProvider
import com.cobblemon.mod.common.api.text.yellow
import com.cobblemon.mod.common.battles.ShowdownInterpreter
import com.cobblemon.mod.common.battles.dispatch.ActionEffectInstruction
import com.cobblemon.mod.common.battles.dispatch.GO
import com.cobblemon.mod.common.battles.dispatch.InterpreterInstruction
import com.cobblemon.mod.common.battles.dispatch.UntilDispatch
import com.cobblemon.mod.common.util.battleLang
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.chat.Component
import java.util.concurrent.CompletableFuture

/**
 * Format: |-start|POKEMON|EFFECT
 *
 * A volatile status has been inflicted on POKEMON by EFFECT.
 * @author Deltric
 * @since January 21st, 2022
 */
class StartInstruction(val message: BattleMessage): ActionEffectInstruction, InterpreterInstruction {
    override var future: CompletableFuture<*> = CompletableFuture.completedFuture(Unit)
    override var holds = mutableSetOf<String>()
    override val id = cobblemonResource("start")

    override fun preActionEffect(battle: PokemonBattle)
    {
        val pokemon = message.battlePokemon(0, battle) ?: return
        val effectID = message.effectAt(1)?.id ?: return

        // skip adding contexts for every time the perish counter decrements
        if (!effectID.contains("perish")) {
            // don't need to add unique: showdown won't send -start instruction if volatile status is already present
            pokemon.contextManager.add(ShowdownInterpreter.getContextFromAction(message, BattleContext.Type.VOLATILE, battle))
        }

        battle.dispatch{
            battle.minorBattleActions[pokemon.uuid] = message
            GO
        }
    }

    override fun runActionEffect(battle: PokemonBattle, runtime: MoLangRuntime) {
        battle.dispatch {
            val pokemon = message.battlePokemon(0, battle) ?: return@dispatch GO
            val effectID = message.effectAt(1)?.id ?: return@dispatch GO
            val actionEffect = ActionEffects.actionEffects[cobblemonResource("start_${effectID}")] ?: return@dispatch GO
            val providers = mutableListOf<Any>(battle)

            pokemon.effectedPokemon.entity?.let { UsersProvider(it) }?.let(providers::add)
            val context = ActionEffectContext(
                actionEffect = actionEffect,
                runtime = runtime,
                providers = providers,
                level = battle.players.firstOrNull()?.level()
            )
            this.future = actionEffect.run(context)
            holds = context.holds // Reference so future things can check on this action effect's holds
            future.thenApply { holds.clear() }
            return@dispatch GO
        }
    }

    override fun postActionEffect(battle: PokemonBattle) {
        battle.dispatch {
            val pokemon = message.battlePokemon(0, battle) ?: return@dispatch GO
            val effectID = message.effectAt(1)?.id ?: return@dispatch GO
            val optionalEffect = message.effect()

            val optionalPokemon = message.battlePokemonFromOptional(battle)
            val optionalPokemonName = optionalPokemon?.getName()
            val extraEffect = message.effectAt(2)?.typelessData ?: Component.literal("UNKOWN")

            if (!message.hasOptionalArgument("silent")) {
                val lang = if (optionalEffect?.id == "reflecttype" && optionalPokemonName != null)
                    battleLang("start.reflecttype", pokemon.getName(), optionalPokemonName)
                else
                    when (effectID) {
                        "perish3" -> return@dispatch GO // Skip
                        "perish2", "perish1", "perish0",
                        "stockpile1", "stockpile2", "stockpile3" -> battleLang("start.${effectID.dropLast(1)}", pokemon.getName(), effectID.last().digitToInt())
                        "dynamax" -> battleLang("start.${message.effectAt(2)?.id ?: effectID}", pokemon.getName()).yellow()
                        "curse" -> battleLang("start.curse", message.battlePokemonFromOptional(battle)!!.getName(), pokemon.getName())
                        else -> battleLang("start.$effectID", pokemon.getName(), extraEffect)
                    }
                battle.broadcastChatMessage(lang)
            }
            //We check holds here so the chat msg + particle effect happen more concurrently, instead of sequentially
            UntilDispatch { "effects" !in holds}
        }
    }
}