/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.pokedex.widgets.evolutiontree

import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.pokemon.feature.IntSpeciesFeature
import com.cobblemon.mod.common.api.pokemon.feature.StringSpeciesFeature
import com.cobblemon.mod.common.api.properties.CustomPokemonProperty
import com.cobblemon.mod.common.api.text.add
import com.cobblemon.mod.common.api.text.green
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.lang
import net.minecraft.locale.Language
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent

internal fun describePokemonProperties(properties: PokemonProperties): MutableComponent? {
    if (hasUndescribedProperty(properties)) {
        return null
    }

    val descriptors = mutableListOf<MutableComponent>()

    properties.species?.let { PokemonSpecies.getByIdentifier(cobblemonResource(it)) }?.let {
        descriptors.add(it.translatedName.plainCopy().green())
    }
    properties.gender?.let {
        descriptors.add(lang("gender.${it.name.lowercase()}").green())
    }
    properties.nickname?.let {
        descriptors.add(lang("ui.pokedex.requirement.properties.nickname", it.plainCopy().green()).green())
    }
    properties.customProperties.forEach { customProperty ->
        describeCustomProperty(customProperty)?.let { descriptors.add(it) }
    }

    if (descriptors.isEmpty()) {
        return null
    }

    if (descriptors.size == 1) {
        return descriptors[0]
    }

    val allButLast = descriptors.dropLast(1).reduce { acc, next -> acc.add(", ").add(next) }
    return lang("ui.pokedex.requirement.properties.and", allButLast, descriptors.last())
}

private fun hasUndescribedProperty(properties: PokemonProperties): Boolean {
    return properties.form != null ||
            properties.shiny != null ||
            properties.level != null ||
            properties.friendship != null ||
            properties.fullness != null ||
            properties.pokeball != null ||
            properties.nature != null ||
            properties.ability != null ||
            properties.status != null ||
            properties.type != null ||
            properties.teraType != null ||
            properties.dmaxLevel != null ||
            properties.gmaxFactor != null ||
            properties.tradeable != null ||
            properties.originalTrainerType != null ||
            properties.originalTrainer != null ||
            properties.moves != null ||
            properties.movesetBuilders != null ||
            properties.heldItem != null ||
            properties.cosmeticItem != null ||
            properties.scaleModifier != null ||
            properties.isAlpha != null ||
            properties.minPerfectIVs != null ||
            properties.customProperties.any { describeCustomProperty(it) == null }
}

private fun describeCustomProperty(property: CustomPokemonProperty): MutableComponent? {
    val name = when (property) {
        is IntSpeciesFeature -> property.name
        is StringSpeciesFeature -> property.name
        else -> return null
    }

    val key = "ui.pokedex.requirement.properties.custom.$name"
    if (!Language.getInstance().has("cobblemon.$key")) {
        return null
    }

    val value = when (property) {
        is IntSpeciesFeature -> Component.literal(property.value.toString())
        is StringSpeciesFeature -> PokemonSpecies.getByIdentifier(cobblemonResource(property.value))?.translatedName?.plainCopy()
            ?: Component.literal(property.value)
        else -> return null
    }

    return lang(key, value.green())
}