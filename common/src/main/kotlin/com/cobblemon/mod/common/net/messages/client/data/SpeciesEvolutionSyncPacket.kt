/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.data

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.pokemon.evolution.Evolution
import com.cobblemon.mod.common.api.pokemon.evolution.PreEvolution
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Species
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.readIdentifier
import com.cobblemon.mod.common.util.readString
import com.cobblemon.mod.common.util.writeIdentifier
import com.cobblemon.mod.common.util.writeString
import com.google.gson.reflect.TypeToken
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

/**
 * Carries the evolutions/preEvolution of every [Species] (and any [FormData] overrides), sent right after
 * [SpeciesRegistrySyncPacket] so that every species is already registered by the time this is decoded. This avoids
 * the cross-species lookup ordering hazards that come with resolving evolutions during the species sync itself.
 */
class SpeciesEvolutionSyncPacket(entries: Collection<SpeciesEvolutionEntry>) : DataRegistrySyncPacket<SpeciesEvolutionEntry, SpeciesEvolutionSyncPacket>(entries) {

    override val id = ID

    override fun encodeEntry(buffer: RegistryFriendlyByteBuf, entry: SpeciesEvolutionEntry) {
        try {
            buffer.writeIdentifier(entry.speciesIdentifier)
            buffer.writeString(PokemonSpecies.gson.toJson(entry.evolutions, EVOLUTION_SET_TYPE))
            buffer.writeNullable(entry.preEvolution) { _, preEvolution -> buffer.writeString(PokemonSpecies.gson.toJson(preEvolution, PreEvolution::class.java)) }
            buffer.writeCollection(entry.formOverrides) { _, override ->
                buffer.writeString(override.formName)
                buffer.writeNullable(override.evolutions) { _, evolutions -> buffer.writeString(PokemonSpecies.gson.toJson(evolutions, EVOLUTION_SET_TYPE)) }
                buffer.writeNullable(override.preEvolution) { _, preEvolution -> buffer.writeString(PokemonSpecies.gson.toJson(preEvolution, PreEvolution::class.java)) }
            }
        } catch (e: Exception) {
            Cobblemon.LOGGER.error("Caught exception encoding evolution data for species {}", entry.speciesIdentifier, e)
        }
    }

    override fun decodeEntry(buffer: RegistryFriendlyByteBuf): SpeciesEvolutionEntry? {
        val identifier = buffer.readIdentifier()
        return try {
            val evolutions: MutableSet<Evolution> = PokemonSpecies.gson.fromJson(buffer.readString(), EVOLUTION_SET_TYPE)
            val preEvolution = buffer.readNullable { _ -> PokemonSpecies.gson.fromJson(buffer.readString(), PreEvolution::class.java) }
            val formOverrides = buffer.readList { _ ->
                val formName = buffer.readString()
                val formEvolutions = buffer.readNullable { _ -> PokemonSpecies.gson.fromJson<MutableSet<Evolution>>(buffer.readString(), EVOLUTION_SET_TYPE) }
                val formPreEvolution = buffer.readNullable { _ -> PokemonSpecies.gson.fromJson(buffer.readString(), PreEvolution::class.java) }
                FormEvolutionOverride(formName, formEvolutions, formPreEvolution)
            }
            SpeciesEvolutionEntry(identifier, evolutions, preEvolution, formOverrides)
        } catch (e: Exception) {
            Cobblemon.LOGGER.error("Caught exception decoding evolution data for species {}", identifier, e)
            null
        }
    }

    override fun synchronizeDecoded(entries: Collection<SpeciesEvolutionEntry>) {
        entries.forEach { entry ->
            val species = PokemonSpecies.getByIdentifier(entry.speciesIdentifier)
            if (species == null) {
                Cobblemon.LOGGER.error("Received evolution data for unknown species {}", entry.speciesIdentifier)
                return@forEach
            }
            species.applyEvolutionData(entry.evolutions, entry.preEvolution)
            entry.formOverrides.forEach { override ->
                species.getFormByName(override.formName).applyEvolutionData(override.evolutions, override.preEvolution)
            }
        }
    }

    companion object {
        val ID = cobblemonResource("species_evolution_sync")
        private val EVOLUTION_SET_TYPE = TypeToken.getParameterized(MutableSet::class.java, Evolution::class.java).type
        fun decode(buffer: RegistryFriendlyByteBuf): SpeciesEvolutionSyncPacket = SpeciesEvolutionSyncPacket(emptyList()).apply { decodeBuffer(buffer) }
    }
}

class SpeciesEvolutionEntry(
    val speciesIdentifier: ResourceLocation,
    val evolutions: MutableSet<Evolution>,
    val preEvolution: PreEvolution?,
    val formOverrides: List<FormEvolutionOverride>
)

class FormEvolutionOverride(
    val formName: String,
    val evolutions: MutableSet<Evolution>?,
    val preEvolution: PreEvolution?
)
