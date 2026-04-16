/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.storage

import com.cobblemon.mod.common.Cobblemon.LOGGER
import com.cobblemon.mod.common.api.reactive.SimpleObservable
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.DataKeys
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import java.util.*

class BottomlessPosition(val currentIndex: Int) : StorePosition

/**
 * A [PokemonStore] that has no maximum capacity. It's used internally as an overflow store.
 *
 * @author Hiroku
 * @since May 2nd, 2022
 */
open class BottomlessStore(override val uuid: UUID) : PokemonStore<BottomlessPosition>() {
    val pokemon = mutableListOf<Pokemon>()
    val storeChangeObservable = SimpleObservable<Unit>()

    override fun iterator() = pokemon.iterator()

    override fun get(position: BottomlessPosition) = position.currentIndex
        .takeIf { it in pokemon.indices }
        ?.let { pokemon[it] }

    override fun getFirstAvailablePosition() = BottomlessPosition(pokemon.size)
    override fun isValidPosition(position: BottomlessPosition) = position.currentIndex >= 0
    operator fun get(index: Int) = index.takeIf { it in pokemon.indices }?.let { pokemon[it] }
    override fun getObservingPlayers() = emptySet<ServerPlayer>()
    override fun sendTo(player: ServerPlayer) {}

    override fun initialize() {
        pokemon.forEachIndexed { index, pokemon ->
            pokemon.storeCoordinates.set(StoreCoordinates(this, BottomlessPosition(index)))
        }
    }

    override fun save(valueOutput: ValueOutput): ValueOutput {
        pokemon.forEachIndexed { index, pokemon -> valueOutput.store(DataKeys.STORE_SLOT + index, Pokemon.CODEC, pokemon) }
        return valueOutput
    }

    override fun load(valueInput: ValueInput): BottomlessStore {
        var i = -1
        while (valueInput.child(DataKeys.STORE_SLOT + ++i).isPresent) {
            try {
                pokemon.add(valueInput.read(DataKeys.STORE_SLOT + i, Pokemon.CODEC).get())
            } catch(_: InvalidSpeciesException) {
                handleInvalidSpecies(valueInput.child(DataKeys.STORE_SLOT + i).get())
            } catch (e: Exception) {
                LOGGER.error("Failed to read a pokémon: ${valueInput.child(DataKeys.STORE_SLOT + i)}", e)
            }
        }
        return this
    }

    override fun getAnyChangeObservable() = storeChangeObservable

    override fun setAtPosition(position: BottomlessPosition, pokemon: Pokemon?) {
        if (position.currentIndex == this.pokemon.size && pokemon != null) {
            this.pokemon.add(pokemon)
            storeChangeObservable.emit(Unit)
        } else if (position.currentIndex in 0 until this.pokemon.size) {
            var startIndex = position.currentIndex
            if (pokemon != null) {
                this.pokemon.add(position.currentIndex, pokemon)
                startIndex += 1
            } else {
                this.pokemon.removeAt(position.currentIndex)
            }
            for (i in startIndex until this.pokemon.size) {
                this.pokemon[i].storeCoordinates.set(StoreCoordinates(this, BottomlessPosition(i)))
            }
            storeChangeObservable.emit(Unit)
        }
    }

    override fun onPokemonChanged(pokemon: Pokemon) {
        this.storeChangeObservable.emit(Unit)
    }
}
