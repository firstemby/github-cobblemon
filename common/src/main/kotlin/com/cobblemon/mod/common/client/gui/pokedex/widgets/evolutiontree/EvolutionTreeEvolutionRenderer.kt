/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.pokedex.widgets.evolutiontree

import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.pokemon.evolution.Evolution
import com.cobblemon.mod.common.api.pokemon.requirement.Requirement
import com.cobblemon.mod.common.api.text.green
import com.cobblemon.mod.common.pokemon.evolution.variants.BlockClickEvolution
import com.cobblemon.mod.common.pokemon.evolution.variants.DummyEvolution
import com.cobblemon.mod.common.pokemon.evolution.variants.ItemInteractionEvolution
import com.cobblemon.mod.common.pokemon.evolution.variants.LevelUpEvolution
import com.cobblemon.mod.common.pokemon.evolution.variants.TradeEvolution
import com.cobblemon.mod.common.util.asIdentifierDefaultingNamespace
import com.cobblemon.mod.common.util.itemRegistry
import com.cobblemon.mod.common.util.lang
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.world.item.Item

data class RenderEvolutionData(
    val icons: List<Item>,
    val tooltips: List<MutableComponent>,
)

internal fun resolveRequirementData(requirement: Requirement): RenderRequirementData? {
    val icon = requirement.pokedexIcon?.let {
        Minecraft.getInstance()?.player?.level()?.itemRegistry?.get(it)
    }

    val tooltip = requirement.pokedexTooltip?.let {
        Component.translatable(it)
    }

    if (icon != null && tooltip != null) {
        return RenderRequirementData(icon, tooltip)
    }

    return renderRequirementData(requirement)
}

fun renderEvolutionData(evolution: Evolution): RenderEvolutionData? {
    when (evolution) {
        is BlockClickEvolution -> return renderBlockClickEvolution(evolution)
        is DummyEvolution -> return renderDummyEvolution(evolution)
        is ItemInteractionEvolution -> return renderItemInteractionEvolution(evolution)
        is LevelUpEvolution -> return renderLevelUpEvolution(evolution)
        is TradeEvolution -> return renderTradeEvolution(evolution)
    }

    return null
}

fun renderBlockClickEvolution(blockClickEvolution: BlockClickEvolution): RenderEvolutionData? {
    return null
}

// TODO: Use dummyEvolution?
fun renderDummyEvolution(evolution: Evolution): RenderEvolutionData? {
    return null
}

fun renderItemInteractionEvolution(itemInteractionEvolution: ItemInteractionEvolution): RenderEvolutionData? {
    val item = itemInteractionEvolution.requiredContext.items.get().first().value()
    val icons = mutableListOf(item)
    val tooltips =
        mutableListOf(lang("ui.pokedex.evolution.item_interact.tooltip", item.description.plainCopy().green()))

    val requirementsDataList = itemInteractionEvolution.requirements.mapNotNull { requirement ->
        resolveRequirementData(requirement)
    }

    requirementsDataList.forEach { requirement ->
        icons.add(requirement.icon)
        tooltips.add(requirement.tooltip)
    }

    return RenderEvolutionData(icons, tooltips)
}

fun renderLevelUpEvolution(levelUpEvolution: LevelUpEvolution): RenderEvolutionData? {
    val requirementsDataList = levelUpEvolution.requirements.map { requirement ->
        resolveRequirementData(requirement)
    }

    val icons = requirementsDataList.mapNotNull { requirementData -> requirementData?.icon }
    val tooltips = requirementsDataList.mapNotNull { requirementData -> requirementData?.tooltip }

    return RenderEvolutionData(icons, tooltips)
}

fun renderTradeEvolution(tradeEvolution: TradeEvolution): RenderEvolutionData? {
    val icons = mutableListOf<Item>(CobblemonItems.LINK_CABLE)
    val tooltips = mutableListOf<MutableComponent>()

    val species = tradeEvolution.requiredContext.species
    if (species == null) {
        tooltips.add(lang("ui.pokedex.evolution.trade.tooltip.with_any_species"))
    } else {
        PokemonSpecies.getByIdentifier(species.asIdentifierDefaultingNamespace())?.let {
            tooltips.add(lang("ui.pokedex.evolution.trade.tooltip.with_species", it.translatedName.green()))
        }
    }

    val requirementsDataList = tradeEvolution.requirements.mapNotNull { requirement ->
        resolveRequirementData(requirement)
    }

    requirementsDataList.forEach { requirement ->
        icons.add(requirement.icon)
        tooltips.add(requirement.tooltip)
    }

    return RenderEvolutionData(icons, tooltips)
}
