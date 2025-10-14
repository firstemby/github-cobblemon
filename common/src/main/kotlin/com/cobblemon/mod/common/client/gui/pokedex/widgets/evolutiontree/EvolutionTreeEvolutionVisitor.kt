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
import com.cobblemon.mod.common.api.pokemon.evolution.EvolutionVisitor
import com.cobblemon.mod.common.api.pokemon.requirement.Requirement
import com.cobblemon.mod.common.api.text.bold
import com.cobblemon.mod.common.api.text.green
import com.cobblemon.mod.common.client.gui.pokedex.widgets.evolutiontree.EvolutionTreeEvolutionVisitor.RenderEvolutionData
import com.cobblemon.mod.common.client.gui.pokedex.widgets.evolutiontree.EvolutionTreeRequirementVisitor.RenderRequirementData
import com.cobblemon.mod.common.pokemon.evolution.variants.BlockClickEvolution
import com.cobblemon.mod.common.pokemon.evolution.variants.ItemInteractionEvolution
import com.cobblemon.mod.common.pokemon.evolution.variants.LevelUpEvolution
import com.cobblemon.mod.common.pokemon.evolution.variants.TradeEvolution
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.itemRegistry
import com.cobblemon.mod.common.util.lang
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

class EvolutionTreeEvolutionVisitor : EvolutionVisitor<RenderEvolutionData?> {

    data class RenderEvolutionData(
        val label: MutableComponent,
        val icons: List<Item>,
        val tooltips: List<MutableComponent>,
    )

    private val requirementVisitor = EvolutionTreeRequirementVisitor()

    private fun visitRequirement(requirement: Requirement): RenderRequirementData? {
        val icon = requirement.pokedexIcon?.let {
            Minecraft.getInstance()?.player?.level()?.itemRegistry?.get(it)
        }

        val tooltip = requirement.pokedexTooltip?.let {
            Component.translatable(it)
        }

        if (icon != null && tooltip != null) {
            return RenderRequirementData(icon, tooltip)
        }

        return requirementVisitor.visit(requirement)
    }

    override fun visitBlockClickEvolution(blockClickEvolution: BlockClickEvolution): RenderEvolutionData? {
        return null
    }

    override fun visitDummyEvolution(evolution: Evolution): RenderEvolutionData? {
        return null
    }

    override fun visitItemInteractionEvolution(itemInteractionEvolution: ItemInteractionEvolution): RenderEvolutionData? {
        val label = lang("ui.pokedex.evolution.item_interact.label").bold()
        val item = itemInteractionEvolution.requiredContext.items.get().first().value()
        val icons = mutableListOf(item)
        val tooltips =
            mutableListOf(lang("ui.pokedex.evolution.item_interact.tooltip", item.description.plainCopy().green()))

        val requirementsDataList = itemInteractionEvolution.requirements.mapNotNull { requirement ->
            visitRequirement(requirement)
        }

        requirementsDataList.forEach { requirement ->
            icons.add(requirement.icon)
            tooltips.add(requirement.tooltip)
        }

        return RenderEvolutionData(label, icons, tooltips)
    }

    override fun visitLevelUpEvolution(levelUpEvolution: LevelUpEvolution): RenderEvolutionData? {
        val requirementsDataList = levelUpEvolution.requirements.map { requirement ->
            visitRequirement(requirement)
        }

        val label = requirementsDataList.firstNotNullOfOrNull { requirement -> requirement?.label }
        val icons = requirementsDataList.mapNotNull { requirementData -> requirementData?.icon }
        val tooltips = requirementsDataList.mapNotNull { requirementData -> requirementData?.tooltip }

        return RenderEvolutionData(label ?: Component.literal(""), icons, tooltips)
    }

    override fun visitTradeEvolution(tradeEvolution: TradeEvolution): RenderEvolutionData? {
        val label = lang("ui.pokedex.evolution.trade.label")
        val icons = mutableListOf<Item>(CobblemonItems.LINK_CABLE)
        val tooltips = mutableListOf<MutableComponent>()

        val species = tradeEvolution.requiredContext.species
        if (species == null) {
            tooltips.add(lang("ui.pokedex.evolution.trade.tooltip.with_any_species"))
        } else {
            PokemonSpecies.getByIdentifier(cobblemonResource(species))?.let {
                tooltips.add(lang("ui.pokedex.evolution.trade.tooltip.with_species", it.translatedName.green()))
            }
        }

        val requirementsDataList = tradeEvolution.requirements.mapNotNull { requirement ->
            visitRequirement(requirement)
        }

        requirementsDataList.forEach { requirement ->
            icons.add(requirement.icon)
            tooltips.add(requirement.tooltip)
        }

        return RenderEvolutionData(label, icons, tooltips)
    }
}