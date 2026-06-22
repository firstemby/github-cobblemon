/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.command

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.text.red
import com.cobblemon.mod.common.battles.BattleFormat
import com.cobblemon.mod.common.battles.BattleRegistry
import com.cobblemon.mod.common.battles.BattleSide
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import com.cobblemon.mod.common.battles.actor.PokemonBattleActor
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon
import com.cobblemon.mod.common.util.party
import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.MobSpawnType

/**
 * Test/dev command: spawns a wild level 20 Pikachu in front of the player and immediately starts a
 * singles battle against it. Handy for exercising move animations/particles in battle (e.g. for the
 * PhotonRT raytraced move-light integration). Op-only ({@code /spawnpikachubattle}).
 */
object SpawnPikachuBattleCommand {

    private const val NAME = "spawnpikachubattle"

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal(NAME)
                .requires { it.hasPermission(2) }
                .executes(::execute)
        )
    }

    private fun execute(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.entity as? ServerPlayer ?: run {
            context.source.sendFailure(Component.literal("This command must be run by a player.").red())
            return 0
        }
        val world = player.level() as ServerLevel

        // The player needs at least one Pokémon to battle with.
        val party = player.party()
        party.heal()
        val playerTeam = party.toBattleTeam()
        if (playerTeam.isEmpty()) {
            context.source.sendFailure(Component.literal("You need at least one Pokémon in your party to battle.").red())
            return 0
        }

        // Spawn a wild level 20 Pikachu ~2 blocks in front of the player, facing them.
        val look = player.lookAngle
        val spawnPos = player.position().add(look.x * 2.0, 0.0, look.z * 2.0)
        val pikachu = PokemonProperties.parse("pikachu level=20").createEntity(world)
        pikachu.moveTo(spawnPos.x, spawnPos.y, spawnPos.z, player.yRot + 180f, 0f)
        pikachu.finalizeSpawn(world, world.getCurrentDifficultyAt(pikachu.blockPosition()), MobSpawnType.COMMAND, null)
        if (!world.addFreshEntity(pikachu)) {
            context.source.sendFailure(Component.literal("Failed to spawn the Pikachu at the target position.").red())
            return 0
        }

        // Start a single battle: the player (their party) vs the wild Pikachu.
        BattleRegistry.startBattle(
            BattleFormat.GEN_9_SINGLES,
            BattleSide(PlayerBattleActor(player.uuid, playerTeam)),
            BattleSide(PokemonBattleActor(pikachu.pokemon.uuid, BattlePokemon(pikachu.pokemon), Cobblemon.config.defaultFleeDistance))
        )

        context.source.sendSuccess({ Component.literal("Spawned a level 20 Pikachu and started a battle.") }, false)
        return Command.SINGLE_SUCCESS
    }
}
