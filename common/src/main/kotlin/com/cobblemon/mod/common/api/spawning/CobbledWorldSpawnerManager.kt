/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.spawning

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.spawning.spawner.PlayerSpawner
import com.cobblemon.mod.common.api.spawning.spawner.PlayerSpawnerFactory
import com.cobblemon.mod.common.platform.events.PlatformEvents
import com.cobblemon.mod.common.util.server
import com.cobblemon.mod.common.world.gamerules.CobblemonGameRules
import net.minecraft.server.level.ServerLevel
import java.util.UUID
import net.minecraft.server.level.ServerPlayer

/**
 * The [SpawnerManager] that handles Cobblemon's world spawner, which is made up
 * of [PlayerSpawner] instances. This manager listens for login and logout and
 * cleans up where relevant.
 *
 * @author Hiroku
 * @since February 14th, 2022
 */
object CobblemonWorldSpawnerManager : SpawnerManager() {

    var spawnersForPlayers = mutableMapOf<UUID, PlayerSpawner>()

    init {
        PlatformEvents.SERVER_PLAYER_LOGIN.subscribe { this.onPlayerLogin(it.player) }
        PlatformEvents.SERVER_PLAYER_LOGOUT.subscribe { this.onPlayerLogout(it.player) }
        PlatformEvents.CHANGE_DIMENSION.subscribe { this.onDimensionChange(it.player, it.origin, it.destination) }
    }

    fun onPlayerLogin(player: ServerPlayer) {
        // Disables spawning
        if (!Cobblemon.config.enableSpawning || player.level().gameRules.getBoolean(CobblemonGameRules.DO_POKEMON_SPAWNING) == false) {
            return;
        }

        registerPlayerSpawner(player)
    }

    fun onPlayerLogout(player: ServerPlayer) {
        unregisterPlayerSpawner(player)
    }

    fun onDimensionChange(player: ServerPlayer, origin: ServerLevel?, destination: ServerLevel?) {
        fun getSpawnRule(level: ServerLevel?) = level?.gameRules?.getBoolean(CobblemonGameRules.DO_POKEMON_SPAWNING)

        if (!Cobblemon.config.enableSpawning || getSpawnRule(origin) == getSpawnRule(destination)) {
            return
        }

        val spawnerAction = if (destination?.gameRules?.getBoolean(CobblemonGameRules.DO_POKEMON_SPAWNING) == false) {
            ::unregisterPlayerSpawner
        } else {
            ::registerPlayerSpawner
        }
        spawnerAction(player)
    }

    private fun unregisterPlayerSpawner(player: ServerPlayer) {
        spawnersForPlayers.remove(player.uuid)?.let { unregisterSpawner(it) }
    }

    private fun registerPlayerSpawner(player: ServerPlayer) {
        PlayerSpawnerFactory.create(this, player).also {
            spawnersForPlayers[player.uuid] = it
            registerSpawner(it)
        }
    }
}