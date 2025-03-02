/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.moves

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.data.DataRegistry
import com.cobblemon.mod.common.api.reactive.SimpleObservable
import com.cobblemon.mod.common.net.messages.client.data.MovesRegistrySyncPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.resources.ResourceManager

/**
 * Registry for all known Moves
 */
object Moves : DataRegistry {

    override val id = cobblemonResource("moves")
    override val type = PackType.SERVER_DATA
    override val observable = SimpleObservable<Moves>()

    private val allMoves = mutableMapOf<String, MoveTemplate>()
    private val idMapping = mutableMapOf<Int, MoveTemplate>()

    override fun reload(manager: ResourceManager) {
        this.allMoves.clear()
        this.idMapping.clear()
        Cobblemon.showdownServer.getMoves().forEach(::register)
        Cobblemon.LOGGER.info("Loaded {} moves", this.allMoves.size)
        this.observable.emit(this)
    }

    override fun sync(player: ServerPlayer) {
        MovesRegistrySyncPacket(all()).sendToPlayer(player)
    }

    fun getByName(name: String) = allMoves[name.lowercase()]
    fun getByNumericalId(id: Int) = idMapping[id]
    fun getByNameOrDummy(name: String) = allMoves[name.lowercase()] ?: MoveTemplate.dummy(name.lowercase())
    fun getExceptional() = getByName("tackle") ?: allMoves.values.random()
    fun count() = allMoves.size
    fun names(): Collection<String> = this.allMoves.keys.toSet()
    fun all() = this.allMoves.values.toList()

    internal fun receiveSyncPacket(moves: Collection<MoveTemplate>) {
        moves.forEach(this::register)
    }

    private fun register(move: MoveTemplate) {
        this.allMoves[move.name] = move
        this.idMapping[move.num] = move
    }

}