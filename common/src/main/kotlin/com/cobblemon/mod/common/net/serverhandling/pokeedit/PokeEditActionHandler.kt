/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.serverhandling.pokeedit

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.moves.MoveSet
import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.api.permission.CobblemonPermissions
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.text.red
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.CobblemonCosmeticItems
import com.cobblemon.mod.common.net.messages.server.pokeedit.PokeEditAction
import com.cobblemon.mod.common.net.messages.server.pokeedit.PokeEditActionPacket
import com.cobblemon.mod.common.util.commandLang
import com.cobblemon.mod.common.util.lang
import com.cobblemon.mod.common.util.toBlockPos
import net.minecraft.network.chat.Component
import net.minecraft.commands.arguments.item.ItemParser
import com.mojang.brigadier.StringReader
import net.minecraft.world.item.ItemStack
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.MobSpawnType
import net.minecraft.world.level.Level

object PokeEditActionHandler : ServerNetworkPacketHandler<PokeEditActionPacket> {
    override fun handle(packet: PokeEditActionPacket, server: MinecraftServer, player: ServerPlayer) {
        val properties = PokemonProperties.parse(packet.properties)
        if (properties.species == null) {
            player.sendSystemMessage(commandLang("spawnpokemon.nospecies").red())
            return
        }

        when (packet.action) {
            PokeEditAction.CREATE -> {
                if (!Cobblemon.permissionValidator.hasPermission(player, CobblemonPermissions.GIVE_POKEMON_SELF)) {
                    player.sendSystemMessage(lang("requires_permission").red())
                    return
                }
                val pokemon = properties.create(player)
                applyMovesIfProvided(pokemon, packet.moves)
                applyCosmeticIfProvided(pokemon, packet.cosmeticItem, server, player)
                Cobblemon.storage.getParty(player).add(pokemon)
            }
            PokeEditAction.SPAWN -> {
                if (!Cobblemon.permissionValidator.hasPermission(player, CobblemonPermissions.SPAWN_POKEMON)) {
                    player.sendSystemMessage(lang("requires_permission").red())
                    return
                }
                val world = player.serverLevel()
                val pos = player.position()
                val blockPos = pos.toBlockPos()
                if (!Level.isInSpawnableBounds(blockPos)) {
                    player.sendSystemMessage(Component.literal("Invalid position").red())
                    return
                }
                try {
                    val pokemonEntity = properties.createEntity(world, player)
                    applyMovesIfProvided(pokemonEntity.pokemon, packet.moves)
                    applyCosmeticIfProvided(pokemonEntity.pokemon, packet.cosmeticItem, server, player)
                    pokemonEntity.moveTo(pos.x, pos.y, pos.z, pokemonEntity.yRot, pokemonEntity.xRot)
                    pokemonEntity.entityData.set(PokemonEntity.SPAWN_DIRECTION, pokemonEntity.random.nextFloat() * 360F)
                    pokemonEntity.finalizeSpawn(world, world.getCurrentDifficultyAt(blockPos), MobSpawnType.COMMAND, null)
                    world.addFreshEntity(pokemonEntity)
                } catch (e: Exception) {
                    e.printStackTrace()
                    player.sendSystemMessage(Component.literal("Unable to spawn at the given position").red())
                }
            }
        }
    }

    private fun applyMovesIfProvided(pokemon: com.cobblemon.mod.common.pokemon.Pokemon, moves: List<String>) {
        if (moves.none { it.isNotBlank() }) return
        val templates = moves.take(MoveSet.MOVE_COUNT).map { name ->
            if (name.isBlank()) null else Moves.getByName(name)
        }
        pokemon.moveSet.doWithoutEmitting {
            for (i in 0 until MoveSet.MOVE_COUNT) {
                pokemon.moveSet.setMove(i, null)
            }
            templates.forEachIndexed { index, template ->
                template?.let { pokemon.moveSet.setMove(index, it.create()) }
            }
        }
        pokemon.moveSet.update()
    }

    private fun applyCosmeticIfProvided(
        pokemon: com.cobblemon.mod.common.pokemon.Pokemon,
        cosmeticItem: String,
        server: MinecraftServer,
        player: ServerPlayer
    ) {
        val trimmed = cosmeticItem.trim()
        if (trimmed.isEmpty()) return

        val parser = ItemParser(server.registryAccess())
        val result = try {
            parser.parse(StringReader(trimmed))
        } catch (_: Exception) {
            player.sendSystemMessage(Component.literal("Invalid cosmetic item: $trimmed").red())
            return
        }

        val stack = ItemStack(result.item)
        stack.applyComponents(result.components)
        if (stack.isEmpty) return

        val isValid = CobblemonCosmeticItems.findValidCosmeticForPokemonAndItem(
            server.registryAccess(),
            pokemon,
            stack
        ) != null
        if (!isValid) {
            player.sendSystemMessage(Component.literal("Item is not a valid cosmetic for this pokemon.").red())
            return
        }

        pokemon.swapCosmeticItem(stack, decrement = false)
    }
}
