/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.pokeedit.widgets

import com.cobblemon.mod.common.CobblemonCosmeticItems
import com.cobblemon.mod.common.api.conditional.RegistryLikeIdentifierCondition
import com.cobblemon.mod.common.api.conditional.RegistryLikeTagCondition
import com.cobblemon.mod.common.api.pokemon.feature.ChoiceSpeciesFeatureProvider
import com.cobblemon.mod.common.api.pokemon.feature.FlagSpeciesFeatureProvider
import com.cobblemon.mod.common.api.pokemon.feature.SpeciesFeatures
import com.cobblemon.mod.common.api.text.bold
import com.cobblemon.mod.common.client.CobblemonResources
import com.cobblemon.mod.common.client.gui.ScrollingWidget
import com.cobblemon.mod.common.client.gui.pokeedit.PokeEditDraft
import com.cobblemon.mod.common.client.gui.summary.widgets.SoundlessWidget
import com.cobblemon.mod.common.client.render.drawScaledText
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Gender
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.Species
import com.cobblemon.mod.common.util.lang
import com.cobblemon.mod.common.util.substitute
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.resources.language.I18n
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import net.minecraft.util.Mth

class PokeEditAspectsTab(
    pX: Int,
    pY: Int,
    private val draft: PokeEditDraft
) : SoundlessWidget(pX, pY, WIDTH, HEIGHT, lang("ui.aspects")) {
    companion object {
        private const val WIDTH = 139
        private const val HEIGHT = 163
        private const val SLOT_HEIGHT = 16
        private const val SCALE = 0.7F
        private const val TOP_MARGIN = 10
        private const val BOTTOM_MARGIN = 10
        private const val COLUMN_GAP = 6

        data class ChoiceGroup(
            val key: String,
            val choices: List<String>,
            val aspectFormat: String
        )

        data class CosmeticChoice(
            val itemId: ResourceLocation,
            val aspects: List<String>,
            val label: MutableComponent
        )

        data class AspectOptions(
            val toggles: Set<String>,
            val choiceGroups: List<ChoiceGroup>,
            val flagKeys: Set<String>,
            val cosmeticChoices: List<CosmeticChoice>
        )

        fun collectAspectOptions(
            species: Species,
            form: FormData? = null,
            gender: Gender? = null,
            shiny: Boolean? = null
        ): AspectOptions {
            val toggles = mutableSetOf<String>()
            val choiceGroups = mutableListOf<ChoiceGroup>()
            val flagKeys = mutableSetOf<String>()

            val forms = if (species.forms.isEmpty()) listOf(species.standardForm) else species.forms
            forms.forEach { form -> toggles.addAll(form.aspects) }

            SpeciesFeatures.getFeaturesFor(species).forEach { provider ->
                when (provider) {
                    is ChoiceSpeciesFeatureProvider -> {
                        if (provider.isAspect && provider.keys.isNotEmpty() && provider.choices.isNotEmpty()) {
                            choiceGroups.add(ChoiceGroup(provider.keys.first(), provider.choices, provider.aspectFormat))
                        }
                    }
                    is FlagSpeciesFeatureProvider -> {
                        if (provider.isAspect && provider.keys.isNotEmpty()) {
                            flagKeys.add(provider.keys.first())
                        }
                    }
                }
            }

            toggles.removeAll(flagKeys)

            val filteredChoiceGroups = choiceGroups
                .filterNot { it.key in setOf("cosmetic", "cosmetic_item", "cosmeticitem") }
                .sortedBy { it.key }

            return AspectOptions(
                toggles = toggles.filter { it.isNotBlank() && it != "cosmetic" && it != "cosmetic_item" && it != "cosmeticitem" }.toSet(),
                choiceGroups = filteredChoiceGroups,
                flagKeys = flagKeys.filter { it.isNotBlank() }.toSet(),
                cosmeticChoices = collectCosmeticChoices(species, form, gender, shiny)
            )
        }

        fun collectCosmeticChoices(
            species: Species,
            form: FormData?,
            gender: Gender?,
            shiny: Boolean?
        ): List<CosmeticChoice> {
            val pokemon = Pokemon().also {
                it.isClient = true
                it.species = species
                it.form = form ?: species.standardForm
                if (gender != null) it.gender = gender
                if (shiny != null) it.shiny = shiny
            }

            val registry = BuiltInRegistries.ITEM
            val choices = linkedMapOf<ResourceLocation, CosmeticChoice>()

            CobblemonCosmeticItems.findValidForPokemon(pokemon).forEach { assignment ->
                assignment.cosmeticItems.forEach { cosmetic ->
                    val aspects = cosmetic.aspects.filter { it.isNotBlank() }
                    when (val condition = cosmetic.consumedItem) {
                        is RegistryLikeIdentifierCondition<*> -> {
                            val id = condition.identifier
                            val label = registry.getOptional(id)
                                .map { it.defaultInstance.hoverName.copy() }
                                .orElseGet { Component.literal(id.toString()) }
                            choices.putIfAbsent(id, CosmeticChoice(id, aspects, label))
                        }
                        is RegistryLikeTagCondition<*> -> {
                            @Suppress("UNCHECKED_CAST")
                            val tag = condition.tag as? TagKey<net.minecraft.world.item.Item> ?: return@forEach
                            val tagItems = registry.getTag(tag).orElse(null) ?: return@forEach
                            tagItems.forEach { holder ->
                                val item = holder.value()
                                val id = registry.getKey(item) ?: return@forEach
                                val label = item.defaultInstance.hoverName.copy()
                                choices.putIfAbsent(id, CosmeticChoice(id, aspects, label))
                            }
                        }
                    }
                }
            }

            return choices.values.sortedBy { it.label.string.lowercase() }
        }

        fun choiceAspectId(key: String, choice: String): String? {
            val provider = SpeciesFeatures.getFeature(key) as? ChoiceSpeciesFeatureProvider ?: return null
            return provider.aspectFormat.substitute("choice", choice)
        }
    }

    private sealed class AspectItem {
        data class Group(val group: ChoiceGroup) : AspectItem()
        data class Toggle(val id: String, val source: ToggleSource) : AspectItem()
        data class Cosmetic(val choices: List<CosmeticChoice>) : AspectItem()
    }

    private sealed class OptionItem {
        data class Choice(val group: ChoiceGroup, val choice: String) : OptionItem()
        data class ClearChoice(val group: ChoiceGroup) : OptionItem()
        data class Toggle(val aspect: AspectItem.Toggle, val enabled: Boolean) : OptionItem()
        data class CosmeticChoice(val choice: PokeEditAspectsTab.Companion.CosmeticChoice) : OptionItem()
        object ClearCosmetic : OptionItem()
    }

    private enum class ToggleSource {
        FORCED_ASPECT,
        FLAG_FEATURE
    }

    private val listX = x + 4
    private val listBottom = y + HEIGHT - BOTTOM_MARGIN
    private val listAreaWidth = WIDTH - 8
    private val listTop = y + TOP_MARGIN
    private val listHeight = listBottom - listTop
    private val leftWidth = (listAreaWidth - COLUMN_GAP) / 2
    private val rightWidth = listAreaWidth - leftWidth - COLUMN_GAP
    private val leftX = listX
    private val rightX = listX + leftWidth + COLUMN_GAP

    private val aspectList = AspectList(
        leftX,
        listTop,
        leftWidth,
        listHeight
    ).also { addWidget(it) }

    private val optionList = OptionList(
        rightX,
        listTop,
        rightWidth,
        listHeight
    ).also { addWidget(it) }

    private var aspectOptions: AspectOptions = collectAspectOptions(
        draft.species,
        draft.form,
        draft.gender,
        draft.shiny
    )
    private var selectedAspect: AspectItem? = null
    private var lastSpeciesId: ResourceLocation? = null

    init {
        rebuildAspectEntries()
    }

    override fun renderWidget(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        val currentSpeciesId = draft.species.resourceIdentifier
        if (lastSpeciesId != currentSpeciesId) {
            refreshAspects()
        }

        drawScaledText(
            context = context,
            font = CobblemonResources.DEFAULT_LARGE,
            text = lang("ui.aspects").bold(),
            x = x + (WIDTH / 2.0),
            y = y + 2,
            scale = SCALE,
            centered = true
        )

        context.fill(
            leftX,
            listTop,
            leftX + leftWidth,
            listBottom,
            0x22000000
        )
        context.fill(
            rightX,
            listTop,
            rightX + rightWidth,
            listBottom,
            0x22000000
        )

        aspectList.render(context, mouseX, mouseY, delta)
        optionList.render(context, mouseX, mouseY, delta)
    }

    private fun refreshAspects() {
        aspectOptions = collectAspectOptions(
            draft.species,
            draft.form,
            draft.gender,
            draft.shiny
        )
        val cosmetic = aspectOptions.cosmeticChoices.firstOrNull { it.itemId.toString() == draft.cosmeticItem }
        draft.cosmeticItem = cosmetic?.itemId?.toString()
        draft.cosmeticAspects = cosmetic?.aspects?.toSet() ?: emptySet()
        val valid = buildAspectEntries()
        if (selectedAspect !in valid) {
            selectedAspect = valid.firstOrNull()
        }
        rebuildAspectEntries()
        lastSpeciesId = draft.species.resourceIdentifier
    }

    private fun buildAspectEntries(): List<AspectItem> {
        val entries = mutableListOf<AspectItem>()
        aspectOptions.choiceGroups.sortedBy { it.key }.forEach { entries.add(AspectItem.Group(it)) }
        aspectOptions.flagKeys.sortedBy { it }.forEach { entries.add(AspectItem.Toggle(it, ToggleSource.FLAG_FEATURE)) }
        aspectOptions.toggles.sortedBy { it }.forEach { entries.add(AspectItem.Toggle(it, ToggleSource.FORCED_ASPECT)) }
        return entries
    }

    private fun rebuildAspectEntries() {
        val entries = buildAspectEntries()
        aspectList.setEntries(entries)
        if (selectedAspect == null) {
            selectedAspect = entries.firstOrNull()
        }
        rebuildOptionEntries()
    }

    private fun rebuildOptionEntries() {
        optionList.setEntries(buildOptionEntries(selectedAspect))
    }

    private fun buildOptionEntries(aspect: AspectItem?): List<OptionItem> {
        if (aspect == null) return emptyList()
        return when (aspect) {
            is AspectItem.Group -> {
                val entries = mutableListOf<OptionItem>()
                entries.add(OptionItem.ClearChoice(aspect.group))
                aspect.group.choices.forEach { choice ->
                    entries.add(OptionItem.Choice(aspect.group, choice))
                }
                entries
            }
            is AspectItem.Cosmetic -> {
                val entries = mutableListOf<OptionItem>()
                entries.add(OptionItem.ClearCosmetic)
                aspect.choices.forEach { choice ->
                    entries.add(OptionItem.CosmeticChoice(choice))
                }
                entries
            }
            is AspectItem.Toggle -> {
                listOf(
                    OptionItem.Toggle(aspect, enabled = true),
                    OptionItem.Toggle(aspect, enabled = false)
                )
            }
        }
    }

    private fun humanizeId(raw: String): String {
        return raw
            .replace('_', ' ')
            .replace('-', ' ')
            .split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { part ->
                part.lowercase().replaceFirstChar { ch -> if (ch.isLowerCase()) ch.titlecase() else ch.toString() }
            }
    }

    private fun labelForId(id: String): MutableComponent {
        val key = "cobblemon.ui.aspect.$id"
        return if (I18n.exists(key)) {
            Component.translatable(key)
        } else {
            Component.literal(humanizeId(id))
        }
    }

    private fun isAspectActive(entry: AspectItem): Boolean = when (entry) {
        is AspectItem.Toggle -> when (entry.source) {
            ToggleSource.FORCED_ASPECT -> entry.id in draft.forcedAspects
            ToggleSource.FLAG_FEATURE -> entry.id in draft.flagFeatures
        }
        is AspectItem.Group -> entry.group.key in draft.choiceFeatures
        is AspectItem.Cosmetic -> draft.cosmeticItem != null
    }

    private fun labelFor(entry: AspectItem): MutableComponent = when (entry) {
        is AspectItem.Group -> {
            val name = labelForId(entry.group.key).string
            val choice = draft.choiceFeatures[entry.group.key]?.let { labelForId(it).string }
            Component.literal(if (choice != null) "$name: $choice" else name)
        }
        is AspectItem.Toggle -> labelForId(entry.id)
        is AspectItem.Cosmetic -> {
            val base = lang("cosmetic_item").string
            val choice = draft.cosmeticItem?.let { id ->
                entry.choices.firstOrNull { it.itemId.toString() == id }?.label?.string
            }
            Component.literal(if (choice != null) "$base: $choice" else base)
        }
    }

    private fun labelFor(option: OptionItem): MutableComponent = when (option) {
        is OptionItem.ClearChoice -> lang("ui.none")
        is OptionItem.Choice -> labelForId(option.choice)
        is OptionItem.Toggle -> if (option.enabled) lang("ui.enabled") else lang("ui.disabled")
        is OptionItem.CosmeticChoice -> option.choice.label
        OptionItem.ClearCosmetic -> lang("ui.none")
    }

    private fun selectAspect(entry: AspectItem) {
        if (selectedAspect == entry) return
        selectedAspect = entry
        rebuildOptionEntries()
    }

    private fun handleOption(option: OptionItem) {
        when (option) {
            is OptionItem.ClearChoice -> {
                draft.choiceFeatures.remove(option.group.key)
            }
            is OptionItem.Choice -> {
                draft.choiceFeatures[option.group.key] = option.choice
            }
            is OptionItem.Toggle -> {
                when (option.aspect.source) {
                    ToggleSource.FORCED_ASPECT -> {
                        if (option.enabled) {
                            draft.forcedAspects.add(option.aspect.id)
                        } else {
                            draft.forcedAspects.remove(option.aspect.id)
                        }
                    }
                    ToggleSource.FLAG_FEATURE -> {
                        if (option.enabled) {
                            draft.flagFeatures.add(option.aspect.id)
                        } else {
                            draft.flagFeatures.remove(option.aspect.id)
                        }
                    }
                }
            }
            is OptionItem.CosmeticChoice -> {
                draft.cosmeticItem = option.choice.itemId.toString()
                draft.cosmeticAspects = option.choice.aspects.toSet()
            }
            OptionItem.ClearCosmetic -> {
                draft.cosmeticItem = null
                draft.cosmeticAspects = emptySet()
            }
        }
    }

    private inner class AspectList(
        pX: Int,
        pY: Int,
        width: Int,
        height: Int
    ) : ScrollingWidget<AspectList.AspectEntry>(
        width = width,
        height = height,
        left = pX,
        top = pY - height,
        slotHeight = SLOT_HEIGHT + 2,
        scrollBarWidth = 3
    ) {
        private val listX = pX

        fun setEntries(aspects: List<AspectItem>) {
            clearEntries()
            aspects.forEach { aspect -> addEntry(AspectEntry(aspect)) }
            scrollAmount = 0.0
        }

        override fun getScrollbarPosition(): Int = listX + width - 3

        override fun renderScrollbar(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
            val xLeft = this.scrollbarPosition
            val xRight = xLeft + 3
            val barHeight = this.bottom - this.y

            var yBottom = ((barHeight * barHeight).toFloat() / this.maxPosition.toFloat()).toInt()
            yBottom = Mth.clamp(yBottom, 32, barHeight - 8)
            var yTop = scrollAmount.toInt() * (barHeight - yBottom) / this.maxScroll + this.y
            if (yTop < this.y) yTop = this.y

            context.fill(xLeft, this.y + 3, xRight, this.bottom - 3, 0xFF3A96B6.toInt())
            context.fill(xLeft, yTop + 3, xRight, yTop + yBottom - 3, 0xFFFCFCFC.toInt())
        }

        inner class AspectEntry(
            private val entry: AspectItem
        ) : Slot<AspectEntry>() {
            private var x: Int = 0
            private var y: Int = 0

            override fun render(
                context: GuiGraphics,
                index: Int,
                y: Int,
                x: Int,
                entryWidth: Int,
                entryHeight: Int,
                mouseX: Int,
                mouseY: Int,
                hovered: Boolean,
                tickDelta: Float
            ) {
                this.x = x
                this.y = y + 1

                if (selectedAspect == entry) {
                    context.fill(this.x - 1, this.y - 1, this.x + entryWidth + 1, this.y + SLOT_HEIGHT + 1, 0x442A7FFF)
                } else if (isAspectActive(entry)) {
                    context.fill(this.x - 1, this.y - 1, this.x + entryWidth + 1, this.y + SLOT_HEIGHT + 1, 0x4436B33F)
                } else if (hovered) {
                    context.fill(this.x - 1, this.y - 1, this.x + entryWidth + 1, this.y + SLOT_HEIGHT + 1, 0x442A7FFF)
                }

                drawScaledText(
                    context = context,
                    font = CobblemonResources.DEFAULT_LARGE,
                    text = labelFor(entry),
                    x = this.x + 4,
                    y = this.y + 3,
                    scale = SCALE,
                    shadow = true
                )
            }

            override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
                if (isMouseOver(mouseX, mouseY)) {
                    selectAspect(entry)
                    return true
                }
                return false
            }

            override fun getNarration(): Component {
                return labelFor(entry)
            }
        }
    }

    private inner class OptionList(
        pX: Int,
        pY: Int,
        width: Int,
        height: Int
    ) : ScrollingWidget<OptionList.OptionEntry>(
        width = width,
        height = height,
        left = pX,
        top = pY - height,
        slotHeight = SLOT_HEIGHT + 2,
        scrollBarWidth = 3
    ) {
        private val listX = pX

        fun setEntries(options: List<OptionItem>) {
            clearEntries()
            options.forEach { option -> addEntry(OptionEntry(option)) }
            scrollAmount = 0.0
        }

        override fun getScrollbarPosition(): Int = listX + width - 3

        override fun renderScrollbar(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
            val xLeft = this.scrollbarPosition
            val xRight = xLeft + 3
            val barHeight = this.bottom - this.y

            var yBottom = ((barHeight * barHeight).toFloat() / this.maxPosition.toFloat()).toInt()
            yBottom = Mth.clamp(yBottom, 32, barHeight - 8)
            var yTop = scrollAmount.toInt() * (barHeight - yBottom) / this.maxScroll + this.y
            if (yTop < this.y) yTop = this.y

            context.fill(xLeft, this.y + 3, xRight, this.bottom - 3, 0xFF3A96B6.toInt())
            context.fill(xLeft, yTop + 3, xRight, yTop + yBottom - 3, 0xFFFCFCFC.toInt())
        }

        inner class OptionEntry(
            private val option: OptionItem
        ) : Slot<OptionEntry>() {
            private var x: Int = 0
            private var y: Int = 0

            private fun isSelectedOption(): Boolean = when (option) {
                is OptionItem.ClearChoice -> {
                    draft.choiceFeatures[option.group.key] == null
                }
                is OptionItem.Choice -> {
                    draft.choiceFeatures[option.group.key] == option.choice
                }
                is OptionItem.Toggle -> {
                    val isEnabled = when (option.aspect.source) {
                        ToggleSource.FORCED_ASPECT -> option.aspect.id in draft.forcedAspects
                        ToggleSource.FLAG_FEATURE -> option.aspect.id in draft.flagFeatures
                    }
                    isEnabled == option.enabled
                }
                is OptionItem.CosmeticChoice -> {
                    draft.cosmeticItem == option.choice.itemId.toString()
                }
                OptionItem.ClearCosmetic -> {
                    draft.cosmeticItem == null
                }
            }

            override fun render(
                context: GuiGraphics,
                index: Int,
                y: Int,
                x: Int,
                entryWidth: Int,
                entryHeight: Int,
                mouseX: Int,
                mouseY: Int,
                hovered: Boolean,
                tickDelta: Float
            ) {
                this.x = x
                this.y = y + 1

                if (isSelectedOption()) {
                    context.fill(this.x - 1, this.y - 1, this.x + entryWidth + 1, this.y + SLOT_HEIGHT + 1, 0x4436B33F)
                } else if (hovered) {
                    context.fill(this.x - 1, this.y - 1, this.x + entryWidth + 1, this.y + SLOT_HEIGHT + 1, 0x442A7FFF)
                }

                drawScaledText(
                    context = context,
                    font = CobblemonResources.DEFAULT_LARGE,
                    text = labelFor(option),
                    x = this.x + 4,
                    y = this.y + 3,
                    scale = SCALE,
                    shadow = true
                )
            }

            override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
                if (isMouseOver(mouseX, mouseY)) {
                    handleOption(option)
                    return true
                }
                return false
            }

            override fun getNarration(): Component {
                return labelFor(option)
            }
        }
    }
}
