/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.storage.egg

import com.cobblemon.mod.common.pokemon.Pokemon
import java.util.UUID
import net.minecraft.server.MinecraftServer

/**
 * Backend interface for storing egg data (UUID -> Pokemon).
 * Implementations can use NBT flatfiles, JSON flatfiles, or MongoDB.
 * All operations are blocking; async dispatch is handled by [EggStoreManager].
 */
interface EggStoreBackend {
    fun setup(server: MinecraftServer)
    fun load(eggId: UUID): Pokemon?
    fun save(eggId: UUID, pokemon: Pokemon)
    fun delete(eggId: UUID)
}
