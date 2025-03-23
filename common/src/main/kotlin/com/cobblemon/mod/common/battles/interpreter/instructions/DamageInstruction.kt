/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battles.interpreter.instructions

import com.bedrockk.molang.runtime.MoLangRuntime
import com.cobblemon.mod.common.api.battles.interpreter.BattleMessage
import com.cobblemon.mod.common.api.battles.interpreter.Effect
import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor
import com.cobblemon.mod.common.api.moves.animations.ActionEffectContext
import com.cobblemon.mod.common.api.moves.animations.ActionEffects
import com.cobblemon.mod.common.api.moves.animations.UsersProvider
import com.cobblemon.mod.common.api.pokemon.status.Statuses
import com.cobblemon.mod.common.api.text.red
import com.cobblemon.mod.common.battles.ShowdownInterpreter
import com.cobblemon.mod.common.battles.dispatch.ActionEffectInstruction
import com.cobblemon.mod.common.battles.dispatch.GO
import com.cobblemon.mod.common.battles.dispatch.InstructionSet
import com.cobblemon.mod.common.battles.dispatch.UntilDispatch
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.net.messages.client.animation.PlayPosableAnimationPacket
import com.cobblemon.mod.common.net.messages.client.battle.BattleHealthChangePacket
import com.cobblemon.mod.common.net.messages.client.effect.RunPosableMoLangPacket
import com.cobblemon.mod.common.pokemon.evolution.progress.DamageTakenEvolutionProgress
import com.cobblemon.mod.common.pokemon.evolution.progress.RecoilEvolutionProgress
import com.cobblemon.mod.common.pokemon.status.statuses.persistent.PoisonBadlyStatus
import com.cobblemon.mod.common.pokemon.status.statuses.persistent.PoisonStatus
import com.cobblemon.mod.common.util.asIdentifierDefaultingNamespace
import com.cobblemon.mod.common.util.battleLang
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.lang
import java.util.concurrent.CompletableFuture
import net.minecraft.network.chat.Component

/**
 * Format: |-damage|POKEMON|HP STATUS
 *
 * POKEMON has taken damage and is now at HP STATUS
 * @author Hiroku
 * @since March 11th, 2022
 */
class DamageInstruction(
    val instructionSet: InstructionSet,
    val actor: BattleActor,
    val publicMessage: BattleMessage,
    val privateMessage: BattleMessage
) : ActionEffectInstruction {
    val expectedTarget = publicMessage.battlePokemon(0, actor.battle)
    override var future: CompletableFuture<*> = CompletableFuture.completedFuture(Unit)
    override var holds = mutableSetOf<String>()
    override val id = cobblemonResource("damage")

    override fun preActionEffect(battle: PokemonBattle) {
        val battlePokemon = publicMessage.battlePokemon(0, actor.battle) ?: return
        val recoiling = privateMessage.optionalArgument("from")?.equals("recoil", true) == true
        val lastCauser  = instructionSet.getMostRecentCauser(comparedTo = this)
        if (recoiling) {
            doRecoilEvoChecks(battlePokemon)
            if (lastCauser is MoveInstruction) {
                battle.dispatch {
                    //Is the recoil hold currently used anywhere?
                    UntilDispatch { "recoil" !in lastCauser.holds }
                }
            }
        }
        val effect = privateMessage.effect()
        val source = privateMessage.battlePokemonFromOptional(battle)
        if (source != null) {
            ShowdownInterpreter.broadcastOptionalAbility(battle, effect, source)
        }
    }


    override fun runActionEffect(battle: PokemonBattle, runtime: MoLangRuntime) {
        val effect = privateMessage.effect()
        val publicPokemon = publicMessage.battlePokemon(0, actor.battle) ?: return
        var status = effect?.id?.let { Statuses.getStatus(it) }
        battle.dispatch {
            //Showdown doesnt tell us on damage if its poison or toxic so we gotta consult the entity
            val isToxic = publicPokemon.effectedPokemon.status?.status is PoisonBadlyStatus
            val realStatus = if (isToxic) PoisonBadlyStatus() else status
            // Damage effects without a action effect receive the generic damage effect
            val actionEffect = if (realStatus != null) {
                realStatus.getActionEffect()
            } else if (effect != null) {
                ActionEffects.actionEffects[cobblemonResource("generic_damage")]
            } else {
                null
            }

            if (actionEffect == null) {
                return@dispatch GO
            }

            val providers = mutableListOf<Any>(battle)
            val pokemonEntity = publicPokemon.effectedPokemon.entity
            if (pokemonEntity != null) {
                providers.add(UsersProvider(pokemonEntity))
            }

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
        val hpStatus = privateMessage.argumentAt(1)?.split(" ")?.get(0)!!
        var causedFaint = hpStatus == "0"
        val remainingHealth = hpStatus.split("/")[0].toFloat()
        val maxHealth = hpStatus.split("/").getOrNull(1)?.toFloat()
        val battlePokemon = publicMessage.battlePokemon(0, actor.battle) ?: return
        val effect = privateMessage.effect()
        val source = privateMessage.battlePokemonFromOptional(battle)
        val lastCauser  = instructionSet.getMostRecentCauser(comparedTo = this)
        val pokemonName = battlePokemon.getName()
        val pokemonEntity = battlePokemon.entity

        battle.dispatch {
            playRecoilAnim(pokemonEntity, causedFaint)
            if (effect != null) {
                sendStatusMessage(battle, effect, pokemonName, source)
            }

            updateHealth(battle, battlePokemon, remainingHealth, maxHealth)

            battle.minorBattleActions[battlePokemon.uuid] = privateMessage
            if (lastCauser is MoveInstruction) {
                if (lastCauser.spreadTargets.size > 1) {
                    val subsequentInstructions = instructionSet.findInstructionsCausedBy(lastCauser)
                    // Only wait on the last target of a multitarget attack
                    if (subsequentInstructions.filterIsInstance<DamageInstruction>().last() != this) {
                        // TODO: Dragon darts?
                        return@dispatch GO
                    }
                }
            }

            // If they faint from this damage then don't bother waiting for the damage to be applied.
            if (lastCauser is MoveInstruction && lastCauser.actionEffect != null && !causedFaint) {
                UntilDispatch { lastCauser.future.isDone }
            } else if (causedFaint) {
                GO
            } else {
                UntilDispatch { "effects" !in holds}
            }
        }
    }

    private fun updateHealth(battle: PokemonBattle, battlePokemon: BattlePokemon, remainingHealth: Float, maxHealth: Float?) {
        val causedFaint = maxHealth == null
        val newHealthRatio = if (maxHealth == null) 0F else remainingHealth / maxHealth
        if (causedFaint) {
            battle.dispatch {
                battlePokemon.effectedPokemon.currentHealth = 0
                battlePokemon.sendUpdate()
                GO
            }
        }
        else {
            val difference = maxHealth - remainingHealth
            battle.dispatchToFront {
                battlePokemon.effectedPokemon.currentHealth = remainingHealth.toInt()
                updateDamageTakenEvo(battlePokemon, difference.toInt())
                battlePokemon.sendUpdate()
                GO
            }
        }
        privateMessage.pnxAndUuid(0)?.let { (pnx, _ ) ->
            battle.sendSidedUpdate(actor, BattleHealthChangePacket(pnx, remainingHealth), BattleHealthChangePacket(pnx, newHealthRatio))
        }

    }

    private fun doRecoilEvoChecks(battlePokemon: BattlePokemon) {
        battlePokemon.effectedPokemon.let { pokemon ->
            if (RecoilEvolutionProgress.supports(pokemon)) {
                val healthStr = privateMessage.argumentAt(1) ?: throw UnsupportedOperationException("Cant get recoil string")
                val newHealth =
                    "([0-9]+).*".toRegex().find(healthStr)?.groups?.get(1)?.value?.toIntOrNull()
                        ?: throw UnsupportedOperationException("Cant get recoil string")
                val difference = pokemon.currentHealth - newHealth
                if (difference > 0) {
                    val progress = pokemon.evolutionProxy.current()
                        .progressFirstOrCreate({ it is RecoilEvolutionProgress }) { RecoilEvolutionProgress() }
                    progress.updateProgress(RecoilEvolutionProgress.Progress(progress.currentProgress().recoil + difference))
                }
            }
        }
    }

    private fun sendStatusMessage(battle: PokemonBattle, effect: Effect, pokemonName: Component, source: BattlePokemon?) {
        val lang = when (effect.id) {
            "blacksludge", "stickybarb", "jabocaberry", "rowapberry" -> battleLang("damage.item", pokemonName, effect.typelessData)
            "brn", "psn", "tox" -> {
                val status = Statuses.getStatus(effect.id)?.name?.path
                lang("status.$status.hurt", pokemonName)
            }
            "aftermath", "innardsout" -> battleLang("damage.generic", pokemonName)
            "chloroblast", "steelbeam" -> battleLang("damage.mindblown", pokemonName)
            "jumpkick" -> battleLang("damage.highjumpkick", pokemonName)
            else -> battleLang("damage.${effect.id}", pokemonName, source?.getName() ?: Component.literal("UNKOWN"))
        }
        battle.broadcastChatMessage(lang.red())
    }

    private fun playRecoilAnim(pokemonEntity: PokemonEntity?, causedFaint: Boolean) {
        if (!causedFaint && pokemonEntity != null) {
            val pkt = PlayPosableAnimationPacket(pokemonEntity.id, setOf("recoil"), emptyList())
            pkt.sendToPlayersAround(
                x = pokemonEntity.x,
                y = pokemonEntity.y,
                z = pokemonEntity.z,
                worldKey = pokemonEntity.level().dimension(),
                distance = 50.0
            )
        }
    }

    private fun updateDamageTakenEvo(battlePokemon: BattlePokemon, damageTaken: Int) {
        battlePokemon.effectedPokemon.let { pokemon ->
            if (DamageTakenEvolutionProgress.supports(pokemon)) {
                val progress = pokemon.evolutionProxy.current().progressFirstOrCreate({ it is DamageTakenEvolutionProgress }) { DamageTakenEvolutionProgress() }
                progress.updateProgress(DamageTakenEvolutionProgress.Progress(progress.currentProgress().amount + damageTaken))
            }
        }
    }
}