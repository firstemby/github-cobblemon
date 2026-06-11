/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.pokemon.feature

import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.readString
import com.cobblemon.mod.common.util.substitute
import com.cobblemon.mod.common.util.writeString
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.world.entity.ai.behavior.ShufflingList

/**
 * A [SpeciesFeatureProvider] which is a string value selected from a fixed map of choices and their weights. Parameters exist
 * to change default behavior, aspects, and the available choices. Choices must be lowercase.
 *
 * @author Gito
 * @since April 5th, 2026
 */
class WeightedChoiceSpeciesFeatureProvider :
    AbstractChoiceSpeciesFeatureProvider<Map<String, Int>>(emptyList(), mutableMapOf()) {
    override var needsKey = true
    override var visible = false

    override fun examples() = choices.map { it.key }

    override fun saveToBuffer(buffer: RegistryFriendlyByteBuf, toClient: Boolean) {
        buffer.writeCollection(keys) { _, value -> buffer.writeString(value) }
        buffer.writeNullable(default) { _, value -> buffer.writeString(value) }
        buffer.writeMap(
            choices,
            { _, value -> buffer.writeString(value) },
            { _, value -> buffer.writeInt(value) }
        )
        buffer.writeBoolean(isAspect)
        buffer.writeString(aspectFormat)
        buffer.writeBoolean(needsKey)
    }

    override fun loadFromBuffer(buffer: RegistryFriendlyByteBuf) {
        keys = buffer.readList { buffer.readString() }
        default = buffer.readNullable { buffer.readString() }
        choices = buffer.readMap({ buffer.readString() }, { buffer.readInt() })
        isAspect = buffer.readBoolean()
        aspectFormat = buffer.readString()
        needsKey = buffer.readBoolean()
    }

    override fun fromString(value: String?): StringSpeciesFeature? {
        val lower = value?.lowercase()
        if (lower == null || lower !in choices.keys) {
            return null
        }

        return StringSpeciesFeature(keys.first(), lower)
    }

    override fun invoke(pokemon: Pokemon): StringSpeciesFeature? {
        val existing = get(pokemon)
        return if (existing != null && existing.value in choices) {
            existing
        } else {
            val value = when {
                (default in choices.keys) -> default!!
                (default == "random") -> {
                    with(ShufflingList<String>()) {
                        choices.forEach { (choice, weight) -> this.add(choice, weight) }
                        return@with this.shuffle().first()
                            ?: throw IllegalStateException("The 'choices' list is empty for species feature provider: ${keys.joinToString()}")
                    }
                }

                else -> null
            }

            fromString(value)
        }
    }

    override fun getAllAspects(): Map<String, Int> {
        val aspects = mutableMapOf<String, Int>()

        choices.forEach { (choice, weight) ->
            aspects[aspectFormat.substitute("choice", choice)] = weight
        }

        return aspects
    }
}
