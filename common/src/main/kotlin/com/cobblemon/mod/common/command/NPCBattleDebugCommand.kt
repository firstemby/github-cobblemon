/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.command

import com.cobblemon.mod.common.api.permission.CobblemonPermissions
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.storage.party.NPCPartyStore
import com.cobblemon.mod.common.api.text.red
import com.cobblemon.mod.common.api.npc.NPCClasses
import com.cobblemon.mod.common.CobblemonNetwork.sendPacket
import com.cobblemon.mod.common.battles.BattleBuilder
import com.cobblemon.mod.common.battles.BattleFormat
import com.cobblemon.mod.common.battles.BattleRegistry
import com.cobblemon.mod.common.battles.BattleRules
import com.cobblemon.mod.common.battles.BattleSide
import com.cobblemon.mod.common.battles.BattleStartError
import com.cobblemon.mod.common.battles.BattleStartResult
import com.cobblemon.mod.common.battles.ErroredBattleStart
import com.cobblemon.mod.common.entity.npc.NPCBattleActor
import com.cobblemon.mod.common.entity.npc.NPCEntity
import com.cobblemon.mod.common.net.messages.client.battle.BattleInitializePacket
import com.cobblemon.mod.common.net.messages.client.battle.BattleMessagePacket
import com.cobblemon.mod.common.util.requiresWithPermission
import com.cobblemon.mod.common.util.isInBattle
import com.cobblemon.mod.common.util.toBlockPos
import com.cobblemon.mod.common.util.update
import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.argument
import net.minecraft.commands.Commands.literal
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

object NPCBattleDebugCommand {
    private const val NAME = "npcbattledebug"
    private const val SINGLES = "singles"
    private const val DOUBLES = "doubles"
    private const val SPAWN_TWO = "spawn_two"
    private const val LEVEL = "level"
    private const val TEAM_SIZE = "team_size"

    private const val DEFAULT_LEVEL = 50
    private const val DEFAULT_TEAM_SIZE = 6
    private const val NPC_DISTANCE = 8.0

    private val INVALID_POS_EXCEPTION = SimpleCommandExceptionType(Component.literal("Invalid position").red())
    private val FAILED_SPAWN_EXCEPTION = SimpleCommandExceptionType(Component.literal("Unable to spawn at the given position").red())
    private val NO_FULLY_EVOLVED_SPECIES_EXCEPTION = SimpleCommandExceptionType(Component.literal("No fully evolved species were found in the loaded species registry.").red())

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            literal(NAME)
                .requiresWithPermission(CobblemonPermissions.NPC_BATTLE_DEBUG) { it.player != null }
                .then(formatNode(SINGLES, BattleFormat.GEN_9_SINGLES))
                .then(formatNode(DOUBLES, BattleFormat.GEN_9_DOUBLES))
        )
    }

    private fun formatNode(
        formatName: String,
        format: BattleFormat
    ) = literal(formatName)
        .executes { context ->
            execute(
                context = context,
                format = format,
                spawnTwo = false,
                level = DEFAULT_LEVEL,
                teamSize = DEFAULT_TEAM_SIZE
            )
        }
        .then(argument(SPAWN_TWO, BoolArgumentType.bool())
            .executes { context ->
                execute(
                    context = context,
                    format = format,
                    spawnTwo = BoolArgumentType.getBool(context, SPAWN_TWO),
                    level = DEFAULT_LEVEL,
                    teamSize = DEFAULT_TEAM_SIZE
                )
            }
            .then(argument(LEVEL, IntegerArgumentType.integer(1, 100))
                .executes { context ->
                    execute(
                        context = context,
                        format = format,
                        spawnTwo = BoolArgumentType.getBool(context, SPAWN_TWO),
                        level = IntegerArgumentType.getInteger(context, LEVEL),
                        teamSize = DEFAULT_TEAM_SIZE
                    )
                }
                .then(argument(TEAM_SIZE, IntegerArgumentType.integer(1, 6))
                    .executes { context ->
                        execute(
                            context = context,
                            format = format,
                            spawnTwo = BoolArgumentType.getBool(context, SPAWN_TWO),
                            level = IntegerArgumentType.getInteger(context, LEVEL),
                            teamSize = IntegerArgumentType.getInteger(context, TEAM_SIZE)
                        )
                    }
                )
            )
        )

    private fun execute(
        context: CommandContext<CommandSourceStack>,
        format: BattleFormat,
        spawnTwo: Boolean,
        level: Int,
        teamSize: Int
    ): Int {
        val effectiveFormat = format.copy(ruleSet = format.ruleSet - BattleRules.TEAM_PREVIEW)
        val player = context.source.playerOrException
        val requiredSlots = effectiveFormat.battleType.slotsPerActor
        if (teamSize < requiredSlots) {
            context.source.sendFailure(Component.literal("team_size must be at least $requiredSlots for $effectiveFormat format."))
            return 0
        }

        val fullyEvolvedSpecies = PokemonSpecies.implemented.filter { it.standardForm.evolutions.isEmpty() }
        if (fullyEvolvedSpecies.isEmpty()) {
            throw NO_FULLY_EVOLVED_SPECIES_EXCEPTION.create()
        }

        return if (spawnTwo) {
            executeNpcVsNpc(context, player, effectiveFormat, level, teamSize, fullyEvolvedSpecies)
        } else {
            executeNpcVsPlayer(context, player, effectiveFormat, level, teamSize, fullyEvolvedSpecies)
        }
    }

    private fun executeNpcVsPlayer(
        context: CommandContext<CommandSourceStack>,
        player: ServerPlayer,
        format: BattleFormat,
        level: Int,
        teamSize: Int,
        fullyEvolvedSpecies: List<com.cobblemon.mod.common.pokemon.Species>
    ): Int {
        val world = context.source.level
        val frontPos = player.position().add(horizontalLook(player).scale(NPC_DISTANCE))
        val npc = spawnDebugNpc(world, frontPos, level, teamSize, fullyEvolvedSpecies, "AI Man")

        val result = BattleBuilder.pvn(
            player = player,
            npcEntity = npc,
            battleFormat = format,
            cloneParties = true,
            healFirst = true
        )

        result.ifErrored { errors ->
            errors.sendTo(player)
        }
        result.ifSuccessful {
            context.source.sendSuccess(
                { Component.literal("Spawned 1 NPC at +$NPC_DISTANCE blocks and started a $format battle.") },
                true
            )
        }
        return Command.SINGLE_SUCCESS
    }

    private fun executeNpcVsNpc(
        context: CommandContext<CommandSourceStack>,
        player: ServerPlayer,
        format: BattleFormat,
        level: Int,
        teamSize: Int,
        fullyEvolvedSpecies: List<com.cobblemon.mod.common.pokemon.Species>
    ): Int {
        val world = context.source.level
        val look = horizontalLook(player)
        val frontPos = player.position().add(look.scale(NPC_DISTANCE))
        val backPos = player.position().add(look.scale(-NPC_DISTANCE))

        val npc1 = spawnDebugNpc(world, frontPos, level, teamSize, fullyEvolvedSpecies, "AI Man")
        val npc2 = spawnDebugNpc(world, backPos, level, teamSize, fullyEvolvedSpecies, "AI Woman")

        val result = startNpcVsNpcBattle(npc1, npc2, format)
        result.ifErrored { errors ->
            errors.sendTo(player)
        }
        result.ifSuccessful { battle ->
            startSpectatingBattle(player, battle)
            context.source.sendSuccess(
                { Component.literal("Spawned 2 NPCs at +/-$NPC_DISTANCE blocks, started a $format NPC battle, and put you into spectator view.") },
                true
            )
        }
        return Command.SINGLE_SUCCESS
    }

    private fun spawnDebugNpc(
        world: Level,
        position: Vec3,
        level: Int,
        teamSize: Int,
        fullyEvolvedSpecies: List<com.cobblemon.mod.common.pokemon.Species>,
        name: String
    ): NPCEntity {
        val blockPos = position.toBlockPos()
        if (!Level.isInSpawnableBounds(blockPos)) {
            throw INVALID_POS_EXCEPTION.create()
        }

        val npc = NPCEntity(world)
        npc.moveTo(position.x, position.y, position.z, npc.yRot, npc.xRot)
        npc.npc = NPCClasses.getByName("standard") ?: NPCClasses.classes.first()
        npc.initialize(level)
        npc.customName = Component.literal(name)
        npc.skill = 5
        npc.party = buildRandomParty(npc, level, teamSize, fullyEvolvedSpecies)
        npc.party?.initialize()

        if (!world.addFreshEntity(npc)) {
            throw FAILED_SPAWN_EXCEPTION.create()
        }
        return npc
    }

    private fun buildRandomParty(
        npc: NPCEntity,
        level: Int,
        teamSize: Int,
        fullyEvolvedSpecies: List<com.cobblemon.mod.common.pokemon.Species>
    ): NPCPartyStore {
        val party = NPCPartyStore(npc)
        val chosenSpecies = if (fullyEvolvedSpecies.size >= teamSize) {
            fullyEvolvedSpecies.shuffled().take(teamSize)
        } else {
            buildList {
                addAll(fullyEvolvedSpecies)
                repeat(teamSize - fullyEvolvedSpecies.size) {
                    add(fullyEvolvedSpecies.random())
                }
            }
        }

        chosenSpecies.forEachIndexed { index, species ->
            val pokemon = species.create(level).also { it.heal() }
            party.set(index, pokemon)
        }
        return party
    }

    private fun startNpcVsNpcBattle(
        npc1: NPCEntity,
        npc2: NPCEntity,
        format: BattleFormat
    ): BattleStartResult {
        val party1 = npc1.party ?: return ErroredBattleStart(mutableSetOf(BattleStartError.noParty(npc1)))
        val party2 = npc2.party ?: return ErroredBattleStart(mutableSetOf(BattleStartError.noParty(npc2)))

        val actor1 = NPCBattleActor(npc1, party1, npc1.skill ?: npc1.npc.skill)
        val actor2 = NPCBattleActor(npc2, party2, npc2.skill ?: npc2.npc.skill)
        val errors = ErroredBattleStart()
        val requiredSlots = format.battleType.slotsPerActor

        if (actor1.pokemonList.filter { it.health > 0 }.size < requiredSlots) {
            errors.participantErrors[actor1] += BattleStartError.insufficientPokemon(npc1, requiredSlots, actor1.pokemonList.size)
        }
        if (actor2.pokemonList.filter { it.health > 0 }.size < requiredSlots) {
            errors.participantErrors[actor2] += BattleStartError.insufficientPokemon(npc2, requiredSlots, actor2.pokemonList.size)
        }
        if (!errors.isEmpty) {
            return errors
        }

        return BattleRegistry.startBattle(
            battleFormat = format,
            side1 = BattleSide(actor1),
            side2 = BattleSide(actor2)
        ).ifSuccessful { battle ->
            npc1.entityData.update(NPCEntity.BATTLE_IDS) { it + battle.battleId }
            npc2.entityData.update(NPCEntity.BATTLE_IDS) { it + battle.battleId }
        }
    }

    private fun horizontalLook(player: ServerPlayer): Vec3 {
        val look = player.lookAngle
        val horizontal = Vec3(look.x, 0.0, look.z)
        if (horizontal.lengthSqr() < 1.0E-6) {
            return Vec3(0.0, 0.0, 1.0)
        }
        return horizontal.normalize()
    }

    private fun startSpectatingBattle(
        player: ServerPlayer,
        battle: com.cobblemon.mod.common.api.battles.model.PokemonBattle
    ) {
        if (player.isInBattle()) {
            return
        }
        battle.spectators.add(player.uuid)
        player.sendPacket(BattleInitializePacket(battle, null))
        player.sendPacket(BattleMessagePacket(battle.chatLog))
    }
}
