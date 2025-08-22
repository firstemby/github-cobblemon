/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battles.interpreter.instructions

import com.bedrockk.molang.runtime.MoLangRuntime
import com.bedrockk.molang.runtime.value.DoubleValue
import com.bedrockk.molang.runtime.value.StringValue
import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.battles.interpreter.BattleMessage
import com.cobblemon.mod.common.api.battles.interpreter.Effect
import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.api.battles.model.actor.EntityBackedBattleActor
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.battles.BattleFledEvent
import com.cobblemon.mod.common.api.molang.MoLangFunctions.addBattleMessageFunctions
import com.cobblemon.mod.common.api.molang.MoLangFunctions.addStandardFunctions
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMostSpecificMoLangValue
import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.api.moves.animations.ActionEffectContext
import com.cobblemon.mod.common.api.moves.animations.TargetsProvider
import com.cobblemon.mod.common.api.moves.animations.UsersProvider
import com.cobblemon.mod.common.api.text.yellow
import com.cobblemon.mod.common.battles.ShowdownInterpreter
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import com.cobblemon.mod.common.battles.dispatch.CauserInstruction
import com.cobblemon.mod.common.battles.dispatch.GO
import com.cobblemon.mod.common.battles.dispatch.InstructionSet
import com.cobblemon.mod.common.battles.dispatch.InterpreterInstruction
import com.cobblemon.mod.common.battles.dispatch.UntilDispatch
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.evolution.progress.UseMoveEvolutionProgress
import com.cobblemon.mod.common.util.*
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import java.util.concurrent.CompletableFuture
import java.lang.Math.*
import kotlin.math.roundToInt

/**
 * Format: |move|POKEMON|MOVE|TARGET
 *
 * POKEMON has used MOVE at TARGET.
 * @author Deltric
 * @since January 22nd, 2022
 */
class MoveInstruction(
    val instructionSet: InstructionSet,
    val message: BattleMessage
) : InterpreterInstruction, CauserInstruction {
    val effect = message.effectAt(1) ?: Effect.pure("", "")
    val move = Moves.getByNameOrDummy(effect.id)
    val actionEffect = move.actionEffect
    val spreadTargets = message.optionalArgument("spread")?.split(",") ?: emptyList()

    var future = CompletableFuture.completedFuture(Unit)
    var holds = mutableSetOf<String>()

    lateinit var userPokemon: BattlePokemon
    var targetPokemon: BattlePokemon? = null

    override fun invoke(battle: PokemonBattle) {
        userPokemon = message.battlePokemon(0, battle)!!
        targetPokemon = message.battlePokemon(2, battle)
        val targetPokemon = targetPokemon // So smart non-null casts can happen

        val optionalEffect = message.effect()
        ShowdownInterpreter.broadcastOptionalAbility(battle, optionalEffect, userPokemon)

        battle.dispatch { UntilDispatch { instructionSet.getMostRecentInstruction<MoveInstruction>(this)?.future?.isDone != false } }

        battle.dispatch {
            val pokemonName = userPokemon.getName()
            ShowdownInterpreter.lastCauser[battle.battleId] = message
            // For Spread targets the message data only gives the pnx strings. So we can't know what pokemon are actually targeted until the previous messages have been interpreted
            val spreadTargetPokemon =
                spreadTargets.map { battle.activePokemon.firstOrNull() { poke -> poke.getPNX() == it }?.battlePokemon }

            val targetPokemonEntity = targetPokemon?.entity
            if (targetPokemonEntity != null) {
                userPokemon.entity?.brain?.setMemory(
                    CobblemonMemories.TARGETED_BATTLE_POKEMON,
                    targetPokemonEntity.uuid
                )
            }

            userPokemon.effectedPokemon.let { pokemon ->
                if (UseMoveEvolutionProgress.supports(pokemon, move)) {
                    val progress = pokemon.evolutionProxy.current()
                        .progressFirstOrCreate({ it is UseMoveEvolutionProgress && it.currentProgress().move == move }) { UseMoveEvolutionProgress() }
                    progress.updateProgress(
                        UseMoveEvolutionProgress.Progress(
                            move,
                            progress.currentProgress().amount + 1
                        )
                    )
                }
            }

            val lang = when {
                optionalEffect?.id == "magicbounce" ->
                    battleLang("ability.magicbounce", pokemonName, move.displayName)
                move.name != "struggle" && spreadTargetPokemon.isEmpty() && targetPokemon != null && targetPokemon != userPokemon && targetPokemon.health > 0 ->
                    battleLang("used_move_on", pokemonName, move.displayName, targetPokemon.getName())
                else ->
                    battleLang("used_move", pokemonName, move.displayName)
            }
            battle.broadcastChatMessage(lang)
            if (isWildTeleport(battle)) {
                val teleportingEntity = userPokemon.originalPokemon.entity
                if (teleportingEntity != null) {
                    tryTeleport(battle.players.firstOrNull()?.level() as ServerLevel, teleportingEntity, 10, 30, 32)
                }

                battle.dispatchWaiting(1.0F) {
                    battle.actors.filterIsInstance<EntityBackedBattleActor<*>>().mapNotNull { it.entity }
                        .forEach { it.sendSystemMessage(battleLang("wild_flee", pokemonName).yellow()) }
                    battle.end()
                }
            }
            battle.majorBattleActions[userPokemon.uuid] = message

            val runtime = MoLangRuntime().also {
                battle.addQueryFunctions(it.environment.query).addStandardFunctions()
                it.environment.query.addBattleMessageFunctions(message)
            }

            val providers = mutableListOf<Any>(battle)
            userPokemon.effectedPokemon.entity?.let { entity ->
                runtime.environment.query.addFunction("user") { entity.asMostSpecificMoLangValue() }
                UsersProvider(entity)
            }?.let(providers::add)

            if (spreadTargetPokemon.isNotEmpty()) {
                val targetEntities = spreadTargetPokemon.mapNotNull { it?.effectedPokemon?.entity }
                runtime.environment.query.addFunction("targets") { targetEntities.asArrayValue { it.asMostSpecificMoLangValue() } }
                providers.add(TargetsProvider(targetEntities))
            } else {
                targetPokemon?.effectedPokemon?.entity?.let { entity ->
                    runtime.environment.query.addFunction("target") { entity.asMostSpecificMoLangValue() }
                    val provider = TargetsProvider(entity)
                    providers.add(provider)
                }
            }

            actionEffect ?: return@dispatch GO
            val context = ActionEffectContext(
                actionEffect = actionEffect,
                runtime = runtime,
                providers = providers,
                level = battle.players.firstOrNull()?.level()
            )

            val subsequentInstructions = instructionSet.findInstructionsCausedBy(this)
            val missedTargets = subsequentInstructions.filterIsInstance<MissInstruction>().mapNotNull { it.target }
            val hitCountInstruction = subsequentInstructions.filterIsInstance<HitCountInstruction>().firstOrNull()

            runtime.environment.query.addFunction("missed") { params ->
                if (params.params.size == 0) {
                    return@addFunction DoubleValue(missedTargets.isNotEmpty())
                } else {
                    val entityUUID = params.getString(0)
                    return@addFunction DoubleValue(missedTargets.any { it.entity?.stringUUID == entityUUID })
                }
            }

            val hurtTargets =
                subsequentInstructions.filterIsInstance<DamageInstruction>().mapNotNull { it.expectedTarget }
            runtime.environment.query.addFunction("hurt") { params ->
                if (params.params.size == 0) {
                    return@addFunction DoubleValue(hurtTargets.isNotEmpty())
                } else {
                    val entityUUID = params.getString(0)
                    return@addFunction DoubleValue(hurtTargets.any { it.entity?.stringUUID == entityUUID })
                }
            }

            if (hitCountInstruction != null && hitCountInstruction.hitCount != null) {
                runtime.environment.query.addFunction("hit_count") { DoubleValue(hitCountInstruction.hitCount.toDouble()) }
            }

            runtime.environment.query.addFunction("move") { move.struct }
            runtime.environment.query.addFunction("instruction_id") { StringValue(cobblemonResource("move").toString()) }

            this.future = actionEffect.run(context)
            holds = context.holds // Reference so future things can check on this action effect's holds
            future.thenApply { holds.clear() }
            return@dispatch UntilDispatch { "effects" !in holds }.andThen {
                val userPokemonId = userPokemon.entity?.uuid ?: return@andThen
                val targets = hurtTargets.mapNotNull { it.entity }
                userPokemonId.let { id ->
                    targets.forEach {
                        it.brain.setMemory(
                            CobblemonMemories.TARGETED_BATTLE_POKEMON,
                            id
                        )
                    }
                }
            }
        }
    }

    fun tryTeleport(
        level: ServerLevel,
        pokemon: PokemonEntity,
        minRadius: Int,
        maxRadius: Int,
        attempts: Int
    ): Boolean {
        val origin = pokemon.blockPosition()
        val rng = level.random

        for (i in 0 until attempts) {
            val dist = rng.nextInt(minRadius, maxRadius + 1)
            val angle = rng.nextDouble() * (PI * 2.0)
            val dx = (cos(angle) * dist).roundToInt()
            val dz = (sin(angle) * dist).roundToInt()
            val x = (origin.x + dx)
            val z = (origin.z + dz)

            val minY = max((origin.y - 20), level.minBuildHeight)
            val maxY = min((origin.y + 20), level.maxBuildHeight)

            for (y in minY..maxY) {
                val candidate = BlockPos(x, y, z)

                if (pokemon.pokemon.isPositionSafe(level, candidate) && level.getBlockState(candidate).isAir) {
                    level.playSoundServer(origin.toVec3d(), SoundEvents.PLAYER_TELEPORT)
                    val success = pokemon.teleportTo(
                        level,
                        candidate.x + 0.5,
                        candidate.y.toDouble(),
                        candidate.z + 0.5,
                        mutableSetOf(),
                        pokemon.yRot,
                        pokemon.xRot
                    )
                    if (success) return true
                }
            }
        }
        return false
    }

    fun isWildTeleport(battle: PokemonBattle) : Boolean {
        return move.name == "teleport" && message.battlePokemon(0, battle)?.originalPokemon?.isWild() ?: false
    }
}