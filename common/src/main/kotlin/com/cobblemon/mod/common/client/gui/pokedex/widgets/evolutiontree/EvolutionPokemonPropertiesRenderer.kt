/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.pokedex.widgets.evolutiontree

import com.cobblemon.mod.common.api.pokemon.Natures
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.pokemon.feature.IntSpeciesFeature
import com.cobblemon.mod.common.api.pokemon.feature.IntSpeciesFeatureProvider
import com.cobblemon.mod.common.api.pokemon.feature.SpeciesFeatures
import com.cobblemon.mod.common.api.pokemon.feature.StringSpeciesFeature
import com.cobblemon.mod.common.api.properties.CustomPokemonProperty
import com.cobblemon.mod.common.api.text.add
import com.cobblemon.mod.common.api.text.green
import com.cobblemon.mod.common.api.types.ElementalTypes
import com.cobblemon.mod.common.util.asIdentifierDefaultingNamespace
import com.cobblemon.mod.common.util.asTranslated
import com.cobblemon.mod.common.util.lang
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.locale.Language
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.world.item.Items

internal fun describePokemonProperties(properties: PokemonProperties): MutableComponent? {
    if (hasUndescribedProperty(properties)) {
        return null
    }

    val descriptors = mutableListOf<MutableComponent>()

    properties.species?.let { PokemonSpecies.getByIdentifier(it.asIdentifierDefaultingNamespace()) }?.let {
        descriptors.add(it.translatedName.plainCopy().green())
    }
    properties.gender?.let {
        descriptors.add(lang("gender.${it.name.lowercase()}").green())
    }
    properties.nickname?.let {
        descriptors.add(lang("ui.pokedex.requirement.properties.nickname", it.plainCopy().green()))
    }
    properties.type?.let {
        val typeName = ElementalTypes.get(it)?.displayName?.plainCopy() ?: Component.literal(it)
        descriptors.add(lang("ui.pokedex.requirement.properties.type", typeName.green()))
    }
    properties.nature?.let {
        val natureName = Natures.getNature(it.asIdentifierDefaultingNamespace())?.displayName?.asTranslated() ?: Component.literal(it)
        descriptors.add(lang("ui.pokedex.requirement.properties.nature", natureName.green()))
    }
    properties.customProperties.forEach { customProperty ->
        describeCustomProperty(customProperty)?.let { descriptors.add(it) }
    }

    val heldItemDescriptor = properties.heldItem?.let { heldItem ->
        val itemId = heldItem.substringBefore('[').trim().asIdentifierDefaultingNamespace()
        val item = BuiltInRegistries.ITEM.get(itemId)
        if (item == Items.AIR) null else lang("ui.pokedex.requirement.properties.held_item", item.description.plainCopy().green())
    }

    if (descriptors.isEmpty() && heldItemDescriptor == null) {
        return null
    }

    val mainDescriptor = when {
        descriptors.isEmpty() -> null
        descriptors.size == 1 -> descriptors[0]
        else -> {
            val allButLast = descriptors.dropLast(1).reduce { acc, next -> acc.add(", ").add(next) }
            lang("ui.pokedex.requirement.properties.and", allButLast, descriptors.last())
        }
    }

    return when {
        mainDescriptor != null && heldItemDescriptor != null -> mainDescriptor.add(" ").add(heldItemDescriptor)
        mainDescriptor != null -> mainDescriptor
        else -> heldItemDescriptor
    }
}

private fun hasUndescribedProperty(properties: PokemonProperties): Boolean {
    return properties.form != null ||
            properties.shiny != null ||
            properties.level != null ||
            properties.friendship != null ||
            properties.fullness != null ||
            properties.pokeball != null ||
            properties.ability != null ||
            properties.status != null ||
            properties.teraType != null ||
            properties.dmaxLevel != null ||
            properties.gmaxFactor != null ||
            properties.tradeable != null ||
            properties.originalTrainerType != null ||
            properties.originalTrainer != null ||
            properties.moves != null ||
            properties.movesetBuilders != null ||
            properties.cosmeticItem != null ||
            properties.scaleModifier != null ||
            properties.isAlpha != null ||
            properties.minPerfectIVs != null ||
            properties.customProperties.any { describeCustomProperty(it) == null }
}

private fun describeCustomProperty(property: CustomPokemonProperty): MutableComponent? {
    return when (property) {
        is IntSpeciesFeature -> describeIntSpeciesFeature(property)
        is StringSpeciesFeature -> describeStringSpeciesFeature(property)
        else -> null
    }
}

private fun describeIntSpeciesFeature(property: IntSpeciesFeature): MutableComponent? {
    val value = Component.literal(property.value.toString())

    val overrideKey = "ui.pokedex.requirement.properties.custom.${property.name}"
    if (Language.getInstance().has("cobblemon.$overrideKey")) {
        return lang(overrideKey, value.green())
    }

    val displayName = (SpeciesFeatures.getFeature(property.name) as? IntSpeciesFeatureProvider)
        ?.display?.name?.takeIf { it.isNotBlank() }
        ?: return null

    return lang("ui.pokedex.requirement.properties.int_feature", value.green(), displayName.asTranslated().green())
}

private fun describeStringSpeciesFeature(property: StringSpeciesFeature): MutableComponent? {
    val key = "ui.pokedex.requirement.properties.custom.${property.name}"
    if (!Language.getInstance().has("cobblemon.$key")) {
        return null
    }

    val value = PokemonSpecies.getByIdentifier(property.value.asIdentifierDefaultingNamespace())?.translatedName?.plainCopy()
        ?: Component.literal(property.value)

    return lang(key, value.green())
}