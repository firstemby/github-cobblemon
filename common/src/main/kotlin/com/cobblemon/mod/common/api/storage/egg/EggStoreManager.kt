/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.storage.egg

import com.cobblemon.mod.common.Cobblemon.MODID
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.server
import com.google.common.util.concurrent.ThreadFactoryBuilder
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import net.minecraft.server.MinecraftServer

/**
 * Manages egg data storage, mapping egg UUIDs to serialized Pokemon instances.
 * All operations are dispatched asynchronously through a single-thread executor
 * to avoid blocking the main server thread. No caching is used so that multiple
 * servers sharing the same backend always read the latest state.
 */
class EggStoreManager(private val backend: EggStoreBackend) {
    val executor: ExecutorService = Executors.newSingleThreadExecutor(
        ThreadFactoryBuilder()
            .setNameFormat("$MODID Egg Store Executor")
            .setDaemon(true)
            .build()
    )

    fun setup(server: MinecraftServer) {
        backend.setup(server)
    }

    fun get(eggId: UUID): CompletableFuture<Pokemon?> = CompletableFuture.supplyAsync({ backend.load(eggId) }, executor)
    fun add(eggId: UUID, pokemon: Pokemon): CompletableFuture<Void> =
        CompletableFuture.runAsync({ backend.save(eggId, pokemon) }, executor)

    fun remove(eggId: UUID): CompletableFuture<Void> = CompletableFuture.runAsync({ backend.delete(eggId) }, executor)

    fun hatch(eggId: UUID, then: (Pokemon?) -> Unit) = get(eggId).handle { pokemon, _ ->
        server()?.execute {
            then(pokemon)
            remove(eggId)
        }
    }

    fun shutdown() {
        executor.shutdown()
        executor.awaitTermination(30L, TimeUnit.SECONDS)
    }
}
