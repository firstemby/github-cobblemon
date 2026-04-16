/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.storage.pc

import com.cobblemon.mod.common.Cobblemon.LOGGER
import com.cobblemon.mod.common.api.pokemon.PokemonSortMode
import com.cobblemon.mod.common.api.reactive.SimpleObservable
import com.cobblemon.mod.common.api.storage.InvalidSpeciesException
import com.cobblemon.mod.common.api.storage.StoreCoordinates
import com.cobblemon.mod.common.net.messages.client.storage.pc.SetPCBoxPacket
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.DataKeys
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.core.RegistryAccess
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput

/**
 * A single box of a PC. The list of Pokémon is strictly sized at [POKEMON_PER_BOX] - 30.
 * Any change to any contained Pokémon is emitted through the [boxChangeEmitter].
 *
 * @author Hiroku
 * @since April 26th, 2022
 */
open class PCBox(val pc: PCStore) : Iterable<Pokemon> {
    override fun iterator() = pokemon.filterNotNull().iterator()

    val boxChangeEmitter = SimpleObservable<Unit>()

    protected var emit = true

    var name : String? = null
        set(value) {
            field = value
            if (emit) boxChangeEmitter.emit(Unit)
        }

    var wallpaper : Identifier = cobblemonResource("textures/gui/pc/pc_screen_overlay.png")
        set(value) {
            field = value
            if (emit) boxChangeEmitter.emit(Unit)
        }

    protected val pokemon = Array<Pokemon?>(POKEMON_PER_BOX) { null }

    open operator fun get(index: Int): Pokemon? {
        return if (index in 0 until POKEMON_PER_BOX) {
            pokemon[index]
        } else {
            null
        }
    }

    open operator fun set(index: Int, pokemon: Pokemon?) {
        if (index in 0 until POKEMON_PER_BOX) {
            this.pokemon[index] = pokemon
            pokemon?.storeCoordinates?.set(StoreCoordinates(pc, PCPosition(boxNumber, index)))
            if (emit) {
                boxChangeEmitter.emit(Unit)
            }
        }
    }

    val boxNumber: Int
        get() = this.pc.boxes.indexOf(this)

    val unoccupiedSlots: Int
        get() = POKEMON_PER_BOX - this.pokemon.filterNotNull().count()

    fun getFirstAvailablePosition(): PCPosition? {
        for (index in 0 until POKEMON_PER_BOX) {
            if (this.pokemon[index] == null) {
                return PCPosition(boxNumber, index)
            }
        }
        return null
    }

    open fun initialize() {
        val box = boxNumber
        pokemon.forEachIndexed { slot, pokemon ->
            if (pokemon != null) {
                val position = PCPosition(box, slot)
                pokemon.storeCoordinates.set(StoreCoordinates(pc, position))
            }
        }
        boxChangeEmitter.subscribe { pc.pcChangeObservable.emit(Unit) }
    }

    fun sort(sortMode: PokemonSortMode, descending: Boolean) {
        pokemon.sortWith(sortMode.comparator(descending))
        pokemon.forEachIndexed { slot, pokemon ->
            pokemon?.storeCoordinates?.set(StoreCoordinates(pc, PCPosition(boxNumber, slot)))
        }
        boxChangeEmitter.emit(Unit)
    }

    fun sendTo(player: ServerPlayer) {
        SetPCBoxPacket(this).sendToPlayer(player)
    }

    open fun save(valueOutput: ValueOutput): ValueOutput {
        name?.let {
            valueOutput.putString(DataKeys.STORE_BOX_NAME, it)
        }
        valueOutput.putString(DataKeys.STORE_BOX_WALLPAPER, wallpaper.toString())
        for (slot in 0 until POKEMON_PER_BOX) {
            val pokemon = pokemon[slot] ?: continue
            valueOutput.store(DataKeys.STORE_SLOT + slot, Pokemon.CODEC, pokemon)
        }
        return valueOutput
    }

    open fun load(valueInput: ValueInput): PCBox {
        name = valueInput.getStringOr(DataKeys.STORE_BOX_NAME, "").takeIf { it.isNotEmpty() }

        if (valueInput.getString(DataKeys.STORE_BOX_WALLPAPER).isPresent) {
            wallpaper = Identifier.parse(valueInput.getString(DataKeys.STORE_BOX_WALLPAPER).get())
        }

        for (slot in 0 until POKEMON_PER_BOX) {
            if (valueInput.child(DataKeys.STORE_SLOT + slot).isPresent) {
                try {
                    pokemon[slot] = valueInput.read(DataKeys.STORE_SLOT + slot, Pokemon.CODEC).get()
                } catch (_: InvalidSpeciesException) {
                    pc.handleInvalidSpecies(valueInput.child(DataKeys.STORE_SLOT + slot).get())
                } catch (e: Exception) {
                    LOGGER.error("Failed to read a pokémon: ${valueInput.child(DataKeys.STORE_SLOT + slot).get()}", e)
                }
            }
        }
        return this
    }

    open fun getNonEmptySlots() = (0 until POKEMON_PER_BOX).filter { get(it) != null }.associateWith { get(it)!! }
    open fun getNonEmptySlotsForPackets() = getNonEmptySlots().mapValues { (_, pokemon) -> { _: RegistryAccess -> pokemon } }
}
