/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.storage.player

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.storage.player.client.ClientPokedexManager
import com.cobblemon.mod.common.api.storage.player.client.ClientGeneralPlayerData
import com.cobblemon.mod.common.api.storage.player.client.ClientTMMoveManager
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.resources.ResourceLocation

object PlayerInstancedDataStoreTypes {
    val types = mutableMapOf<ResourceLocation, PlayerInstancedDataStoreType>()

    val GENERAL = register(PlayerInstancedDataStoreType(
        cobblemonResource("general"),
        ClientGeneralPlayerData::decode,
        ClientGeneralPlayerData::runAction
    ))
    val POKEDEX = register(PlayerInstancedDataStoreType(
        cobblemonResource("pokedex"),
        ClientPokedexManager::decode,
        ClientPokedexManager::runAction,
        ClientPokedexManager::runIncremental
    ))
    val TM_MOVES = register(PlayerInstancedDataStoreType(
        cobblemonResource("tm_moves"),
        ClientTMMoveManager::decode,
        ClientTMMoveManager::runAction,
        ClientTMMoveManager::runIncremental
    ))

    fun register(type: PlayerInstancedDataStoreType): PlayerInstancedDataStoreType {
        Cobblemon.LOGGER.debug("Registering PlayerInstancedDataStoreType: {}", type.id)
        types[type.id] = type
        return type
    }

    fun getTypeById(id: ResourceLocation): PlayerInstancedDataStoreType? {
        val type = types[id]
        if (type == null) Cobblemon.LOGGER.warn("Unknown PlayerInstancedDataStoreType ID: {}", id)
        return type
    }
}
