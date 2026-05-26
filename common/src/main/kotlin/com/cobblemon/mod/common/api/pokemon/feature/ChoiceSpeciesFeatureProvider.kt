/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.pokemon.feature

import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.readString
import com.cobblemon.mod.common.util.substitute
import com.cobblemon.mod.common.util.writeString
import net.minecraft.network.RegistryFriendlyByteBuf

/**
 * A [SpeciesFeatureProvider] which is a string value selected from a fixed list of choices. Parameters exist
 * to change default behaviour, aspects, and the available choices. Choices must be lowercase.
 *
 * @author Hiroku
 * @since November 30th, 2022
 */
class ChoiceSpeciesFeatureProvider : AbstractChoiceSpeciesFeatureProvider<List<String>>(emptyList(), emptyList()) {
    override var needsKey = true
    override var visible = false

    override fun examples() = choices

    override fun saveToBuffer(buffer: RegistryFriendlyByteBuf, toClient: Boolean) {
        buffer.writeCollection(keys) { _, value -> buffer.writeString(value) }
        buffer.writeNullable(default) { _, value -> buffer.writeString(value) }
        buffer.writeCollection(choices) { _, value -> buffer.writeString(value) }
        buffer.writeBoolean(isAspect)
        buffer.writeString(aspectFormat)
        buffer.writeBoolean(needsKey)
    }

    override fun loadFromBuffer(buffer: RegistryFriendlyByteBuf) {
        keys = buffer.readList { buffer.readString() }
        default = buffer.readNullable { buffer.readString() }
        choices = buffer.readList { buffer.readString() }
        isAspect = buffer.readBoolean()
        aspectFormat = buffer.readString()
        needsKey = buffer.readBoolean()
    }

    override fun fromString(value: String?): StringSpeciesFeature? {
        val lower = value?.lowercase()
        if (lower == null || lower !in choices) {
            return null
        }

        return StringSpeciesFeature(keys.first(), lower)
    }

    fun getAllAspects(): MutableList<String> {
        val aspects = choices.toMutableList()
        choices.forEach {
            aspects[choices.indexOf(it)] = (aspectFormat.substitute("choice", it))
        }
        return aspects
    }

    fun parseValue(aspect: String): String? {
        if (!matches(aspect)) return null
        val head = aspectFormat.substringBefore("{{choice}}")
        val tail = aspectFormat.substringAfter("{{choice}}").takeIf { it.isNotBlank() }

        var value = aspect.substringAfter(head)
        tail?.let { value = value.substringBefore(it) }
        return value
    }

    override fun matches(aspect: String): Boolean = this.isAspect && this.getAllAspects().contains(aspect)

    override fun from(aspect: String): SpeciesFeature<*>? {
        val value = parseValue(aspect)
        return if (value != null && choices.contains(value)) this.fromString(value) else null
    }

    override fun set(pokemon: Pokemon, aspect: String) {
        val value = parseValue(aspect)
        if (value != null && choices.contains(value)) this.get(pokemon)?.value = value
    }

    override fun examples() = choices

    internal constructor(): this(emptyList())

    override fun get(pokemon: Pokemon) = pokemon.getFeature<StringSpeciesFeature>(keys.first())

    override fun invoke(pokemon: Pokemon): StringSpeciesFeature? {
        val existing = get(pokemon)
        return if (existing != null && existing.value in choices) {
            existing
        } else {
            val value = if (default in choices) {
                default!!
            } else if (default == "random") {
                // If it's mandatory, but they provided no value and no default, give it a random value.
                choices.randomOrNull()
                    ?: throw IllegalStateException("The 'choices' list is empty for species feature provider: ${keys.joinToString()}")
            } else {
                return null
            }

            fromString(value)
        }
    }

    override fun getAllAspects(): MutableList<String> {
        val aspects = choices.toMutableList()
        choices.forEach {
            aspects[choices.indexOf(it)] = (aspectFormat.substitute("choice", it))
        }
        return aspects
    }
}

    override fun provide(pokemon: Pokemon): Set<String> {
        return if (isAspect) {
            get(pokemon)?.let {
                setOf(getAspect(it))
            } ?: emptySet()
        } else {
            emptySet()
        }
    }

    override fun provide(properties: PokemonProperties): Set<String> {
        return if (isAspect) {
            val feature = properties.customProperties.filterIsInstance<StringSpeciesFeature>().find { it.name == keys.first() }
            if (feature != null) {
                setOf(getAspect(feature))
            } else {
                emptySet()
            }
        } else {
            emptySet()
        }
    }
}