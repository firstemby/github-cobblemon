/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.command

import com.cobblemon.mod.common.api.permission.CobblemonPermissions
import com.cobblemon.mod.common.api.spawning.CobblemonWorldSpawnerManager
import com.cobblemon.mod.common.api.spawning.SpawnCause
import com.cobblemon.mod.common.api.spawning.detail.EntitySpawnResult
import com.cobblemon.mod.common.api.spawning.position.AreaSpawnablePosition
import com.cobblemon.mod.common.api.text.green
import com.cobblemon.mod.common.api.text.red
import com.cobblemon.mod.common.util.alias
import com.cobblemon.mod.common.util.commandLang
import com.cobblemon.mod.common.util.effectiveName
import com.cobblemon.mod.common.util.permission
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.Commands.literal

/**
 * Spawn Pokemon From Surrounding Pool
 *
 * `/spawnpokemonfrompool [amount]` or the alias `/forcespawn [amount]`
 *
 * This command can fail if the randomly selection spawn region has no possible [AreaSpawnablePosition]. For example if
 *   you are flying in the air
 */
object SpawnPokemonFromPool {
    const val NAME = "spawnpokemonfrompool"
    const val ALIAS = "forcespawn"

    private val UNABLE_TO_SPAWN = commandLang("spawnpokemonfrompool.unable_to_spawn")

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        val spawnPokemonFromPoolCommand = dispatcher.register(literal(NAME)
            .permission(CobblemonPermissions.SPAWN_POKEMON)
            .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                .executes { context -> execute(context, IntegerArgumentType.getInteger(context, "amount")) }
            )
            .executes { context -> execute(context, 1) }
        )

        dispatcher.register(spawnPokemonFromPoolCommand.alias(ALIAS))
    }

    private fun execute(context: CommandContext<CommandSourceStack>, amount: Int): Int {
        val player = context.source.playerOrException
        val spawner = CobblemonWorldSpawnerManager.spawnersForPlayers.getValue(player.uuid)

        var spawnsTriggered = 0

        // This could instead directly use a [Spawner] method if refactored, as it is currently it has
        //   entity counting coupled to the generation of an entity to spawn. Might be a good future change?
        for (i in 1..amount) {
            val spawnCause = SpawnCause(spawner = spawner, entity = spawner.getCauseEntity())

            val zoneInput = spawner.getZoneInput(spawnCause) ?: continue
            val zone = spawner.spawningZoneGenerator.generate(spawner, zoneInput)
            val contexts = spawner.spawnablePositionResolver.resolve(spawner, spawner.spawnablePositionCalculators, zone)
            val influences = spawner.getAllInfluences() + zone.unconditionalInfluences

            // This has a chance to fail, if you get a spawning zone that has no associated contexts.
            //   but as it was selected at random by the Spawning Zone Generator, it could just be a miss which
            //   means two attempts to spawn in the same location can have differing results (which is expected for
            //   randomness).
            if (contexts.isEmpty()) {
                player.sendSystemMessage(UNABLE_TO_SPAWN.red())
                continue
            }

            val bucket = spawner.chooseBucket(spawnCause, influences)

            val spawnAction = spawner.getSpawningSelector().select(spawner, bucket, contexts, max = 1).firstOrNull() // one at a time
            if (spawnAction == null) {
                player.sendSystemMessage(UNABLE_TO_SPAWN.red())
                continue
            }

            spawnAction.future.thenApply {
                if (it is EntitySpawnResult) {
                    for (entity in it.entities) {
                        player.sendSystemMessage(commandLang("spawnpokemonfrompool.success", entity.effectiveName()).green())
                    }
                }
            }

            spawnAction.complete()
            spawnsTriggered++
        }

        return spawnsTriggered
    }
}