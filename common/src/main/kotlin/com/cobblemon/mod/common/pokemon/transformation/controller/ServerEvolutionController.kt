/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.transformation.controller

import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.pokemon.evolution.EvolutionAcceptedEvent
import com.cobblemon.mod.common.net.messages.client.pokemon.update.evolution.AddEvolutionPacket
import com.cobblemon.mod.common.net.messages.client.pokemon.update.evolution.ClearEvolutionsPacket
import com.cobblemon.mod.common.net.messages.client.pokemon.update.evolution.RemoveEvolutionPacket
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.transformation.evolution.Evolution

/**
 * Controller for managing evolutions on the server. These are data objects in the form of [Evolution]s.
 *
 * @author Licious
 * @since April 28th, 2022
 */
class ServerEvolutionController(
    private val pokemon: Pokemon,
    evolutionIds: Set<String>,
) : ServerTransformationController<Evolution>(pokemon, evolutionIds) {

    init {
        val potentialEvolutions = this.pokemon.evolutions.map { it.id }.toSet()
        this.transformationIds.removeIf { !potentialEvolutions.contains(it.lowercase()) }
        this.transformationIds.forEach { this.findEvolutionFromId(it)?.let(this::silentAdd) }
    }

    private fun findEvolutionFromId(id: String): Evolution? = this.pokemon.evolutions.firstOrNull { evolution -> evolution.id.equals(id, true) }
    override fun addPacket(transformation: Evolution) = AddEvolutionPacket(this.pokemon, transformation)
    override fun clearPacket() = ClearEvolutionsPacket { this.pokemon }
    override fun removePacket(transformation: Evolution) = RemoveEvolutionPacket(this.pokemon, transformation)
    override fun isDuplicate(element: Evolution) = this.find { element.result.matches(it.result) } != null

    override fun start(transformation: Evolution) {
        if (!canStart(transformation)) return
        CobblemonEvents.EVOLUTION_ACCEPTED.postThen(
            event = EvolutionAcceptedEvent(this.pokemon, transformation),
            ifSucceeded = { transformation.perform(this.pokemon) }
        )
    }
}