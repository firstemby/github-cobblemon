/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.pokemon.feature

import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.pokemon.aspect.AspectProvider
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.Species
import com.cobblemon.mod.common.util.asIdentifierDefaultingNamespace
import com.google.gson.JsonObject
import net.minecraft.nbt.CompoundTag

data class GrowthVariantStage(
    var minLevel: Int = 1,
    var aspect: String = ""
)

class GrowthVariantSpeciesFeatureProvider(
    var stages: List<GrowthVariantStage> = emptyList()
) : SpeciesFeatureProvider<SpeciesFeature>, AspectProvider {
    override fun invoke(pokemon: Pokemon): SpeciesFeature? = null
    override fun invoke(nbt: CompoundTag): SpeciesFeature? = null
    override fun invoke(json: JsonObject): SpeciesFeature? = null

    override fun provide(pokemon: Pokemon): Set<String> {
        if (!isAssignedTo(pokemon.species)) {
            return emptySet()
        }
        return aspectForLevel(pokemon.level)
    }

    override fun provide(properties: PokemonProperties): Set<String> {
        val speciesId = properties.species ?: return emptySet()
        val species = PokemonSpecies.getByIdentifier(speciesId.asIdentifierDefaultingNamespace()) ?: return emptySet()
        if (!isAssignedTo(species)) {
            return emptySet()
        }
        return aspectForLevel(properties.level ?: 1)
    }

    private fun isAssignedTo(species: Species): Boolean {
        return SpeciesFeatures.getFeaturesFor(species).contains(this)
    }

    private fun aspectForLevel(level: Int): Set<String> {
        val stage = stages.filter { level >= it.minLevel }.maxByOrNull { it.minLevel } ?: return emptySet()
        return if (stage.aspect.isNotEmpty()) setOf(stage.aspect) else emptySet()
    }
}
