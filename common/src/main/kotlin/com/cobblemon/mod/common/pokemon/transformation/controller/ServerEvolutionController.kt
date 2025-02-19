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
import com.cobblemon.mod.common.pokemon.transformation.evolution.Evolution
import com.cobblemon.mod.common.api.pokemon.transformation.proxy.PreProcessor
import com.cobblemon.mod.common.api.pokemon.transformation.progress.TransformationProgress
import com.cobblemon.mod.common.api.pokemon.transformation.progress.TransformationProgressTypes
import com.cobblemon.mod.common.net.messages.client.pokemon.update.evolution.AddEvolutionPacket
import com.cobblemon.mod.common.net.messages.client.pokemon.update.evolution.ClearEvolutionsPacket
import com.cobblemon.mod.common.net.messages.client.pokemon.update.evolution.RemoveEvolutionPacket
import com.cobblemon.mod.common.pokemon.Pokemon
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

/**
 * Controller for managing evolutions on the server. These are data objects in the form of [Evolution]s.
 *
 * @author Licious
 * @since April 28th, 2022
 */
class ServerEvolutionController(
    private val pokemon: Pokemon,
    evolutionIds: Set<String>,
    progress: Set<TransformationProgress<*>>,
) : CobblemonServerTransformationController<Evolution, ServerEvolutionController.Intermediate>(pokemon, evolutionIds, progress) {

    init {
        val potentialEvolutions = this.pokemon.evolutions.map { it.id }.toSet()
        this.transformationIds.removeIf { !potentialEvolutions.contains(it.lowercase()) }
        this.transformationIds.forEach { this.findEvolutionFromId(it)?.let(this::silentAdd) }
    }

    private fun findEvolutionFromId(id: String): Evolution? = this.pokemon.evolutions.firstOrNull { evolution -> evolution.id.equals(id, true) }

    override fun addPacket(evolution: Evolution) = AddEvolutionPacket(this.pokemon, evolution)

    override fun clearPacket() = ClearEvolutionsPacket { this.pokemon }

    override fun removePacket(evolution: Evolution) = RemoveEvolutionPacket(this.pokemon, evolution)

    override fun start(evolution: Evolution) {
        CobblemonEvents.EVOLUTION_ACCEPTED.postThen(
            event = EvolutionAcceptedEvent(this.pokemon, evolution),
            ifSucceeded = {
                // Evolution will clear the pending stuff after if successful
                super.start(evolution)
            }
        )
    }

    override fun isDuplicate(element: Evolution) = this.find { element.result.matches(it.result) } != null

    override fun asIntermediate(): Intermediate = Intermediate(this.transformations.map { it.id }.toSet(), this.progress)

    data class Intermediate(
        val evolutionIds: Set<String>,
        val progress: Set<TransformationProgress<*>>,
    ): PreProcessor {
        override fun create(pokemon: Pokemon): ServerEvolutionController = ServerEvolutionController(
            pokemon,
            this.evolutionIds,
            this.progress
        )
    }

    companion object {
        private const val PENDING = "pending"
        private const val PROGRESS = "progress"

        @JvmStatic
        val CODEC: Codec<Intermediate> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.list(Codec.STRING).fieldOf(PENDING).forGetter { controller -> controller.evolutionIds.toMutableList() },
                Codec.list(TransformationProgressTypes.codec()).fieldOf(PROGRESS).forGetter { controller -> controller.progress.toMutableList() }
            ).apply(instance) { evolutions, progress -> Intermediate(evolutions.toSet(), progress.toSet()) }
        }
    }
}