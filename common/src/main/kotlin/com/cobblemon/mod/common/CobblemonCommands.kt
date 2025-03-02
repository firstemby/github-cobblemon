/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common

import com.cobblemon.mod.common.command.AbandonMultiTeam
import com.cobblemon.mod.common.command.ApplyPlayerTextureCommand
import com.cobblemon.mod.common.command.BedrockParticleCommand
import com.cobblemon.mod.common.command.ChangeBoxWallpaperCommand
import com.cobblemon.mod.common.command.ChangeEyeHeight
import com.cobblemon.mod.common.command.ChangeJointScaleCommand
import com.cobblemon.mod.common.command.ChangeScaleAndSize
import com.cobblemon.mod.common.command.ChangeWalkSpeed
import com.cobblemon.mod.common.command.CheckSpawnsCommand
import com.cobblemon.mod.common.command.ClearPCCommand
import com.cobblemon.mod.common.command.ClearPartyCommand
import com.cobblemon.mod.common.command.ClickTextCommand
import com.cobblemon.mod.common.command.CobblemonConfigCommand
import com.cobblemon.mod.common.command.CobblemonInfoCommand
import com.cobblemon.mod.common.command.FreezePokemonCommand
import com.cobblemon.mod.common.command.FriendshipCommand
import com.cobblemon.mod.common.command.GetNBT
import com.cobblemon.mod.common.command.GiveAllPokemon
import com.cobblemon.mod.common.command.GivePokemon
import com.cobblemon.mod.common.command.HealPokemonCommand
import com.cobblemon.mod.common.command.HeldItemCommand
import com.cobblemon.mod.common.command.LevelUp
import com.cobblemon.mod.common.command.NPCDeleteCommand
import com.cobblemon.mod.common.command.NPCEditCommand
import com.cobblemon.mod.common.command.OpenDialogueCommand
import com.cobblemon.mod.common.command.OpenStarterScreenCommand
import com.cobblemon.mod.common.command.PcCommand
import com.cobblemon.mod.common.command.PokeboxCommand
import com.cobblemon.mod.common.command.PokedexCommand
import com.cobblemon.mod.common.command.PokemonEditCommand
import com.cobblemon.mod.common.command.PokemonRestartCommand
import com.cobblemon.mod.common.command.QueryLearnsetCommand
import com.cobblemon.mod.common.command.ReloadShowdownCommand
import com.cobblemon.mod.common.command.RenameBoxCommand
import com.cobblemon.mod.common.command.RunMolangScriptCommand
import com.cobblemon.mod.common.command.ShowdownCommand
import com.cobblemon.mod.common.command.SpawnAllPokemon
import com.cobblemon.mod.common.command.SpawnNPCCommand
import com.cobblemon.mod.common.command.SpawnPokemon
import com.cobblemon.mod.common.command.SpawnPokemonFromPool
import com.cobblemon.mod.common.command.StopBattleCommand
import com.cobblemon.mod.common.command.TakePokemon
import com.cobblemon.mod.common.command.TeachCommand
import com.cobblemon.mod.common.command.TestCommand
import com.cobblemon.mod.common.command.TestPartySlotCommand
import com.cobblemon.mod.common.command.TestPcSlotCommand
import com.cobblemon.mod.common.command.TestStoreCommand
import com.mojang.brigadier.CommandDispatcher
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands

object CobblemonCommands {
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>, registry: CommandBuildContext, selection: Commands.CommandSelection) {
        SpawnPokemon.register(dispatcher)
        SpawnNPCCommand.register(dispatcher)
        GivePokemon.register(dispatcher)
        TakePokemon.register(dispatcher)
        ChangeEyeHeight.register(dispatcher)
        ChangeScaleAndSize.register(dispatcher)
        ChangeWalkSpeed.register(dispatcher)
        ChangeJointScaleCommand.register(dispatcher)
        TestCommand.register(dispatcher)
        ReloadShowdownCommand.register(dispatcher)
        ClickTextCommand.register(dispatcher)
        PokemonEditCommand.register(dispatcher)
        TeachCommand.register(dispatcher)
        LevelUp.register(dispatcher)
        HealPokemonCommand.register(dispatcher)
        StopBattleCommand.register(dispatcher)
        CheckSpawnsCommand.register(dispatcher)
        GetNBT.register(dispatcher)
        OpenStarterScreenCommand.register(dispatcher)
        SpawnAllPokemon.register(dispatcher)
        FriendshipCommand.register(dispatcher)
        GiveAllPokemon.register(dispatcher)
        HeldItemCommand.register(dispatcher, registry)
        PcCommand.register(dispatcher)
        SpawnPokemonFromPool.register(dispatcher)
        PokeboxCommand.register(dispatcher)
        PokedexCommand.register(dispatcher)
        TestStoreCommand.register(dispatcher)
        QueryLearnsetCommand.register(dispatcher)
        TestPcSlotCommand.register(dispatcher)
        TestPartySlotCommand.register(dispatcher)
        ClearPartyCommand.register(dispatcher)
        ClearPCCommand.register(dispatcher)
        PokemonRestartCommand.register(dispatcher)
        BedrockParticleCommand.register(dispatcher)
        OpenDialogueCommand.register(dispatcher)
        NPCEditCommand.register(dispatcher)
        NPCDeleteCommand.register(dispatcher)
        AbandonMultiTeam.register(dispatcher)
        FreezePokemonCommand.register(dispatcher)
        ApplyPlayerTextureCommand.register(dispatcher)
        RenameBoxCommand.register(dispatcher)
        ChangeBoxWallpaperCommand.register(dispatcher)
        RunMolangScriptCommand.register(dispatcher)
        CobblemonConfigCommand.register(dispatcher)

        // Possibly lock down registration if and only if under dev environment or running in an environment
        // with a certain system environment variable set
        CobblemonInfoCommand.register(dispatcher)
        ShowdownCommand.register(dispatcher)
    }
}