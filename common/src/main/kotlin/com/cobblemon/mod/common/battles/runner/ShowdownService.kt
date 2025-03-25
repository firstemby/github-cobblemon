/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battles.runner

import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.battles.runner.graal.GraalShowdownService
import com.cobblemon.mod.common.battles.runner.remote.UnionShowdownService
import com.cobblemon.mod.common.util.cobblemonResource
import com.google.gson.JsonArray
import net.minecraft.resources.ResourceLocation
import java.util.UUID

/**
 * Mediator service for communicating between the Cobblemon Minecraft mod and Cobblemon showdown service.
 *
 * All outgoing calls to showdown should be done through this layer to decouple the mod from a specific implementation.
 * To get an instance of the service, use the singleton provided via `ShowdownService.get()`.
 *
 * @since February 27th, 2023
 * @author landonjw
 */
interface ShowdownService {
    /**
     * The unique identifier for this showdown service.
     */
    val id: ResourceLocation

    /**
     * The current status of the showdown service.
     */
    val status: ShowdownStatus

    /**
     * The log of messages from the showdown service.
     * This is typically used for debugging purposes and may be null if not in a debug environment.
     */
    val log: List<String>?

    /**
     * Opens a connection to the showdown service.
     */
    fun openConnection()

    /**
     * Closes the connection to the showdown service.
     */
    fun closeConnection()

    /**
     * Restarts the connection to the showdown service.
     */
    fun restartConnection()

    /**
     * Starts a battle in the showdown service.
     *
     * @param battle The battle to start.
     * @param messages The initial messages for the battle.
     */
    fun startBattle(battle: PokemonBattle, messages: Array<String>)

    /**
     * Sends messages to a specific battle in the showdown service.
     *
     * @param battleId The unique identifier of the battle.
     * @param messages The messages to send.
     */
    fun send(battleId: UUID, messages: Array<String>)

    /**
     * Sends a message to the showdown service.
     *
     * @param message The message to send.
     */
    fun send(message: String)

    /**
     * Retrieves the ability IDs from the showdown service.
     *
     * @return A `JsonArray` of ability IDs.
     */
    fun getAbilityIds(): JsonArray

    /**
     * Retrieves the moves from the showdown service.
     *
     * @return A `JsonArray` of moves.
     */
    fun getMoves(): JsonArray

    /**
     * Retrieves the item IDs from the showdown service.
     *
     * @return A `JsonArray` of item IDs.
     */
    fun getItemIds(): JsonArray

    /**
     * Registers species in the showdown service.
     */
    fun registerSpecies()

    /**
     * Registers bag items in the showdown service.
     */
    fun registerBagItems()

    /**
     * Indicates that species have been initialized in the showdown service.
     */
    fun indicateSpeciesInitialized() {}

    companion object {
        private val services = mutableMapOf<ResourceLocation, ShowdownService>(
            cobblemonResource("embedded") to GraalShowdownService(cobblemonResource("embedded")),
            cobblemonResource("union") to UnionShowdownService(cobblemonResource("union"))
        )

        /**
         * Retrieves the showdown service for the specified path.
         *
         * @param path The resource location of the showdown service.
         * @return The showdown service.
         * @throws IllegalStateException if no showdown service is found for the specified path.
         */
        fun get(path: ResourceLocation = cobblemonResource("embedded")): ShowdownService {
            return services[path] ?: error("No showdown service found for path $path")
        }

        /**
         * Checks if a showdown service exists for the specified path.
         *
         * @param path The resource location of the showdown service.
         * @return `true` if the showdown service exists, `false` otherwise.
         */
        fun contains(path: ResourceLocation): Boolean {
            return services.containsKey(path)
        }

        /**
         * Retrieves all available showdown services.
         *
         * @return A list of all showdown services.
         */
        fun all(): List<ShowdownService> {
            return services.values.toList()
        }
    }
}

/**
 * Enum representing the status of the showdown service.
 */
enum class ShowdownStatus {
    CONNECTED, DISCONNECTED, RESTARTING
}
