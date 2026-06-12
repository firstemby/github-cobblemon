/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.pokemon.feature

import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.aspect.AspectProvider
import com.cobblemon.mod.common.api.properties.CustomPokemonPropertyType
import com.cobblemon.mod.common.client.gui.summary.featurerenderers.SummarySpeciesFeatureRenderer
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.substitute
import com.google.gson.JsonObject
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.RegistryFriendlyByteBuf

/**
 * An abstract [SpeciesFeatureProvider] for choice based features. It must be extended by feature provider
 * that implements any sort of choice based value selection.
 * Parameters can be overridden to change default behavior, aspects, and the available choices.
 *
 * @author Hiroku
 * @since April 9th, 2026
 */
abstract class AbstractChoiceSpeciesFeatureProvider<T>(
    override var keys: List<String>,
    open var choices: T,
    open var default: String? = null,
    open var isAspect: Boolean = true,
    open var aspectFormat: String = "{{choice}}",
) : SynchronizedSpeciesFeatureProvider<StringSpeciesFeature>,
    CustomPokemonPropertyType<StringSpeciesFeature>,
    AspectProvider {

    fun getAspect(feature: StringSpeciesFeature) = aspectFormat.substitute("choice", feature.value)

    abstract fun getAllAspects(): T

    override fun getRenderer(pokemon: Pokemon): SummarySpeciesFeatureRenderer<StringSpeciesFeature>? {
        return null
    }

    override fun invoke(buffer: RegistryFriendlyByteBuf, name: String): StringSpeciesFeature? {
        return if (name in keys) {
            StringSpeciesFeature(name, "").also { it.loadFromBuffer(buffer) }
        } else {
            null
        }
    }

    override fun invoke(nbt: CompoundTag): StringSpeciesFeature? {
        val key = keys.find { nbt.contains(it) }
        if (key == null) return null
        return StringSpeciesFeature(key, "").also { it.loadFromNBT(nbt) }
    }

    override fun invoke(json: JsonObject): StringSpeciesFeature? {
        val key = keys.find { json.has(it) }
        if (key == null) return null
        return StringSpeciesFeature(key, "").also { it.loadFromJSON(json) }
    }

    override fun provide(pokemon: Pokemon): Set<String> {
        return if (isAspect) {
            get(pokemon)?.let { setOf(getAspect(it)) } ?: emptySet()
        } else {
            emptySet()
        }
    }

    override fun provide(properties: PokemonProperties): Set<String> {
        return if (isAspect) {
            val feature = properties.customProperties.filterIsInstance<StringSpeciesFeature>().find { it.name in keys }
            if (feature != null) {
                setOf(getAspect(feature))
            } else {
                emptySet()
            }
        } else {
            emptySet()
        }
    }

    override fun get(pokemon: Pokemon): StringSpeciesFeature? {
        return pokemon.features.filterIsInstance<StringSpeciesFeature>().find { it.name in keys }
    }
}
